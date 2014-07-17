package org.libbun.peg4d;

import java.util.Map;

import org.libbun.Main;
import org.libbun.UCharset;
import org.libbun.UList;
import org.libbun.UMap;

public class RecursiveDecentParser extends ParserContext {
	protected UList<Peg>       pegList;
	private UMap<Peg>        pegCache;
	
	public RecursiveDecentParser(ParserSource source) {
		this(source, 0, source.length(), null);
		this.initMemo();
	}

	protected RecursiveDecentParser(ParserSource source, long startIndex, long endIndex, UList<Peg> pegList) {
		super(source, startIndex, endIndex);
		if(pegList != null) {
			this.pegList = pegList;
			this.pegCache = new UMap<Peg>();
			for(int i = 0; i < pegList.size(); i++) {
				Peg e = pegList.ArrayValues[i];
				this.pegCache.put(e.ruleName, e);
			}
		}
		this.initMemo();
	}
	
	public ParserContext newParserContext(ParserSource source, long startIndex, long endIndex) {
		return new RecursiveDecentParser(source, startIndex, endIndex, this.pegList);
	}
	
	@Override
	public void setRuleSet(Grammar ruleSet) {
		this.ruleSet = ruleSet;
		this.pegCache = new UMap<Peg>();
		this.pegList = new UList<Peg>(new Peg[this.ruleSet.pegMap.size()]);
		UList<String> list = ruleSet.pegMap.keys();
		PegTransformer optimizer = new PegOptimizer();
		for(int i = 0; i < list.size(); i++) {
			String key = list.ArrayValues[i];
			Peg e = ruleSet.pegMap.get(key, null);
			Peg ne = this.pegCache.get(key);
			if(ne == null) {
				ne = e.clone(optimizer);
				this.pegCache.put(key, ne);
			}
			ne.ruleName = key;
			this.pegList.add(ne);
		}
	}

	final void log(Peg orig, String msg) {
		if(Main.VerbosePeg) {
			System.out.println("optimized: " + msg);
		}
		this.statOptimizedPeg += 1;
	}

	private final boolean isTextMatchOnly(Peg e) {
		if(e.hasObjectOperation()) {
			return false;
		}
		if(Main.OptimizedLevel < 2) {
			if(e.is(Peg.HasTagging) || e.is(Peg.HasPipe) || e.is(Peg.HasMessage) || e.is(Peg.HasContext)) {
				return false;
			}
		}
		return true;
	}

	private final boolean isCharacter(Peg e) {
		if(e instanceof PegCharacter || e instanceof PegString1) {
			return true;
		}
		if(e instanceof PegString) {
			if(((PegString) e).symbol.length() == 1) {
				return true;
			}
		}
		return false;
	}

	class PegOptimizer extends PegTransformer {
		public Peg transform(Peg e) {
			if(Main.OptimizedLevel > 0) {
				if(e instanceof PegNonTerminal) {
					return this.extractInline(((PegNonTerminal) e));
				}
				if(e instanceof PegChoice) {
					return this.predictChoice(((PegChoice) e));
				}
				if(e instanceof PegNewObject) {
					return this.predictNewObject(((PegNewObject) e));
				}
				if(e instanceof PegUnary) {
					Peg inner = ((PegUnary) e).innerExpr;
					if(e instanceof PegNot) {
						return peepNot(e, inner.clone(this));
					}
					if(e instanceof PegSetter) {
						return peepSetter(e, inner);
					}
				}
				return this.peepHoleTransform(e);
			}
			return null;
		}
		
		private final Peg extractInline(PegNonTerminal label) {
			String ruleName = label.symbol;
			Peg next = ruleSet.getRule(ruleName);
			if(next.is(Peg.CyclicRule) || !isTextMatchOnly(next)) {
				return null;
			}
			Peg n = pegCache.get(ruleName);
			if(n == null) {
				n = next.clone(this);
				pegCache.put(ruleName, n);
			}				
			log(label, "inlining: " + ruleName);
			return n;
		}

