package org.libbun.peg4d;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.libbun.Functor;
import org.libbun.Main;
import org.libbun.UCharset;
import org.libbun.UList;

public abstract class ParserContext {
	public final          ParserSource source;
	protected long        sourcePosition = 0;
	public    long        endPosition;
	public    PegRuleSet ruleSet = null;
	
	long statBacktrackCount = 0;
	long statBacktrackSize = 0;
	long statWorstBacktrack = 0;
	int  statObjectCount = 0;

	public ParserContext(ParserSource source, long startIndex, long endIndex) {
		this.source = source;
		this.sourcePosition = startIndex;
		this.endPosition = endIndex;
	}
	
	public abstract void setRuleSet(PegRuleSet ruleSet);
	public abstract PegObject parsePegObject(PegObject left, String key);

	protected final long getPosition() {
		return this.sourcePosition;
	}
	protected final void setPosition(long pos) {
		this.sourcePosition = pos;
	}
	protected final void rollback(long pos) {
		long len = this.sourcePosition - pos;
		if(len > 0) {
			this.statBacktrackCount = this.statBacktrackCount + 1;
			this.statBacktrackSize = this.statBacktrackSize + len;
			if(len > this.statWorstBacktrack) {
				this.statWorstBacktrack = len;
			}
		}
		this.sourcePosition = pos;
	}
	@Override
	public final String toString() {
		if(this.endPosition > this.sourcePosition) {
			return this.source.substring(this.sourcePosition, this.endPosition);
		}
		return "";
	}
	public final boolean hasChar() {
		return this.sourcePosition < this.endPosition;
	}
	protected final char charAt(long pos) {
		if(pos < this.endPosition) {
			return this.source.charAt(pos);
		}
		return '\0';
	}

	protected final char getChar() {
		if(this.hasChar()) {
			return this.source.charAt(this.sourcePosition);
		}
		return '\0';
	}
	
	public String substring(long startIndex, long endIndex) {
		if(endIndex <= this.endPosition) {
			return this.source.substring(startIndex, endIndex);
		}
		return "";
	}


	protected final void consume(long plus) {
		this.sourcePosition = this.sourcePosition + plus;
	}

	protected final boolean match(char ch) {
		if(ch == this.getChar()) {
			this.consume(1);
			return true;
		}
		return false;
	}

	protected final boolean match(String text) {
		if(this.endPosition - this.sourcePosition >= text.length()) {
			for(int i = 0; i < text.length(); i++) {
				if(text.charAt(i) != this.source.charAt(this.sourcePosition + i)) {
					return false;
				}
			}
			this.consume(text.length());
			return true;
		}
		return false;
	}

	protected final boolean match(UCharset charset) {
		if(charset.match(this.getChar())) {
			this.consume(1);
			return true;
		}
		return false;
	}
	
	protected final long matchZeroMore(UCharset charset) {
		for(;this.hasChar(); this.consume(1)) {
			char ch = this.source.charAt(this.sourcePosition);
			if(!charset.match(ch)) {
				break;
			}
		}
		return this.sourcePosition;
	}
	
	public final String formatErrorMessage(String msg1, String msg2) {
		return this.source.formatErrorMessage(msg1, this.sourcePosition, msg2);
	}

	public final void showPosition(String msg) {
		showPosition(msg, this.getPosition());
	}

	public final void showPosition(String msg, long pos) {
		System.out.println(this.source.formatErrorMessage("debug", pos, msg));
	}

	public final void showErrorMessage(String msg) {
		System.out.println(this.source.formatErrorMessage("error", this.sourcePosition, msg));
		Main._Exit(1, msg);
	}

	
	
	public boolean hasNode() {
		this.matchZeroMore(UCharset.WhiteSpaceNewLine);
		return this.sourcePosition < this.endPosition;
	}

	public PegObject parseNode(String key) {
		PegObject o = this.parsePegObject(new PegObject("#toplevel"), key);
		if(o.isFailure()) {
			o = this.newErrorObject();
		}
		return o;
	}
	
	public final PegObject newPegObject(String name) {
		PegObject node = new PegObject(name, this.source, null, this.sourcePosition);
		this.statObjectCount = this.statObjectCount + 1;
		return node;
	}
	
