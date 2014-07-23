package org.libbun.peg4d;

import java.util.HashMap;

import org.libbun.Main;
import org.libbun.UCharset;
import org.libbun.UList;
import org.libbun.UMap;
import org.libbun.UStringBuilder;

public abstract class Peg {
	public final static int CyclicRule       = 1;
	public final static int HasNonTerminal    = 1 << 1;
	public final static int HasString         = 1 << 2;
	public final static int HasCharacter      = 1 << 3;
	public final static int HasAny            = 1 << 4;
	public final static int HasRepetation     = 1 << 5;
	public final static int HasOptional       = 1 << 6;
	public final static int HasChoice         = 1 << 7;
	public final static int HasAnd            = 1 << 8;
	public final static int HasNot            = 1 << 9;
	
	public final static int HasNewObject      = 1 << 10;
	public final static int HasSetter         = 1 << 11;
	public final static int HasTagging        = 1 << 12;
	public final static int HasMessage        = 1 << 13;
	public final static int HasPipe           = 1 << 14;
	public final static int HasCatch          = 1 << 15;
	public final static int HasContext        = 1 << 16;
	public final static int Mask = HasNonTerminal | HasString | HasCharacter | HasAny
	                             | HasRepetation | HasOptional | HasChoice | HasAnd | HasNot
	                             | HasNewObject | HasSetter | HasTagging | HasMessage 
	                             | HasPipe | HasCatch | HasContext;
	
	public final static int Debug             = 1 << 24;
	private static int PegIdCount = 0;
	
	int        flag     = 0;
	int        pegid2   = 0;
	String     ruleName = null;

	ParserSource source = null;
	int       sourcePosition = 0;
	
	protected Peg(int flag) {
		this.flag = flag;
		PegIdCount += 1;
		this.pegid2 = PegIdCount;
	}
	
	protected abstract Peg clone(PegTransformer tr);
	protected abstract void stringfy(UStringBuilder sb, PegFormatter fmt);
	protected abstract void makeList(String startRule, Grammar parser, UList<String> list, UMap<String> set);
	protected abstract void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited);
	public abstract PegObject simpleMatch(PegObject left, ParserContext context);
	
	public Object getPrediction() {
		return null;
	}

	public final boolean isPredictablyAcceptable(char ch) {
		Object predicted = this.getPrediction();
		if(predicted != null) {
			if(predicted instanceof String) {
				String p = (String)predicted;
				if(p.charAt(0) == ch) {
					return true;
				}
				return false;
			}
			if(predicted instanceof UCharset) {
				UCharset p = (UCharset)predicted;
				if(p.match(ch)) {
					return true;
				}
			}
			return false;
		}
		return true;
	}

//	public abstract void accept(PegVisitor visitor);

	public final boolean is(int uflag) {
		return ((this.flag & uflag) == uflag);
	}

	public final void set(int uflag) {
		this.flag = this.flag | uflag;
	}

	protected final void derived(Peg e) {
		this.flag |= (e.flag & Peg.Mask);
	}
	
	public int size() {
		return 0;
	}
	public Peg get(int index) {
		return this;  // to avoid NullPointerException
	}
	
	public Peg get(int index, Peg def) {
		return def;
	}

	@Override public String toString() {
		UStringBuilder sb = new UStringBuilder();
		this.stringfy(sb, new PegFormatter());
		if(this.ruleName != null) {
			sb.append(" defined in ");
			sb.append(this.ruleName);
		}
		return sb.toString();
	}

	protected PegObject performMatch(PegObject left, ParserContext context) {
		if(this.is(Peg.Debug)) {
			PegObject node2 = this.simpleMatch(left, context);
			String msg = "matched";
			if(node2.isFailure()) {
				msg = "failed";
			}
			String line = context.formatErrorMessage(msg, this.toString());
			System.out.println(line + "\n\tnode #" + left + "# => #" + node2 + "#");
			return node2;
		}
		return this.simpleMatch(left, context);
	}

	public final String format(String name, PegFormatter fmt) {
		UStringBuilder sb = new UStringBuilder();
		fmt.formatRule(sb, name, this);
		return sb.toString();
	}

	public final String format(String name) {
		return this.format(name, new PegFormatter());
	}

	protected final void report(String type, String msg) {
		if(!Main.VerboseStat) {
			if(this.source != null) {
				System.out.println(this.source.formatErrorMessage(type, this.sourcePosition-1, msg));
			}
			else {
				System.out.println(type + ": " + msg + "\n\t" + this);
			}
		}
	}
	
	protected void warning(String msg) {
		if(Main.VerbosePeg && !Main.VerboseStat) {
			Main._PrintLine("PEG warning: " + msg);
		}
	}
	
	public final boolean hasObjectOperation() {
		return this.is(Peg.HasNewObject) || this.is(Peg.HasSetter);
	}
	
	public final static void addAsChoice(UMap<Peg> map, String key, Peg e) {
		Peg defined = map.get(key);
		if(defined != null) {
			defined = defined.appendAsChoice(e);
			map.put(key, defined);
		}
		else {
			map.put(key, e);
		}
	}

	public final static Peg appendAsChoice(Peg e, Peg e1) {
		if(e == null) {
			return e1;
		}
		if(e1 == null) {
			return e;
		}
		if(e instanceof PegChoice) {
			((PegChoice) e).extend(e1);
			return e;
		}
		else {
			PegChoice choice = new PegChoice(0);
			choice.extend(e);
			choice.extend(e1);
			return choice;
		}
	}

	
	public final PegChoice appendAsChoice(Peg e) {
		if(this instanceof PegChoice) {
			((PegChoice)this).extend(e);
			return ((PegChoice)this);
		}
		else {
			PegChoice choice = new PegChoice(0);
			choice.add(this);
			choice.extend(e);
			return choice;
		}
	}

	int statCallCount = 0;
	int statRepeatCount = 0;