		private final void appendChoiceList(UList<Peg> flatList, Peg e) {
			if(flatList.size() > 0 && (e instanceof PegString1 || e instanceof PegCharacter)) {
				Peg prev = flatList.ArrayValues[flatList.size()-1];
				if(prev instanceof PegString1) {
					PegCharacter c = new PegCharacter("");
					c.charset.append(((PegString1) prev).symbol);
					flatList.ArrayValues[flatList.size()-1] = c;
					prev = c;
				}
				if(prev instanceof PegCharacter) {
					UCharset charset = ((PegCharacter) prev).charset;
					if(e instanceof PegCharacter) {
						charset.append(((PegCharacter) e).charset);
						log(prev, "merged character: " + prev);
					}
					else {
						charset.append(((PegString1) e).symbol);
						log(prev, "merged character: " + prev);
					}
					return;
				}
			}
			flatList.add(e);
		}
		
		private final Peg predictChoice(PegChoice orig) {
			UList<Peg> flatList = new UList<Peg>(new Peg[orig.size()]);
			for(int i = 0; i < orig.size(); i++) {
				Peg sub = orig.get(i).clone(this);
				if(sub instanceof PegChoice) {
					log(orig, "flaten choice: " + sub);
					for(int j = 0; j < sub.size(); j++) {
						this.appendChoiceList(flatList, sub.get(j));
					}
				}
				else {
					this.appendChoiceList(flatList, sub);
				}
			}
			if(flatList.size() == 1) {
				log(orig, "removed choice: " + flatList.ArrayValues[0]);
				return flatList.ArrayValues[0];
			}
			if(Main.OptimizedLevel >= 3) {
				int unpredicatableCount = 0;
				for(int i = 0; i < flatList.size(); i++) {
					Peg sub = flatList.ArrayValues[i];
					Object p = sub.getPrediction();
					if(p == null) {
						unpredicatableCount += 1;
					}
					if(p instanceof UCharset) {
						unpredicatableCount += 1;
					}
				}
				if(unpredicatableCount == 0) {
					PegMappedChoice choice = new PegMappedChoice();
					choice.flag = orig.flag;
					choice.list = flatList;
					choice.makeMemo();
					log(orig, "mapped choice: " + choice.map.keys());
					return choice;
				}
				else {
					//System.out.println("@@@@@@@@@@@@@@@@@");
					//System.out.println("Predictable: " + orig);
//					int min = 100000;
//					for(int i = 0; i < flatList.size(); i++) {
//						Peg sub = flatList.ArrayValues[i];
//						String p = sub.getPrediction().toString();
//						System.out.println("\t: '" + p + "'");
//						if(p.length() < min) {
//							min = p.length();
//						}
//					}
//					System.out.println("min: " + min);
//					System.out.println("@@@@@@@@@@@@@@@@@");
					//System.out.println("####### " + unpredicatableCount + " < " + flatList.size());
				}
			}
			PegChoice p = new PegChoice();
			p.flag = orig.flag;
			p.list = flatList;
			return p;
		}

		private final void order(PegList list) {
			for(int i = 0; i < list.size() -1; i++) {
				Peg e0 = list.get(i);
				Peg e1 = list.get(i+1);
				if(e0 instanceof PegTagging) {
					if(e1 instanceof PegString) {
						list.swap(i, i+1);
					}
				}
				if(e0 instanceof PegMessage) {
					
				}
			}
		}
		
		private final Peg predictNewObject(PegNewObject orig) {
			PegNewObject ne = new PegNewObject(orig.leftJoin);
			ne.flag = orig.flag;
			int predictionIndex = -1;
			for(int i = 0; i < orig.size(); i++) {
				Peg sub = orig.get(i).clone(this);
				ne.list.add(sub);
				if(isNeedsObjectCreation(sub) && predictionIndex == -1) {
					predictionIndex = i;
				}
			}
			if(predictionIndex == -1) {
				predictionIndex=0;
			}
			if(Main.OptimizedLevel >= 3 && predictionIndex > 0) {
				log(orig, "prediction index: " + predictionIndex);
				ne.predictionIndex = predictionIndex;
			}
			return ne;
		}
		