	protected final PegObject foundFailureNode = new PegObject(null, this.source, null, 0);

	public final PegObject newErrorObject() {
		PegObject node = newPegObject("#error");
		node.createdPeg = this.foundFailureNode.createdPeg;
		node.startIndex = this.foundFailureNode.startIndex;
		node.matched = Functor.ErrorFunctor;
		return node;
	}
	
	public final PegObject foundFailure(Peg created) {
		if(this.sourcePosition >= this.foundFailureNode.startIndex) {  // adding error location
			this.foundFailureNode.startIndex = this.sourcePosition;
			this.foundFailureNode.createdPeg = created;
		}
		return this.foundFailureNode;
	}

	public final PegObject refoundFailure(Peg created, long pos) {
		this.foundFailureNode.startIndex = pos;
		this.foundFailureNode.createdPeg = created;
		return this.foundFailureNode;
	}

	public final PegObject matchString(PegObject left, PegString e) {
		if(this.match(e.symbol)) {
			return left;
		}
		return this.foundFailure(e);
	}

	public final PegObject matchCharacter(PegObject left, PegCharacter e) {
		char ch = this.getChar();
		if(!e.charset.match(ch)) {
			return this.foundFailure(e);
		}
		this.consume(1);
		return left;
	}

	public final PegObject matchAny(PegObject left, PegAny e) {
		if(this.hasChar()) {
			this.consume(1);
			return left;
		}
		return this.foundFailure(e);
	}

	public PegObject matchLabel(PegObject left, PegLabel e) {
		return this.parsePegObject(left, e.symbol);
	}

	public PegObject matchOptional(PegObject left, PegOptional e) {
		long pos = this.getPosition();
		int markerId = this.pushNewMarker();
		PegObject parsedNode = e.innerExpr.performMatch(left, this);
		if(parsedNode.isFailure()) {
			this.popBack(markerId);
			this.rollback(pos);
			return left;
		}
		return parsedNode;
	}

	public PegObject matchRepeat(PegObject left, PegRepeat e) {
		PegObject prevNode = left;
		int count = 0;
		int markerId = this.pushNewMarker();
		while(this.hasChar()) {
			long pos = this.getPosition();
			markerId = this.pushNewMarker();
			PegObject node = e.innerExpr.performMatch(prevNode, this);
			if(node.isFailure()) {
				assert(pos == this.getPosition());
				if(count < e.atleast) {
					this.popBack(markerId);
					return node;
				}
				break;
			}
			prevNode = node;
			//System.out.println("startPostion=" + startPosition + ", current=" + this.getPosition() + ", count = " + count);
			if(!(pos < this.getPosition())) {
				if(count < e.atleast) {
					return this.foundFailure(e);
				}
				break;
			}
			count = count + 1;
			if(!this.hasChar()) {
				markerId = this.pushNewMarker();
			}
		}
		this.popBack(markerId);
		return prevNode;
	}

	public PegObject matchAnd(PegObject left, PegAnd e) {
		PegObject node = left;
		long pos = this.getPosition();
		int markerId = this.pushNewMarker();
		node = e.innerExpr.performMatch(node, this);
		this.popBack(markerId);
		this.rollback(pos);
		return node;
	}

	public PegObject matchNot(PegObject left, PegNot e) {
		PegObject node = left;
		long pos = this.getPosition();
		int markerId = this.pushNewMarker();
		node = e.innerExpr.performMatch(node, this);
		this.popBack(markerId);
		this.rollback(pos);
		if(node.isFailure()) {
			return left;
		}
		return this.foundFailure(e);
	}

	public PegObject matchSequence(PegObject left, PegSequence e) {
		long pos = this.getPosition();
		int markerId = this.pushNewMarker();
		for(int i = 0; i < e.size(); i++) {
			PegObject parsedNode = e.get(i).performMatch(left, this);
			if(parsedNode.isFailure()) {
				this.popBack(markerId);
				this.rollback(pos);
				return parsedNode;
			}
			left = parsedNode;
		}
		return left;
	}

