package org.libbun.peg4d;

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

	
	int       flag     = 0;
	String    ruleName = null;

	ParserSource source = null;
	int       sourcePosition = 0;
	
	protected abstract Peg clone(PegTransformer tr);
	protected abstract void stringfy(UStringBuilder sb, boolean debugMode);
	protected abstract boolean makeList(String startRule, PegRuleSet parser, UList<String> list, UMap<String> set);
	protected abstract void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited);
	protected abstract PegObject simpleMatch(PegObject left, ParserContext context);
	public Object getPrediction() {
		return null;
	}
//	public abstract void accept(PegVisitor visitor);

	public final boolean is(int uflag) {
		return ((this.flag & uflag) == uflag);
	}

	public final void set(int uflag) {
		this.flag = this.flag | uflag;
	}

	public final void derived(Peg e) {
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
		this.stringfy(sb, true);
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

	public final String toPrintableString(String name, String Setter, String Choice, String SemiColon, boolean debugMode) {
		UStringBuilder sb = new UStringBuilder();
		sb.append(name);
		sb.append(Setter);
		if(this instanceof PegChoice) {
			for(int i = 0; i < this.size(); i++) {
				if(i > 0) {
					sb.append(Choice);
				}
				this.get(i).stringfy(sb, debugMode);
			}
		}
		else {
			this.stringfy(sb, debugMode);
		}
		sb.append(SemiColon);
		return sb.toString();
	}
	
	protected final void report(String type, String msg) {
		if(this.source != null) {
			System.out.println(this.source.formatErrorMessage(type, this.sourcePosition-1, msg));
		}
		else {
			System.out.println(type + ": " + msg + "\n\t" + this);
		}
	}
	
	protected void warning(String msg) {
		if(Main.VerbosePeg) {
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

	public final PegChoice appendAsChoice(Peg e) {
		if(this instanceof PegChoice) {
			((PegChoice)this).extend(e);
			return ((PegChoice)this);
		}
		else {
			PegChoice choice = new PegChoice();
			choice.add(this);
			choice.extend(e);
			return choice;
		}
	}

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

abstract class PegAtom extends Peg {
	String symbol;
	public PegAtom (String symbol) {
		super();
		this.symbol = symbol;
	}
	@Override
	protected void stringfy(UStringBuilder sb, boolean debugMode) {
		sb.append(this.symbol);
	}	
	@Override
	protected boolean makeList(String startRule, PegRuleSet rules, UList<String> list, UMap<String> set) {
		return false;
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
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

class PegString extends PegAtom {
	public PegString(String symbol) {
		super(symbol);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegString(this.symbol);
			ne.flag = this.flag;
		}
		return ne;
	}
	public final static String quoteString(String s) {
		char quote = '\'';
		if(s.indexOf("'") != -1) {
			quote = '"';
		}
		return UCharset._QuoteString(quote, s, quote);
	}
	@Override
	protected void stringfy(UStringBuilder sb, boolean debugMode) {
		sb.append(PegString.quoteString(this.symbol));
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasString);
		startRule.derived(this);
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchString(left, this);
	}
	public Object getPrediction() {
		return this.symbol;
	}
}

class PegAny extends PegAtom {
	public PegAny() {
		super(".");
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegAny();
			ne.flag = this.flag;
		}
		return ne;
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchAny(left, this);
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasAny);
		startRule.derived(this);
	}
}

class PegCharacter extends PegAtom {
	UCharset charset;
	public PegCharacter(String token) {
		super(token);
		this.charset = new UCharset(token);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegCharacter(this.symbol);
			ne.flag = this.flag;
		}
		return ne;
	}
	@Override
	protected void stringfy(UStringBuilder sb, boolean debugMode) {
		sb.append("[" + this.charset, "]");
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchCharacter(left, this);
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasCharacter);
		startRule.derived(this);
	}
	public Object getPrediction() {
		return this.charset;
	}
}