		private boolean isNeedsObjectCreation(Peg e) {
			if(e.is(Peg.HasNewObject) || e.is(Peg.HasSetter) || e.is(Peg.HasTagging) || e.is(Peg.HasMessage) || e.is(Peg.HasContext)) {
				return true;
			}
			return false;
		}
		
		public Peg peepHoleTransform(Peg e) {
			if(e instanceof PegString) {
				String symbol = ((PegString) e).symbol;
				if(symbol.length() == 1) {
					log(e, "string1: " + e);
					return new PegString1(e, symbol);
				}
				if(symbol.length() == 0) {
					log(e, "empty: " + e);
					return new PegEmpty(e);
				}
				return null;
			}
			if(e instanceof PegOptional) {
				Peg inner = ((PegOptional) e).innerExpr;
				if(inner instanceof PegString) {
					log(e, "optional string: " + e);
					return new PegOptionalString(e, ((PegString) inner).symbol);
				}
				if(inner instanceof PegCharacter) {
					log(e, "optional character: " + e);
					return new PegOptionalCharacter(e, ((PegCharacter) inner).charset);
				}
			}
			if(e instanceof PegRepeat) {
				PegRepeat re = (PegRepeat)e;
				Peg inner = re.innerExpr;
				if(inner instanceof PegCharacter) {
					UCharset charset = ((PegCharacter) inner).charset;
					Peg ne = null;
					if(re.atleast == 1) {
						log(e, "one more character: " + e);
						ne = new PegOneMoreCharacter(e, charset);
					}
					else {
						log(e, "zero more character: " + e);
						ne = new PegZeroMoreCharacter(e, charset);
					}
					return ne;
				}
				if(isTextMatchOnly(inner)) {
					inner = inner.clone(this);
					if(re.atleast == 1) {
						log(e, "one more text: " + e);
						return new PegOneMoreText(e, inner);
					}
					else {
						log(e, "zero more text: " + e);
						return new PegZeroMoreText(e, inner);
					}
				}
			}
			if(e instanceof PegSequence) {
				PegSequence seq = (PegSequence)e;
				if(seq.size() == 2 && seq.get(1) instanceof PegAny) {
					Peg ne = this.transform(seq.get(0));
					if(ne instanceof PegNotAtom) {
						log(e, "merged not and any: " + e);
						((PegNotAtom) ne).setNextAny(e);
						return ne;
					}

				}
			}
			return null;
		}
		
		private final Peg peepNot(Peg orig, Peg inner) {
			if(inner instanceof PegString) {
				log(orig, "not string:" + inner);
				return new PegNotString(orig, ((PegString) inner).symbol);
			}
			if(inner instanceof PegString1) {
				log(orig, "not string1: " + inner);
				return new PegNotString1(orig, ((PegString1) inner).symbol);
			}
			if(inner instanceof PegCharacter) {
				log(orig, "not character: " + inner);
				return new PegNotCharacter(orig, ((PegCharacter) inner).charset);
			}
			PegNot ne = new PegNot(inner);
			ne.flag = orig.flag;
			return ne;
		}

		private final Peg peepSetter(Peg orig, Peg inner) {
			if(!inner.is(Peg.HasNewObject)) {
				log(orig, "removed stupid peg: " + orig);
				return inner.clone(this);
			}
			return null;
		}

		
	}
	
//	private void appendPegCache(String name, Peg e) {
//		Peg defined = this.pegCache.get(name, null);
//		if(defined != null) {
//			e = defined.appendAsChoice(e);
//		}
//		this.pegCache.put(name, e);
//	}

	public final Peg getRule(String name) {
		return this.pegCache.get(name, null);
	}

//	private final Peg getRightJoinRule(String name) {
//		return this.pegCache.get(this.nameRightJoinName(name), null);
//	}
	