//	// statistics 
//	class CallCounter {
//		String ruleName;
//		int total = 0;
//		int repeated = 0;
//	}
//	private HashMap<Long, CallCounter> countMap;
//	private CallCounter statCallCounter;
//	public final void countCall(ParserContext p, String name, long pos) {
//		Long key = pos;
//		if(this.countMap == null) {
//			this.countMap = new HashMap<>();
//			this.statCallCounter = new CallCounter();
//			this.statCallCounter.ruleName = name;
//			p.setCallCounter(this.statCallCounter);
//		}
//		this.statCallCounter.total += 1;
//		CallCounter c = this.countMap.get(key);
//		if(c == null) {
//			this.countMap.put(key, this.statCallCounter);
//		}
//		else {
//			c.repeated += 1;
//		}
//	}
//	public final boolean isRepeatedCall(long pos) {
//		if(this.countMap != null) {
//			CallCounter c = this.countMap.get(pos);
//			return c != null;
//		}
//		return false;
//	}
	
}

abstract class PegTransformer {
	public abstract Peg transform(Peg e);
}

class PegNoTransformer extends PegTransformer {
	@Override
	public Peg transform(Peg e) {
		return e;
	}
}

abstract class PegTerm extends Peg {
	public PegTerm (int flag) {
		super(flag);
	}
	@Override
	protected void makeList(String startRule, Grammar rules, UList<String> list, UMap<String> set) {
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		this.ruleName = visitingName;
	}
	@Override
	public final int size() {
		return 0;
	}
	@Override
	public final Peg get(int index) {
		return this;  // just avoid NullPointerException
	}
}

class PegNonTerminal extends PegTerm {
	String symbol;
	Object predicted = null;
	public PegNonTerminal(int flag, String ruleName) {
		super(flag | Peg.HasNonTerminal);
		this.symbol = ruleName;
	}
	public PegNonTerminal(String ruleName) {
		this(0, ruleName);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegNonTerminal(this.flag, this.symbol);
		}
		return ne;
	}
	@Override
	protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatNonTerminal(sb, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		Peg next = rules.getRule(this.symbol);
		if( next == null && !Main.VerboseStat) {
			Main._PrintLine(this.source.formatErrorMessage("error", this.sourcePosition, "undefined label: " + this.symbol));
			rules.foundError = true;
			return;
		}
		if(startRule.ruleName.equals(this.symbol)) {
			startRule.set(CyclicRule);
		}
		else if(visited != null && !visited.hasKey(this.symbol)) {
			visited.put(this.symbol, this.symbol);
			this.verify2(startRule, rules, this.symbol, visited);
		}
		this.derived(next);
		startRule.derived(this);
	}
	@Override
	public Object getPrediction() {
		return this.predicted;
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchNonTerminal(left, this);
	}
	@Override
	protected void makeList(String startRule, Grammar parser, UList<String> list, UMap<String> set) {
		if(!set.hasKey(this.symbol)) {
			Peg next = parser.getRule(this.symbol);
			list.add(this.symbol);
			set.put(this.symbol, this.symbol);
			next.makeList(startRule, parser, list, set);
		}
	}
}