class PegLabel extends PegAtom {
	public PegLabel(String ruleName) {
		super(ruleName);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegLabel(this.symbol);
			ne.flag = this.flag;

		}
		return ne;
	}
	@Override protected PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchLabel(left, this);
	}
	@Override
	protected boolean makeList(String startRule, PegRuleSet parser, UList<String> list, UMap<String> set) {
		boolean cyclic = false;
		if(startRule.equals(this.symbol)) {
			cyclic = true;
		}
		if(!set.hasKey(this.symbol)) {
			Peg next = parser.getRule(this.symbol);
			if(next != null) {   // listRule is called before verification
				list.add(this.symbol);
				set.put(this.symbol, this.symbol);
				if(next.makeList(startRule, parser, list, set)) {
					cyclic = true;
				}
			}
		}
		return cyclic;
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasNonTerminal);
		Peg next = rules.getRule(this.symbol);
		if( next == null ) {
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
}

abstract class PegUnary extends Peg {
	Peg innerExpr;
	boolean prefix;
	public PegUnary(Peg e, boolean prefix) {
		super();
		this.innerExpr = e;
		this.prefix = prefix;
	}
	@Override
	public final int size() {
		return 1;
	}
	@Override
	public final Peg get(int index) {
		return this.innerExpr;
	}
	protected abstract String getOperator();
	@Override
	protected final void stringfy(UStringBuilder sb, boolean debugMode) {
		if(this.prefix) {
			sb.append(this.getOperator());
		}
		if(this.innerExpr instanceof PegAtom || this.innerExpr instanceof PegNewObject) {
			this.innerExpr.stringfy(sb, debugMode);
		}
		else {
			sb.append("(");
			this.innerExpr.stringfy(sb, debugMode);
			sb.append(")");
		}
		if(!this.prefix) {
			sb.append(this.getOperator());
		}
	}
	@Override
	protected boolean makeList(String startRule, PegRuleSet parser, UList<String> list, UMap<String> set) {
		return this.innerExpr.makeList(startRule, parser, list, set);
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		this.ruleName = visitingName;
		this.innerExpr.verify2(startRule, rules, visitingName, visited);
		this.derived(this.innerExpr);
	}
}

class PegOptional extends PegUnary {
	public PegOptional(Peg e) {
		super(e, false);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegOptional(this.innerExpr.clone(tr));
			ne.flag = this.flag;

		}
		return ne;
	}
	@Override
	protected String getOperator() {
		return "?";
	}
	@Override protected PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchOptional(left, this);
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasOptional);
		startRule.derived(this);
	}
}

class PegRepeat extends PegUnary {
	public int atleast = 0; 
	protected PegRepeat(Peg e, int atLeast) {
		super(e, false);
		this.atleast = atLeast;
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegRepeat(this.innerExpr.clone(tr), this.atleast);
			ne.flag = this.flag;
		}
		return ne;
	}
	@Override
	protected String getOperator() {
		return (this.atleast > 0) ? "+" : "*";
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasRepetation);
		startRule.derived(this);
	}

	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchRepeat(left, this);
	}

	public Object getPrediction() {
		if(this.atleast > 0) {
			this.innerExpr.getPrediction();
		}
		return null;
	}
}

class PegAnd extends PegUnary {
	PegAnd(Peg e) {
		super(e, true);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegAnd(this.innerExpr.clone(tr));
			ne.flag = this.flag;
		}
		return ne;
	}
	@Override
	protected String getOperator() {
		return "&";
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasAnd);
		startRule.derived(this);
		if(visited == null) { /* in the second phase */
			if(this.innerExpr.is(Peg.HasNewObject) || this.innerExpr.is(Peg.HasSetter)) {
				this.report("warning", "ignored object operation");
			}
		}
	}
	@Override
	protected PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchAnd(left, this);
	}
	public Object getPrediction() {
		return this.innerExpr.getPrediction();
	}
}