	public PegObject matchChoice(PegObject left, PegChoice e) {
		PegObject node = left;
		long pos = this.getPosition();
		for(int i = 0; i < e.size(); i++) {
			int markerId = this.pushNewMarker();
			node = e.get(i).performMatch(left, this);
			if(!node.isFailure()) {
				break;
			}
			this.popBack(markerId);
			this.setPosition(pos);
		}
		return node;
	}
	
	private class ObjectLog {
		ObjectLog next;
		int  id;
		PegObject parentNode;
		int  index;
		PegObject childNode;
	}

	ObjectLog logStack = new ObjectLog();  // needs first logs
	ObjectLog unusedLog = null;
	int usedLog = 0;
	int maxLog  = 0;
	
	private ObjectLog newLog() {
		if(this.unusedLog == null) {
			maxLog = maxLog + 1;
			return new ObjectLog();
		}
		ObjectLog l = this.unusedLog;
		this.unusedLog = l.next;
		l.next = null;
		return l;
	}
	
	protected final int pushNewMarker() {
		return this.logStack.id;
	}

	protected final void popBack(int markerId) {
		ObjectLog cur = this.logStack;
		if(cur.id > markerId) {
			ObjectLog unused = this.logStack;
			while(cur != null) {
				//System.out.println("pop cur.id="+cur.id + ", marker="+markerId);
				if(cur.id == markerId + 1) {
					this.logStack = cur.next;
					cur.next = this.unusedLog;
					this.unusedLog = unused;
					break;
				}
				cur.parentNode = null;
				cur.childNode = null;
				cur = cur.next;
			}
		}
	}
	
	protected final void pushSetter(PegObject parentNode, int index, PegObject childNode) {
		ObjectLog l = this.newLog();
		l.parentNode = parentNode;
		l.childNode  = childNode;
		l.index = index;
		l.id = this.logStack.id + 1;
		l.next = this.logStack;
		this.logStack = l;
		//System.out.println("push " + l.id + ", index= " + index);
	}

	protected final void popNewObject(PegObject newnode, long startIndex, int marker) {
		ObjectLog cur = this.logStack;
		if(cur.id > marker) {
			UList<ObjectLog> entryList = new UList<ObjectLog>(new ObjectLog[8]);
			ObjectLog unused = this.logStack;
			while(cur != null) {
				//System.out.println("object cur.id="+cur.id + ", marker="+marker);
				if(cur.parentNode == newnode) {
					entryList.add(cur);
				}
				if(cur.id == marker + 1) {
					this.logStack = cur.next; 
					cur.next = this.unusedLog;
					this.unusedLog = unused;
					break;
				}
				cur = cur.next;
			}
			if(entryList.size() > 0) {
				newnode.expandAstToSize(entryList.size());
				int index = 0;
				for(int i = entryList.size() - 1; i >= 0; i--) {
					ObjectLog l = entryList.ArrayValues[i];
					if(l.index == -1) {
						l.index = index;
					}
					index += 1;
				}
				for(int i = entryList.size() - 1; i >= 0; i--) {
					ObjectLog l = entryList.ArrayValues[i];
					newnode.set(l.index, l.childNode);
					if(l.childNode.is("#lazy")) {
						this.parseLazyObject(l.childNode);
					}
					l.childNode = null;
				}
				newnode.checkNullEntry();
			}
			entryList = null;
		}
		newnode.setSource(startIndex, this.getPosition());
	}
	
	public PegObject matchNewObject(PegObject left, PegNewObject e) {
		PegObject leftNode = left;
		long startIndex = this.getPosition();
		if(e.predictionIndex > 0) {
			for(int i = 0; i < e.predictionIndex; i++) {
				PegObject node = e.get(i).performMatch(left, this);
				if(node.isFailure()) {
					this.rollback(startIndex);
					return node;
				}
				assert(left == node);
			}
		}
		int markerId = this.pushNewMarker();
		PegObject newnode = this.newPegObject(e.nodeName);
		newnode.setSource(e, this.source, startIndex);
		if(e.leftJoin) {
			this.pushSetter(newnode, -1, leftNode);
		}
		for(int i = e.predictionIndex; i < e.size(); i++) {
			PegObject node = e.get(i).performMatch(newnode, this);
			if(node.isFailure()) {
				this.popBack(markerId);
				this.rollback(startIndex);
				return node;
			}
			//			if(node != newnode) {
			//				e.warning("dropping @" + newnode.name + " " + node);
			//			}
		}
		this.popNewObject(newnode, startIndex, markerId);
		return newnode;
	}