class PegString extends PegTerm {
	String text;
	public PegString(int flag, String text) {
		super(Peg.HasString | flag);
		this.text = text;
	}
	public PegString(String text) {
		this(0, text);
	}

	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegString(this.flag, this.text);
		}
		return ne;
	}
	@Override
	protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatString(sb, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
	}
	public Object getPrediction() {
		if(this.text.length() > 0) {
			return this.text;
		}
		return null;
	}
	
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchString(left, this);
	}
}

class PegAny extends PegTerm {
	public PegAny(int flag) {
		super(Peg.HasAny | flag);
	}
	public PegAny() {
		this(0);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegAny(this.flag);
		}
		return ne;
	}
	@Override
	protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatAny(sb, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchAny(left, this);
	}
}

class PegCharacter extends PegTerm {
	UCharset charset;
	public PegCharacter(int flag, String token) {
		super(Peg.HasCharacter | flag);
		this.charset = new UCharset(token);
	}
	public PegCharacter(int flag, UCharset charset) {
		super(Peg.HasCharacter | flag);
		this.charset = charset;
	}
	public PegCharacter(String text) {
		this(0, text);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegCharacter(this.flag, this.charset);
		}
		return ne;
	}
	@Override
	protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatCharacter(sb, this);
	}

	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchCharacter(left, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasCharacter);
		startRule.derived(this);
	}
	public Object getPrediction() {
		return this.charset;
	}
}


abstract class PegUnary extends Peg {
	Peg inner;
	public PegUnary(int flag, Peg e) {
		super(flag);
		this.inner = e;
		this.derived(e);
	}
	@Override
	public final int size() {
		return 1;
	}
	@Override
	public final Peg get(int index) {
		return this.inner;
	}
	@Override
	protected void makeList(String startRule, Grammar parser, UList<String> list, UMap<String> set) {
		this.inner.makeList(startRule, parser, list, set);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		this.ruleName = visitingName;
		this.inner.verify2(startRule, rules, visitingName, visited);
		this.derived(this.inner);
	}
}

class PegOptional extends PegUnary {
	public PegOptional(int flag, Peg e) {
		super(flag | Peg.HasOptional, e);
	}
	public PegOptional(Peg e) {
		this(0, e);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegOptional(this.flag, this.inner.clone(tr));
		}
		return ne;
	}
	@Override
	protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatOptional(sb, this);
	}

	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchOptional(left, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
	}
}

class PegRepeat extends PegUnary {
	public int atleast = 0; 
	protected PegRepeat(int flag, Peg e, int atLeast) {
		super(flag | Peg.HasRepetation, e);
		this.atleast = atLeast;
	}
	public PegRepeat(Peg e, int atleast) {
		this(0, e, atleast);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegRepeat(this.flag, this.inner.clone(tr), this.atleast);
		}
		return ne;
	}
	@Override
	protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatRepeat(sb, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
	}

	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchRepeat(left, this);
	}

	public Object getPrediction() {
		if(this.atleast > 0) {
			this.inner.getPrediction();
		}
		return null;
	}
}

class PegAnd extends PegUnary {
	PegAnd(int flag, Peg e) {
		super(flag | Peg.HasAnd, e);
	}
	public PegAnd(Peg e) {
		this(0, e);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegAnd(this.flag, this.inner.clone(tr));
		}
		return ne;
	}
	@Override
	protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatAnd(sb, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasAnd);
		startRule.derived(this);
		if(visited == null) { /* in the second phase */
			if(this.inner.is(Peg.HasNewObject) || this.inner.is(Peg.HasSetter)) {
				this.report("warning", "ignored object operation");
			}
		}
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchAnd(left, this);
	}
	public Object getPrediction() {
		return this.inner.getPrediction();
	}
}

class PegNot extends PegUnary {
	PegNot(int flag, Peg e) {
		super(Peg.HasNot | flag, e);
	}
	public PegNot(Peg e) {
		this(0, e);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegNot(this.flag, this.inner.clone(tr));
		}
		return ne;
	}
	@Override
	protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatNot(sb, this);
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchNot(left, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
		if(visited == null) { /* in the second phase */
			if(this.inner.hasObjectOperation()) {
				this.report("warning", "ignored object operation");
			}
		}
	}
}