class PegNot extends PegUnary {
	PegNot(Peg e) {
		super(e, true);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegNot(this.innerExpr.clone(tr));
			ne.flag = this.flag;
		}
		return ne;
	}
	@Override
	protected String getOperator() {
		return "!";
	}
	@Override
	protected PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchNot(left, this);
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasNot);
		startRule.derived(this);
		if(visited == null) { /* in the second phase */
			if(this.innerExpr.hasObjectOperation()) {
				this.report("warning", "ignored object operation");
			}
		}
	}
}

abstract class PegList extends Peg {
	protected UList<Peg> list;
	PegList() {
		super();
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

	@Override protected void stringfy(UStringBuilder sb, boolean debugMode) {
		for(int i = 0; i < this.size(); i++) {
			if(i > 0) {
				sb.append(" ");
			}
			Peg e = this.get(i);
			if(e instanceof PegChoice || e instanceof PegSequence) {
				sb.append("(");
				e.stringfy(sb, debugMode);
				sb.append(")");
			}
			else {
				e.stringfy(sb, debugMode);
			}
		}
	}
	@Override
	protected boolean makeList(String startRule, PegRuleSet parser, UList<String> list, UMap<String> set) {
		boolean cyclic = false;
		for(int i = 0; i < this.size(); i++) {
			if(this.get(i).makeList(startRule, parser, list, set)) {
				cyclic = true;
			}
		}
		return cyclic;
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		this.ruleName = visitingName;
		for(int i = 0; i < this.size(); i++) {
			Peg e  = this.get(i);
			e.verify2(startRule, rules, visitingName, visited);
			this.derived(e);
		}
	}
}

class PegSequence extends PegList {
	PegSequence() {
		super();
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			PegList l = new PegSequence();
			for(int i = 0; i < this.list.size(); i++) {
				l.list.add(this.get(i).clone(tr));
			}
			l.flag = this.flag;
			return l;
		}
		return ne;
	}
//	public Peg cdr() {
//		PegSequence seq = new PegSequence(); 
//		for(int i = 1; i < this.size(); i++) {
//			Peg e  = this.list.ArrayValues[i];
//			seq.list.add(e);
//		}
//		return seq;
//	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		startRule.derived(this);
	}
	public Object getPrediction() {
		return this.get(0).getPrediction();
	}
	@Override
	protected PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchSequence(left, this);
	}

}

class PegChoice extends PegList {
	PegChoice() {
		super();
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			PegList l = new PegChoice();
			for(int i = 0; i < this.list.size(); i++) {
				l.list.add(this.get(i).clone(tr));
			}
			l.flag = this.flag;
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
	@Override protected void stringfy(UStringBuilder sb, boolean debugMode) {
		for(int i = 0; i < this.size(); i++) {
			if(i > 0) {
				sb.append(" / ");
			}
			Peg e = this.list.ArrayValues[i];
			e.stringfy(sb, debugMode);
		}
	}
	
	@Override
	protected PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchChoice(left, this);
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasChoice);
		startRule.derived(this);
		
	}
}

class PegSetter extends PegUnary {
	public int index;
	public PegSetter(Peg e, int index) {
		super(e, false);
		this.innerExpr = e;
		this.index = index;
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegSetter(this.innerExpr.clone(tr), this.index);
			ne.flag = this.flag;
		}
		return ne;
	}
	@Override
	protected String getOperator() {
		if(this.index != -1) {
			return "^" + this.index;
		}
		return "^";
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchSetter(left, this);
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasSetter);
		startRule.derived(this);
		if(visited == null) { /* in the second phase */
			if(!this.innerExpr.is(Peg.HasNewObject)) {
				this.report("warning", "no object is generated");
			}
		}
	}
}

class PegTagging extends PegAtom {
	public PegTagging(String tag) {
		super(tag);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegTagging(this.symbol);
			ne.flag = this.flag;
		}
		return ne;
	}
	@Override
	protected final void stringfy(UStringBuilder sb, boolean debugMode) {
		if(debugMode) {
			sb.append(this.symbol);
		}
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchTag(left, this);
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		//rules.addObjectLabel(this.symbol);
		this.set(Peg.HasTagging);
		startRule.derived(this);
	}
}