	class PegEmpty extends PegOptimized {
		public PegEmpty(Peg orig) {
			super(orig);
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			return left;
		}
	}

	class PegString1 extends PegOptimized {
		char symbol;
		public PegString1(Peg orig, String token) {
			super(orig);
			this.symbol = token.charAt(0);
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			long pos = context.getPosition();
			if(context.charAt(pos) == this.symbol) {
				context.consume(1);
				return left;
			}
			return context.foundFailure(this);
		}
		public Object getPrediction() {
			return ""+symbol;
		}
	}
	
	abstract class PegNotAtom extends PegOptimized {
		boolean nextAny = false;
		public PegNotAtom(Peg orig) {
			super(orig);
		}
		public final void setNextAny(Peg orig) {
			this.orig = orig;
			this.nextAny = true;
		}
		protected final PegObject matchNextAny(PegObject left, ParserContext context) {
			if(context.hasChar()) {
				context.consume(1);
				return left;
			}
			else {
				return context.foundFailure(this);
			}
		}
	}

	class PegNotString extends PegNotAtom {
		String symbol;
		public PegNotString(Peg orig, String token) {
			super(orig);
			this.symbol = token;
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			long pos = context.getPosition();
			if(context.match(this.symbol)) {
				context.setPosition(pos);
				return context.foundFailure(this);
			}
			if(this.nextAny) {
				return this.matchNextAny(left, context);
			}
			return left;
		}
	}

	class PegNotString1 extends PegNotAtom {
		char symbol;
		public PegNotString1(Peg orig, char token) {
			super(orig);
			this.symbol = token;
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			if(this.symbol == context.getChar()) {
				return context.foundFailure(this);
			}
			if(this.nextAny) {
				return this.matchNextAny(left, context);
			}
			return left;
		}
	}	
	
	class PegNotCharacter extends PegNotAtom {
		UCharset charset;
		public PegNotCharacter(Peg orig, UCharset charset) {
			super(orig);
			this.charset = charset;
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			long pos = context.getPosition();
			if(context.match(this.charset)) {
				context.setPosition(pos);
				return context.foundFailure(this);
			}
			if(this.nextAny) {
				return this.matchNextAny(left, context);
			}
			return left;
		}
	}

	class PegOptionalString extends PegOptimized {
		String symbol;
		public PegOptionalString(Peg orig, String token) {
			super(orig);
			this.symbol = token;
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			context.match(this.symbol);
			return left;
		}
	}

	class PegOptionalCharacter extends PegOptimized {
		UCharset charset;
		public PegOptionalCharacter(Peg orig, UCharset charset) {
			super(orig);
			this.charset = charset;
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			context.match(this.charset);
			return left;
		}
	}
	
	class PegOneMoreText extends PegOptimized {
		Peg repeated;
		public PegOneMoreText(Peg orig, Peg repeated) {
			super(orig);
			this.repeated = repeated;
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			long pos = context.getPosition();
			PegObject node = this.repeated.simpleMatch(left, context);
			if(node.isFailure()) {
				context.setPosition(pos);
				return node;
			}
			left = node;
			while(context.hasChar()) {
				pos = context.getPosition();
				node = this.repeated.simpleMatch(left, context);
				if(node.isFailure() || pos == context.getPosition()) {
					context.setPosition(pos);
					break;
				}
				left = node;
			}
			return left;
		}
	}