abstract class PegList extends Peg {
	protected UList<Peg> list;
	PegList(int flag) {
		super(flag);
		this.list = new UList<Peg>(new Peg[2]);
	}
	public final int size() {
		return this.list.size();
	}
	public final Peg get(int index) {
		return this.list.ArrayValues[index];
	}
	public final Peg get(int index, Peg def) {
		if(index < this.size()) {
			return this.list.ArrayValues[index];
		}
		return def;
	}
	public void add(Peg e) {
		this.list.add(e);
	}
	public final void swap(int i, int j) {
		Peg e = this.list.ArrayValues[i];
		this.list.ArrayValues[i] = this.list.ArrayValues[j];
		this.list.ArrayValues[j] = e;
	}
	@Override
	protected void makeList(String startRule, Grammar parser, UList<String> list, UMap<String> set) {
		for(int i = 0; i < this.size(); i++) {
			this.get(i).makeList(startRule, parser, list, set);
		}
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		this.ruleName = visitingName;
		for(int i = 0; i < this.size(); i++) {
			Peg e  = this.get(i);
			e.verify2(startRule, rules, visitingName, visited);
			this.derived(e);
		}
	}
}

class PegSequence extends PegList {
	PegSequence(int flag) {
		super(flag);
	}
	public PegSequence() {
		this(0);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			PegList l = new PegSequence(this.flag);
			for(int i = 0; i < this.list.size(); i++) {
				Peg e = this.get(i).clone(tr);
				l.list.add(e);
				this.derived(e);
			}
			return l;
		}
		return ne;
	}
	@Override
	protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatSequence(sb, this);
	}

	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
	}
	
	public Object getPrediction() {
		return this.get(0).getPrediction();
	}
	
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchSequence(left, this);
	}

}

class PegChoice extends PegList {
	PegChoice(int flag) {
		super(flag | Peg.HasChoice);
	}
	public PegChoice() {
		this(0);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			PegList l = new PegChoice(this.flag);
			for(int i = 0; i < this.list.size(); i++) {
				Peg e = this.get(i).clone(tr);
				l.list.add(e);
				this.derived(e);
			}
			return l;
		}
		return ne;
	}

	public void extend(Peg e) {
		if(e instanceof PegChoice) {
			for(int i = 0; i < e.size(); i++) {
				this.add(e.get(i));
			}
		}
		else if(e != null) {
			this.list.add(e);
		}
	}
	
	@Override protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatChoice(sb, this);
	}
	
	protected int predictable = -1;
	protected UCharset predicted = null;
	protected int pretextSize = 0; 
	
	public Object getPrediction() {
		if(this.predictable == -1) {
			UCharset u = new UCharset("");
			this.predictable = 0;
			this.predicted = null;
			this.pretextSize = 100000;
			for(int i = 0; i < this.size(); i++) {
				Object p = this.get(i).getPrediction();
				if(p instanceof UCharset) {
					u.append((UCharset)p);
					this.predictable += 1;
					this.pretextSize = 1;
				}
				if(p instanceof String) {
					String text = (String)p;
					if(text.length() > 0) {
						u.append(text.charAt(0));
						this.predictable += 1;
						if(text.length() < this.pretextSize) {
							this.pretextSize = text.length();
						}
					}
				}
			}
			if(this.predictable == this.size()) {
				this.predicted = u;
			}
			else {
				this.pretextSize = 0;
			}
		}
		return this.predicted;
	}
	
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchChoice(left, this);
	}
	
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
		if(visited == null) {  // in the second phase
			this.predictable = -1;
			this.predicted = null; // reset
			if(this.getPrediction() == null) {
				rules.statUnpredictableChoice += 1;
			}
			else {
				rules.statPredictableChoice += 1;
			}
		}
	}
}

class PegSetter extends PegUnary {
	public int index;
	public PegSetter(int flag, Peg e, int index) {
		super(flag | Peg.HasSetter, e);
		this.index = index;
	}
	public PegSetter(Peg e, int index) {
		this(0, e, index);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegSetter(this.flag, this.inner.clone(tr), this.index);
		}
		return ne;
	}
	@Override protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatSetter(sb, this);
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchSetter(left, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
		if(visited == null) { /* in the second phase */
			if(!this.inner.is(Peg.HasNewObject)) {
				this.report("warning", "no object is generated");
			}
		}
	}
}

class PegTagging extends PegTerm {
	String symbol;
	public PegTagging(int flag, String tagName) {
		super(Peg.HasTagging | flag);
		this.symbol = tagName;
	}
	public PegTagging(String tagName) {
		this(0, tagName);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegTagging(this.flag, this.symbol);
		}
		return ne;
	}
	@Override
	protected final void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatTagging(sb, this);
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchTag(left, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		//rules.addObjectLabel(this.symbol);
		startRule.derived(this);
	}
}

class PegMessage extends PegTerm {
	String symbol;
	public PegMessage(int flag, String message) {
		super(flag | Peg.HasMessage);
		this.symbol = message;
	}
	public PegMessage(String message) {
		this(0, message);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegMessage(this.flag, this.symbol);
		}
		return ne;
	}
	@Override
	protected final void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatMessage(sb, this);
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchMessage(left, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
	}
}