class PegMessage extends PegAtom {
	public PegMessage(String message) {
		super(message);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegMessage(this.symbol);
			ne.flag = this.flag;
		}
		return ne;
	}
	@Override
	protected final void stringfy(UStringBuilder sb, boolean debugMode) {
		if(debugMode) {
			sb.append("`");			
			sb.append(this.symbol);
			sb.append("`");			
		}
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchMessage(left, this);
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasMessage);
		startRule.derived(this);
	}
}

class PegPipe extends PegLabel {
	public PegPipe(String ruleName) {
		super(ruleName);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegPipe(this.symbol);
			ne.flag = this.flag;
		}
		return ne;
	}
	@Override
	protected final void stringfy(UStringBuilder sb, boolean debugMode) {
		if(debugMode) {
			sb.append("|> ");
			sb.append(this.symbol);
		}
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasPipe);
		startRule.derived(this);
	}

	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchPipe(left, this);
	}
}

class PegNewObject extends PegList {
	boolean leftJoin = false;
	String nodeName = "noname";
	int predictionIndex = 0;
	public PegNewObject(boolean leftJoin) {
		super();
		this.leftJoin = leftJoin;
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			PegList l = new PegNewObject(this.leftJoin);
			for(int i = 0; i < this.list.size(); i++) {
				l.list.add(this.get(i).clone(tr));
			}
			l.flag = this.flag;
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

	@Override
	protected final void stringfy(UStringBuilder sb, boolean debugMode) {
		if(debugMode) {
			if(this.leftJoin) {
				sb.append("<{^ ");
			}
			else {
				sb.append("<{ ");
			}
		}
		else {
			sb.append("( ");
		}
		super.stringfy(sb, debugMode);
		if(debugMode) {
			sb.append(" }>");
		}
		else {
			sb.append(" )");
		}
	}
	@Override
	public PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchNewObject(left, this);
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasNewObject);
		startRule.derived(this);
	}
	public Object getPrediction() {
		return this.get(0).getPrediction();
	}

}

class PegIndent extends PegAtom {
	PegIndent() {
		super("indent");
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg ne = tr.transform(this);
		if(ne == null) {
			ne = new PegIndent();
			ne.flag = this.flag;
		}
		return ne;
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasContext);
		startRule.derived(this);
	}
	@Override
	protected PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchIndent(left, this);
	}
}

// (e / catch e)

class PegCatch extends PegUnary {
	PegCatch (String ruleName, Peg first) {
		super(first, true);
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		Peg e = this.innerExpr.clone(tr);
		if(e != this) {
			return new PegCatch(this.ruleName, e);
		}
		return this;
	}
	@Override
	protected String getOperator() {
		return "catch ";
	}
	@Override
	protected PegObject simpleMatch(PegObject left, ParserContext context) {
		return context.matchCatch(left, this);
	}
	@Override
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		super.verify2(startRule, rules, visitingName, visited);
		this.set(Peg.HasCatch);
		startRule.derived(this);
	}

}

abstract class PegOptimized extends Peg {
	Peg orig;
	PegOptimized (Peg orig) {
		super();
		this.orig = orig;
		this.ruleName = orig.ruleName;
		this.flag = orig.flag;
	}
	@Override
	protected Peg clone(PegTransformer tr) {
		return this;
	}
	@Override
	protected void stringfy(UStringBuilder sb, boolean debugMode) {
		this.orig.stringfy(sb, debugMode);
	}
	protected void verify2(Peg startRule, PegRuleSet rules, String visitingName, UMap<String> visited) {
		this.ruleName = visitingName;
		this.orig.verify2(startRule, rules, visitingName, visited);
		this.derived(this.orig);
	}
	@Override
	protected boolean makeList(String startRule, PegRuleSet parser, UList<String> list, UMap<String> set) {
		return false;
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