	public PegObject matchSetter(PegObject left, PegSetter e) {
		PegObject node = e.innerExpr.performMatch(left, this);
		if(node.isFailure() || left == node) {
			return node;
		}
		this.pushSetter(left, e.index, node);
		return left;
	}

	public PegObject matchTag(PegObject left, PegTagging e) {
		left.tag = e.symbol;
		return left;
	}

	public PegObject matchMessage(PegObject left, PegMessage e) {
		left.optionalToken = e.symbol;
		left.startIndex = this.getPosition();
		return left;
	}

	public PegObject matchPipe(PegObject left, PegPipe e) {
		left.tag = "#lazy";
		left.optionalToken = e.symbol;
		if(left.source instanceof FileSource) {
			left.source = left.source.trim(left.startIndex, left.startIndex + left.length);
			left.startIndex = 0;
			left.length = (int)left.source.length();
		}
		this.parseLazyObject(left);
		return left;
	}

	public abstract ParserContext newParserContext(ParserSource source, long startIndex, long endIndex);
	
	private static final int ThreadSiza = 2;
	private ExecutorService threadPool = null;
	private int taskCount = 0;
	public void parseLazyObject(PegObject left) {
		if(threadPool == null) {
			threadPool = Executors.newFixedThreadPool(ThreadSiza);
//			parserPool = new ParserContext[ThreadSiza];
//			for(int i = 0; i < ThreadSiza; i++) {
//			}
		}
		if(left.optionalToken != null) {
			//System.out.println("pipe: " + left.optionalToken);
			threadPool.execute(new Task(taskCount, this, left, left.optionalToken));
			taskCount += 1;
		}
		else {
			System.out.println("pipe null: " + left);
		}
	}
	
	class Task implements Runnable {
		int taskId;
		ParserContext parser;
		PegObject left;
		String    pipe;
		Task(int taskId, ParserContext parser, PegObject left, String pipe) {
			this.taskId = taskId;
			this.parser = parser;
			this.left = left;
			this.pipe = pipe;
		}
		public void run(){
			try {
				ParserContext sub = parser.newParserContext(left.source, left.startIndex, left.startIndex + left.length);
				if(Main.VerbosePeg) {
					//sub.beginStatInfo();
					//System.out.println("[" + this.taskId + "] start parsing: " + left);
				}
				//System.out.println("thread pipe: " + this.pipe);
				PegObject newone = sub.parseNode(this.pipe);
				left.tag = newone.tag;
				left.AST = newone.AST;
				//left.optionalToken = newone.optionalToken;
//				parent.replace(left, newone);
				if(Main.VerbosePeg) {
					//System.out.println("[" + this.taskId + "] end parsing: " + newone);
					//sub.endStatInfo(newone);
				}
				sub = null;
			}
			catch (RuntimeException e) {
				e.printStackTrace();
				Main._Exit(1, "bugs");
			}
			this.parser = null;
			this.left = null;
	   }
	}
	
	public PegObject matchIndent(PegObject left, PegIndent e) {
		if(left.source != null) {
			String indent = left.source.getIndentText(left.startIndex);
			//System.out.println("###" + indent + "###");
			if(this.match(indent)) {
				return left;
			}
		}
		return left;
	}

	public PegObject matchCatch(PegObject left, PegCatch e) {
		e.innerExpr.performMatch(left, this);
		return left;
	}

	public void initMemo() {
		// TODO Auto-generated method stub
		
	}
	
	protected int memoHit = 0;
	protected int memoMiss = 0;
	protected int memoSize = 0;

	long statErapsedTime = 0;
	long usedMemory;
	int statOptimizedPeg = 0;
	
	public void beginStatInfo() {
		System.gc(); // meaningless ?
		this.statBacktrackSize = 0;
		this.statBacktrackCount = 0;
		long total = Runtime.getRuntime().totalMemory() / 1024;
		long free =  Runtime.getRuntime().freeMemory() / 1024;
		usedMemory =  total - free;
		statErapsedTime = System.currentTimeMillis();
	}