class PegNewObject extends PegList {
	boolean leftJoin = false;
	String nodeName = "noname";
	int predictionIndex = 0;
	public PegNewObject(int flag, boolean leftJoin) {
		super(flag | Peg.HasNewObject);
		this.leftJoin = leftJoin;
	}
	public PegNewObject(boolean leftJoin) {
		this(0, leftJoin);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			PegList l = new PegNewObject(this.flag, this.leftJoin);
			l.ruleName = this.ruleName;
			for(int i = 0; i < this.list.size(); i++) {
				l.list.add(this.get(i).clone(tr));
			}
			return l;
		}
		return ne;
	}

	public void add(Peg e) {
		if(e instanceof PegSequence) {
			for(int i =0; i < e.size(); i++) {
				this.list.add(e.get(i));
			}
		}
		else {
			this.list.add(e);
		}
	}

	@Override protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatNewObject(sb, this);
	}

	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
	}
	public Object getPrediction() {
		return this.get(0).getPrediction();
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchNewObject(left, this);
	}
}

class PegExport extends PegUnary {
	public PegExport(int flag, Peg e) {
		super(flag, e);
	}
	public PegExport(Peg e) {
		this(0, e);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			return new PegExport(this.flag, this.inner.clone(tr));
		}
		return ne;
	}
	@Override protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatExport(sb, this);
	}
	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchExport(left, this);
	}
}

class PegIndent extends PegTerm {
	PegIndent(int flag) {
		super(flag | Peg.HasContext);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegIndent(this.flag);
		}
		return ne;
	}
	@Override protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatIndent(sb, this);
	}

	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchIndent(left, this);
	}
}

class PegIndex extends PegTerm {
	int index;
	PegIndex(int flag, int index) {
		super(flag | Peg.HasContext);
		this.index = index;
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegIndex(this.flag, this.index);
		}
		return ne;
	}
	
	@Override protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		fmt.formatIndex(sb, this);
	}

	@Override
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchIndex(left, this);
	}
}


abstract class PegOptimized extends Peg {
	Peg orig;
	PegOptimized (Peg orig) {
		super(orig.flag);
		this.orig = orig;
		this.ruleName = orig.ruleName;
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		return this;
	}
	@Override
	protected void stringfy(UStringBuilder sb, PegFormatter fmt) {
		this.orig.stringfy(sb, fmt);
	}
	protected void verify2(Peg startRule, Grammar rules, String visitingName, UMap<String> visited) {
		this.ruleName = visitingName;
		this.orig.verify2(startRule, rules, visitingName, visited);
		this.derived(this.orig);
	}
	@Override
	protected void makeList(String startRule, Grammar parser, UList<String> list, UMap<String> set) {
	}

}

//class PegCType extends PegAtom {
//	static public HashSet<String> typedefs = new HashSet<String>();
//	boolean AddType = false;
//	PegCType(String leftLabel, boolean AddType) {
//		super(AddType ? "addtype" : "ctype");
//		this.AddType = AddType;
//	}
//
//	@Override
//	protected Peg clone(String ns) {
//		return this;
//	}
//	
//	
//	@Override
//	protected PegObject simpleMatch(PegObject left, ParserContext context) {
//		if(left.source != null) {
//			if(AddType) {
//				if(left.tag.equals("#DeclarationNoAttribute") && left.AST.length >= 2) {
//					// left.AST = [typedef struct A, StructA]
//					PegObject first = left.AST[0];
//					if(first.AST.length >= 2) {
//						String firstText = first.AST[0].getText().trim();
//						// first is "typedef"
//						if(first.AST[0].tag.equals("#storageclassspecifier") && firstText.equals("typedef")) {
//							PegObject second = left.AST[1];
//							for (int i = 0; i < second.AST.length; i++) {
//								PegObject decl = second.get(i);
//								if(decl.tag.equals("#declarator")) {
//									// "typedef struct A StructA;"
//									// add new typename StructA
//									System.out.println(decl.get(decl.AST.length - 1).getText());
//									typedefs.add(decl.get(decl.AST.length - 1).getText());
//								}
//							}
//							return left;
//						}
//					}
//				}
//			}
//			else {
//				String name = left.getText().trim();
//				if (!typedefs.contains(name)) {
//					return new PegObject(null); //not match
//				}
//			}
//		}
//		return left;
//	}
//
//	@Override
//	public void accept(PegVisitor visitor) {
//		// TODO Auto-generated method stub
//	}
//
//}