	class PegZeroMoreText extends PegOptimized {
		Peg repeated;
		public PegZeroMoreText(Peg orig, Peg repeated) {
			super(orig);
			this.repeated = repeated;
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			while(context.hasChar()) {
				long pos = context.getPosition();
				PegObject node = this.repeated.simpleMatch(left, context);
				if(node.isFailure() || pos == context.getPosition()) {
					context.setPosition(pos);
					break;
				}
				left = node;
			}
			return left;
		}
	}

	
	class PegOneMoreCharacter extends PegOptimized {
		UCharset charset;
		public PegOneMoreCharacter(Peg orig, UCharset charset) {
			super(orig);
			this.charset = charset;
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			long pos = context.getPosition();
			char ch = context.charAt(pos);
			if(!this.charset.match(ch)) {
				return context.foundFailure(this);
			}
			pos++;
			for(;context.hasChar();pos++) {
				ch = context.charAt(pos);
				if(!this.charset.match(ch)) {
					break;
				}
			}
			context.setPosition(pos);
			return left;
		}
	}

	class PegZeroMoreCharacter extends PegOptimized {
		UCharset charset;
		public PegZeroMoreCharacter(Peg orig, UCharset charset) {
			super(orig);
			this.charset = charset;
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			long pos = context.getPosition();
			for(;context.hasChar();pos++) {
				char ch = context.charAt(pos);
				if(!this.charset.match(ch)) {
					break;
				}
			}
			context.setPosition(pos);
			return left;
		}
	}
	
	class PegMappedChoice extends PegChoice {
		private int tokenSize;
		private UMap<Peg> map = new UMap<Peg>();
		public void makeMemo() {
			int min = 1000000;
			for(int i = 0; i < this.list.size(); i++) {
				Peg sub = this.list.ArrayValues[i];
				String p = sub.getPrediction().toString();
				if(p.length() < min) {
					min = p.length();
				}
			}
			this.tokenSize = min;
			this.map = new UMap<Peg>();
			for(int i = 0; i < this.list.size(); i++) {
				Peg sub = this.list.ArrayValues[i];
				String token = sub.getPrediction().toString().substring(0, min);
				Peg.addAsChoice(this.map, token, sub);
			}
		}
		@Override
		public PegObject simpleMatch(PegObject left, ParserContext context) {
			long pos = context.getPosition();
			String token = context.substring(pos, pos + this.tokenSize);
			Peg e = this.map.get(token);
			if(e != null) {
				PegObject node2 = e.simpleMatch(left, context);
				if(node2.isFailure()) {
					context.setPosition(pos);
				}
				return node2;
			}
			return context.foundFailure(this);
		}
	}
	
	protected final static int FifoSize = 100;

	public final class ObjectMemo {
		ObjectMemo next;
		Peg  keypeg;
		PegObject generated;
		int  consumed;
	}

	protected Map<Long, ObjectMemo> memoMap;
	private ObjectMemo UnusedMemo = null;

	public void initMemo() {
	}

	private final ObjectMemo newMemo() {
		if(UnusedMemo != null) {
			ObjectMemo m = this.UnusedMemo;
			this.UnusedMemo = m.next;
			return m;
		}
		else {
			ObjectMemo m = new ObjectMemo();
			this.memoSize += 1;
			return m;
		}
	}
	
	protected final void setMemo(long keypos, Peg keypeg, PegObject generated, int consumed) {
		ObjectMemo m = null;
		m = newMemo();
		m.keypeg = keypeg;
		m.generated = generated;
		m.consumed = consumed;
		m.next = this.memoMap.get(keypos);
		this.memoMap.put(keypos, m);
//		if(keypeg == peg) {
//			System.out.println("cache " + keypos + ", " + keypeg);
//		}
	}

	protected final ObjectMemo getMemo(Peg keypeg, long keypos) {
		ObjectMemo m = this.memoMap.get(keypos);
		while(m != null) {
			if(m.keypeg == keypeg) {
				this.memoHit += 1;
				return m;
			}
			m = m.next;
		}
		this.memoMiss += 1;
		return m;
	}
	
	protected final void unusedMemo(ObjectMemo m) {
		this.appendMemo2(m, UnusedMemo);
		UnusedMemo = m;
	}
	
	private void appendMemo2(ObjectMemo m, ObjectMemo n) {
		while(m.next != null) {
			m = m.next;
		}
		m.next = n;
	}			
	
}