	private String ratio(double num) {
		return String.format("%.3f", num);
	}
	private String Punit(String unit) {
		return "[" + unit +"]";
	}

	private String Kunit(long num) {
		return String.format("%.3f", (double)num / 1024);
	}
	
	private String Munit(double num) {
		return String.format("%.3f", (double)num/(1024*1024));
	}

	private String Nunit(long num, String unit) {
		return num + Punit(unit);
	}

	private String Kunit(long num, String unit) {
		return ratio((double)num / 1024) + Punit(unit);
	}
	
	private String Munit(long num, String unit) {
		return ratio((double)num/(1024*1024)) + Punit(unit);
	}

	private String KMunit(long num, String unit, String unit2) {
		return Kunit(num, unit) + " " + Munit(num, unit2);
	}

	private String kpx(double num) {
		return ratio(num / 1024);
	}

	private String kpx(double num, String unit) {
		return kpx(num) + Punit(unit);
	}

	private String mpx(double num) {
		return ratio(num / (1024*1024));
	}

	private String mpx(double num, String unit) {
		return mpx(num) + Punit(unit);
	}

	public void endStatInfo(PegObject parsedObject) {
		long awaitTime = 0;
		if(this.threadPool != null) {
			this.threadPool.shutdown();
			awaitTime = System.currentTimeMillis();
			try {
				this.threadPool.awaitTermination(60000, TimeUnit.MILLISECONDS);
				awaitTime = (System.currentTimeMillis() - awaitTime);
				this.threadPool = null;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		statErapsedTime = (System.currentTimeMillis() - statErapsedTime);
		System.gc(); // meaningless ?
		if(Main.VerboseStat) {
			System.gc(); // meaningless ?
			if(Main.VerbosePeg) {
				System.out.println("parsed:\n" + parsedObject);
				if(this.hasChar()) {
					System.out.println("** uncosumed: '" + this.source + "' **");
				}
			}
			long statCharLength = this.getPosition();
			long statFileLength = this.source.getFileLength();
			long statReadLength = this.source.statReadLength;
			double fileKps = (statFileLength) / (statErapsedTime / 1000.0);
			System.out.println("parser: " + this.getClass().getSimpleName() + " -O" + Main.OptimizedLevel + " optimized peg: " + this.statOptimizedPeg );
			System.out.println("file: " + this.source.fileName + " filesize: " + KMunit(statFileLength, "Kb", "Mb"));
			System.out.println("IO: " + this.source.statIOCount +" read/file: " + ratio((double)statReadLength/statFileLength) + " pagesize: " + Nunit(FileSource.PageSize, "bytes") + " read: " + KMunit(statReadLength, "Kb", "Mb"));
			System.out.println("erapsed time: " + Nunit(statErapsedTime, "msec") + " speed: " + kpx(fileKps,"KiB/s") + " " + mpx(fileKps, "MiB/s"));
			System.out.println("backtrack raito: " + ratio((double)this.statBacktrackSize / statCharLength) + " backtrack: " + this.statBacktrackSize + " length: " + this.source.length() + ", consumed: " + statCharLength);
			System.out.println("backtrack_count: " + this.statBacktrackCount + " average: " + ratio((double)this.statBacktrackSize / this.statBacktrackCount) + " worst: " + this.statWorstBacktrack);
			System.out.println("created_object: " + this.statObjectCount + ", used_object: " + parsedObject.count() + " object_logs: " + maxLog);
			System.out.println("memo hit: " + this.memoHit + ", miss: " + this.memoMiss + 
					", ratio: " + ratio(((double)this.memoHit / (this.memoHit+this.memoMiss))) + ", consumed memo:" + this.memoSize);
			long total = Runtime.getRuntime().totalMemory() / 1024;
			long free =  Runtime.getRuntime().freeMemory() / 1024;
			long used =  total - free;
			System.out.println("heap: " + used + "KiB/ " + (used/1024) + "MiB");
			used =  used - usedMemory;
			statCharLength = statCharLength / 1024;
			System.out.println("used: " + used + "KiB/ " + (used/1024) + "MiB,  heap/length: " + (double) used/ statCharLength);
			System.out.println();
		}
	}
	
}

