package org.libbun.peg4d;

import org.libbun.Main;
import org.libbun.UCharset;
import org.libbun.UList;
import org.libbun.UMap;

public final class Grammar {
	UMap<Peg>           pegMap;
	UMap<String>        objectLabelMap = null;
	boolean             lrExistence = false;
	public boolean      foundError = false;
	
	public Grammar() {
		this.pegMap = new UMap<Peg>();
		this.pegMap.put("indent", new PegIndent());  // default rule
	}

	public final boolean hasRule(String ruleName) {
		return this.pegMap.get(ruleName, null) != null;
	}

	public final Peg getRule(String ruleName) {
		return this.pegMap.get(ruleName, null);
	}
	
	public final void setRule(String ruleName, Peg e) {
		Peg checked = this.checkPegRule(ruleName, e);
		if(checked != null) {
			this.pegMap.put(ruleName, checked);
		}
	}

	private Peg checkPegRule(String name, Peg e) {
		if(e instanceof PegChoice) {
			PegChoice newnode = new PegChoice();
			for(int i = 0; i < e.size(); i++) {
				newnode.add(this.checkPegRule(name, e.get(i)));
			}
//			if(Main.FastMatchMode) {
//				this.checkMemoMode(newnode, newnode, 0);
//			}
			if(newnode.size() == 1) {
				return newnode.get(0);
			}
			return newnode;
		}
		if(e instanceof PegNonTerminal) {  // self reference
			if(name.equals(((PegNonTerminal) e).symbol)) {
				Peg defined = this.pegMap.get(name, null);
				if(defined == null) {
					e.warning("undefined self reference: " + name);
				}
//				System.out.println("name " + name + ", " + ((PegLabel) e).symbol + " " + defined);
				return defined;
			}
		}
		return e;
	}
		
	public final void check() {
		this.objectLabelMap = new UMap<String>();
		this.foundError = false;
		UList<String> list = this.pegMap.keys();
		UMap<String> visited = new UMap<String>();
		for(int i = 0; i < list.size(); i++) {
			String ruleName = list.ArrayValues[i];
			Peg e = this.pegMap.get(ruleName, null);
			e.ruleName = ruleName;
			e.verify2(e, this, ruleName, visited);
			visited.clear();
			if(Main.VerbosePeg) {
				if(e.is(Peg.HasNewObject)) {
					ruleName = "object " + ruleName; 
				}
				if(!e.is(Peg.HasNewObject) && !e.is(Peg.HasSetter)) {
					ruleName = "text " + ruleName; 
				}
				if(e.is(Peg.CyclicRule)) {
					ruleName += "*"; 
				}
				System.out.println(e.toPrintableString(ruleName, "\n  = ", "\n  / ", "\n  ;", true));
			}
		}
		/* to complete the verification of cyclic rules */
		PegNormaizer norm = new PegNormaizer();
		for(int i = 0; i < list.size(); i++) {
			String ruleName = list.ArrayValues[i];
			Peg e = this.pegMap.get(ruleName, null);
			e.verify2(e, this, e.ruleName, null);
			norm.setRuleName(ruleName);
			Peg ne = e.clone(norm);
			if(ne != e) {
				this.pegMap.put(ruleName, ne);
			}
		}
		if(this.foundError) {
			Main._Exit(1, "peg error found");
		}
	}
	
	class PegNormaizer extends PegTransformer {
		private String ruleName;
		void setRuleName(String ruleName) {
			this.ruleName = ruleName;
		}
		@Override
		public Peg transform(Peg e) {
			e.ruleName = ruleName;
			if(e instanceof PegChoice) {
				return this.flattenChoice((PegChoice)e);
			}
			if(e instanceof PegList) {
				for(int i = 0; i < e.size(); i++) {
					((PegList) e).list.ArrayValues[i] = e.get(i).clone(this);
				}
				return e;
			}
			if(e instanceof PegSetter) {
				return this.flattenSetter((PegSetter)e);
			}
			if(e instanceof PegUnary) {
				((PegUnary) e).innerExpr = ((PegUnary) e).innerExpr.clone(this);
			}
			return e;
		}

		private Peg flattenChoice(PegChoice e) {
			boolean hasChoice = false;
			for(int i = 0; i < e.size(); i++) {
				if(e.get(i) instanceof PegChoice) {
					hasChoice = true;
					break;
				}
			}
			if(hasChoice) {
				UList<Peg> l = new UList<Peg>(new Peg[e.size()*2]);
				flattenChoiceImpl(e, l);
				e.list = l;
			}
			return e;
		}

		private void flattenChoiceImpl(PegChoice e, UList<Peg> l) {
			for(int i = 0; i < e.size(); i++) {
				Peg sub = e.get(i);
				if(sub instanceof PegChoice) {
					this.flattenChoiceImpl((PegChoice)sub, l);
				}
				else {
					l.add(sub);
				}
			}
		}

		private Peg flattenSetter(PegSetter e) {
			if(!e.innerExpr.is(Peg.HasNewObject)) {
				return e.innerExpr;
			}
			return e;
		}

		
	}
	
//	final void checkCyclicRule(String ruleName, Peg e) {
//		UList<String> list = new UList<String>(new String[100]);
//		UMap<String> set = new UMap<String>();
//		list.add(ruleName);
//		set.put(ruleName, ruleName);
//		if(e.makeList(ruleName, this, list, set)) {
//			e.set(Peg.CyclicRule);
//		}
//	}

	public void addObjectLabel(String objectLabel) {
		this.objectLabelMap.put(objectLabel, objectLabel);
	}

	public final boolean loadPegFile(String fileName) {
		ParserContext p = Main.newParserContext(Main.loadSource(fileName));
		p.setRuleSet(PegGrammar);
		while(p.hasNode()) {
			p.initMemo();
			PegObject node = p.parseNode("TopLevel");
			if(node.isFailure()) {
				Main._Exit(1, "FAILED: " + node);
				break;
			}
			if(!this.tramsform(p, node)) {
				break;
			}
		}
		this.check();
		return this.foundError;
	}
	
	private boolean tramsform(ParserContext context, PegObject node) {
		//System.out.println("DEBUG? parsed: " + node);		
		if(node.is("#rule")) {
			String ruleName = node.textAt(0, "");
			Peg e = toPeg(node.get(1));
			this.setRule(ruleName, e);
			//System.out.println("#rule** " + node + "\n@@@@ => " + e);
			return true;
		}
		if(node.is("#import")) {
			String ruleName = node.textAt(0, "");
			String fileName = context.source.checkFileName(node.textAt(1, ""));
			this.importRuleFromFile(ruleName, fileName);
			return true;
		}
		if(node.is("#error")) {
			char c = node.source.charAt(node.startIndex);
			System.out.println(node.source.formatErrorMessage("error", node.startIndex, "syntax error: ascii=" + (int)c));
			return false;
		}
		System.out.println("Unknown peg node: " + node);
		return false;
	}
	private Peg toPeg(PegObject node) {
		Peg e = this.toPegImpl(node);
		e.source = node.source;
		e.sourcePosition = (int)node.startIndex;
		return e;
	}	
	private Peg toPegImpl(PegObject node) {
		if(node.is("#PegNonTerminal")) {
			return new PegNonTerminal(node.getText());
		}
		if(node.is("#PegString")) {
			return new PegString(UCharset._UnquoteString(node.getText()));
		}
		if(node.is("#PegCharacter")) {
			return new PegCharacter(node.getText());
		}
		if(node.is("#PegAny")) {
			return new PegAny();
		}
		if(node.is("#PegChoice")) {
			PegChoice l = new PegChoice();
			for(int i = 0; i < node.size(); i++) {
				l.list.add(toPeg(node.get(i)));
			}
			return l;
		}
		if(node.is("#PegSequence")) {
			PegSequence l = new PegSequence();
			for(int i = 0; i < node.size(); i++) {
				l.list.add(toPeg(node.get(i)));
			}
			return l;
		}
		if(node.is("#PegNot")) {
			return new PegNot(toPeg(node.get(0)));
		}
		if(node.is("#PegAnd")) {
			return new PegAnd(toPeg(node.get(0)));
		}
		if(node.is("#PegOneMore")) {
			return new PegRepeat(toPeg(node.get(0)), 1);
		}
		if(node.is("#PegZeroMore")) {
			return new PegRepeat(toPeg(node.get(0)), 0);
		}
		if(node.is("#PegOptional")) {
			return new PegOptional(toPeg(node.get(0)));
		}
		if(node.is("#PegTagging")) {
			return new PegTagging(node.getText());
		}
		if(node.is("#PegMessage")) {
			return new PegMessage(node.getText());
		}
		if(node.is("##PegNewObjectJoin")) {
			Peg seq = toPeg(node.get(0));
			PegNewObject o = new PegNewObject(true);
			if(seq.size() > 0) {
				for(int i = 0; i < seq.size(); i++) {
					o.list.add(seq.get(i));
				}
			}
			else {
				o.list.add(seq);
			}
			return o;
		}
		if(node.is("#PegNewObject")) {
			Peg seq = toPeg(node.get(0));
			PegNewObject o = new PegNewObject(false);
			if(seq.size() > 0) {
				for(int i = 0; i < seq.size(); i++) {
					o.list.add(seq.get(i));
				}
			}
			else {
				o.list.add(seq);
			}
			return o;
		}
		if(node.is("#PegExport")) {
			Peg seq = toPeg(node.get(0));
			PegList o = new PegNewObject(false);
			if(seq.size() > 0) {
				for(int i = 0; i < seq.size(); i++) {
					o.list.add(seq.get(i));
				}
			}
			else {
				o.list.add(seq);
			}
			return new PegExport(o);
		}
		if(node.is("#PegSetter")) {
			int index = -1;
			String indexString = node.getText();
			if(indexString.length() > 0) {
				index = (int)UCharset._ParseInt(indexString);
			}
			return new PegSetter(toPeg(node.get(0)), index);
		}
		if(node.is("#pipe")) {
			return new PegPipe(node.getText());
		}
		if(node.is("#catch")) {
			return new PegCatch(null, toPeg(node.get(0)));
		}
		Main._Exit(1, "undefined peg: " + node);
		return null;
	}

	void importRuleFromFile(String label, String fileName) {
		if(Main.VerbosePeg) {
			System.out.println("importing " + fileName);
		}
		Grammar p = new Grammar();
		p.loadPegFile(fileName);
		UList<String> list = p.makeList(label);
		String prefix = "";
		int loc = label.indexOf(":");
		if(loc > 0) {
			prefix = label.substring(0, loc+1);
			label = label.substring(loc+1);
			this.pegMap.put(label, new PegNonTerminal(prefix+label));
		}
		for(int i = 0; i < list.size(); i++) {
			String l = list.ArrayValues[i];
			Peg e = p.getRule(l);
			this.pegMap.put(prefix + l, e.clone(new PegNoTransformer()));
		}
	}

	public final UList<String> makeList(String startPoint) {
		UList<String> list = new UList<String>(new String[100]);
		UMap<String> set = new UMap<String>();
		Peg e = this.getRule(startPoint);
		if(e != null) {
			list.add(startPoint);
			set.put(startPoint, startPoint);
			e.makeList(startPoint, this, list, set);
		}
		return list;
	}

	public final void show(String startPoint) {
		UList<String> list = makeList(startPoint);
		for(int i = 0; i < list.size(); i++) {
			String name = list.ArrayValues[i];
			Peg e = this.getRule(name);
			String rule = e.toPrintableString(name, "\n  = ", "\n  / ", "\n  ;", true);
			System.out.println(rule);
		}
	}

	// Definiton of Bun's Peg	
	private final static Peg s(String token) {
		return new PegString(token);
	}
	private final static Peg c(String charSet) {
		return new PegCharacter(charSet);
	}
	public static Peg n(String ruleName) {
		return new PegNonTerminal(ruleName);
	}
	private final static Peg opt(Peg e) {
		return new PegOptional(e);
	}
	private final static Peg zero(Peg e) {
		return new PegRepeat(e, 0);
	}
	private final static Peg zero(Peg ... elist) {
		return new PegRepeat(seq(elist), 0);
	}
	private final static Peg one(Peg e) {
		return new PegRepeat(e, 1);
	}
	private final static Peg one(Peg ... elist) {
		return new PegRepeat(seq(elist), 1);
	}
	private final static Peg seq(Peg ... elist) {
		PegSequence l = new PegSequence();
		for(Peg e : elist) {
			l.list.add(e);
		}
		return l;
	}
	private final static Peg choice(Peg ... elist) {
		PegChoice l = new PegChoice();
		for(Peg e : elist) {
			l.add(e);
		}
		return l;
	}
	public static Peg not(Peg e) {
		return new PegNot(e);
	}
	public static Peg L(String label) {
		return new PegTagging(label);
	}
	public static Peg O(Peg ... elist) {
		PegNewObject l = new PegNewObject(false);
		for(Peg e : elist) {
			l.list.add(e);
		}
		return l;
	}
	public static Peg LO(Peg ... elist) {
		PegNewObject l = new PegNewObject(true);
		for(Peg e : elist) {
			l.list.add(e);
		}
		return l;
	}
	public static Peg set(Peg e) {
		return new PegSetter(e, -1);
	}

	public Grammar loadPegGrammar() {
		Peg Any = new PegAny();
		Peg NewLine = c("\\r\\n");
//		Comment
//		  = '/*' (!'*/' .)* '*/'
//		  / '//' (![\r\n] .)* [\r\n]
//		  ;
		Peg Comment = choice(
			seq(s("/*"), zero(not(s("*/")), Any), s("*/")),
			seq(s("//"), zero(not(NewLine), Any), NewLine)	
		);
//		_ = 
//		  ([ \t\r\n]+ / Comment )* 
//		  ;
		this.setRule("_", zero(choice(one(c(" \\t\\n\\r")), Comment)));
		
//		RuleName
//		  = << [A-Za-z_] [A-Za-z0-9_]* #PegNonTerminal >>
//		  ;
		this.setRule("RuleName", O(c("A-Za-z_"), zero(c("A-Za-z0-9_")), L("#PegNonTerminal")));
////	String
////	  = "'" << (!"'" .)*  #PegString >> "'"
////	  / '"' <<  (!'"' .)* #PegString >> '"'
////	  ;
		Peg _String = choice(
			seq(s("'"), O(zero(not(s("'")), Any), L("#PegString")), s("'")),
			seq(s("\""), O(zero(not(s("\"")), Any), L("#PegString")), s("\"")),
			seq(s("`"), O(zero(not(s("`")), Any), L("#PegMessage")), s("`"))
		);	
//	Character 
//	  = "[" <<  (!']' .)* #PegCharacter >> "]"
//	  ;
		Peg _Character = seq(s("["), O(zero(not(s("]")), Any), L("#PegCharacter")), s("]"));
//	Any
//	  = << '.' #PegAny >>
//	  ;
		Peg _Any = O(s("."), L("#PegAny"));
//	ObjectLabel 
//	  = << '#' [A-z0-9_.]+ #PegTagging>>
//	  ;
		Peg _Tagging = O(s("#"), one(c("A-Za-z0-9_.")), L("#PegTagging"));
//	Index
//	  = << [0-9] #PegIndex >>
//	  ;
		Peg _Index = O(c("0-9"), L("#PegIndex"));
//		Index
//		  = << [0-9] #PegIndex >>
//		Peg _Pipe = seq(s("|>"), opt(n("_")), O(c("A-Za-z_"), zero(c("A-Za-z0-9_")), L("#pipe")));
		Peg _Export = O(s("<|"), opt(n("_")), set(n("Expr")), opt(n("_")), L("#PegExport"), s("|>"));
//	Setter
//	  = '@' <<@ [0-9]? #PegSetter>>
//	  ;
		setRule("Setter", seq(choice(s("^"), s("@")), LO(opt(c("0-9")), L("#PegSetter"))));
//		SetterTerm
//		  = '(' Expr ')' Setter?
//		  / '<<' << ('@' [ \t\n] ##PegNewObjectJoin / '' #PegNewObject) _? Expr@ >> _? '>>' Setter?
//		  / RuleName Setter?
//		  ;
		Peg _SetterTerm = choice(
			seq(s("("), opt(n("_")), n("Expr"), opt(n("_")), s(")"), opt(n("Setter"))),
			seq(O(choice(s("<<"), s("<{"), s("8<")), choice(seq(choice(s("^"), s("@")), c(" \\t\\n\\r"), L("##PegNewObjectJoin")), seq(s(""), L("#PegNewObject"))), 
					opt(n("_")), set(n("Expr")), opt(n("_")), choice(s(">>"), s("}>"), s(">8"))), opt(n("Setter"))),
			seq(n("RuleName"), opt(n("Setter")))
		);
//	Term
//	  = String 
//	  / Character
//	  / Any
//	  / ObjectLabel
//	  / Index
//	  / SetterTerm
//	  ;
		setRule("Term", choice(
			_String, _Character, _Any, _Tagging, _Index, _Export, _SetterTerm
		));
//
//	SuffixTerm
//	  = Term <<@ ('*' #PegZeroMore / '+' #PegOneMore / '?' #PegOptional) >>?
//	  ;
		this.setRule("SuffixTerm", seq(n("Term"), opt(LO(choice(seq(s("*"), L("#PegZeroMore")), seq(s("+"), L("#PegOneMore")), seq(s("?"), L("#PegOptional")))))));
//	Predicated
//	  = << ('&' #PegAnd / '!' #PegNot) SuffixTerm@ >> / SuffixTerm 
//	  ;
		this.setRule("Predicate",  choice(
			O(choice(seq(s("&"), L("#PegAnd")),seq(s("!"), L("#PegNot"))), set(n("SuffixTerm"))), 
			n("SuffixTerm")
		));
//  Catch
//    = << 'catch' Expr@ >>
//    ;
		Peg Catch = O(s("catch"), n("_"), L("#catch"), set(n("Expr")));
//	Sequence 
//	  = Predicated <<@ (_ Predicated@)+ #seq >>?
//	  ;
		setRule("Sequence", seq(n("Predicate"), opt(LO(L("#PegSequence"), one(n("_"), set(n("Predicate")))))));
//	Choice
//	  = Sequence <<@ _? ('/' _? Sequence@)+ #PegChoice >>?
//	  ;
		Peg _Choice = seq(n("Sequence"), opt(LO( L("#PegChoice"), one(opt(n("_")), s("/"), opt(n("_")), set(choice(Catch, n("Sequence")))))));
//	Expr
//	  = Choice
//	  ;
		this.setRule("Expr", _Choice);
//	Rule
//	  = << RuleName@ _? '=' _? Expr@ #rule>>
//	  ;
		this.setRule("Rule", O(L("#rule"), set(n("RuleName")), opt(n("_")), s("="), opt(n("_")), set(n("Expr"))));
//	Import
//    = << 'import' _ RuleName@ from String@ #import>>
//		  ;
		this.setRule("Import", O(s("import"), L("#import"), n("_"), set(n("RuleName")), n("_"), s("from"), n("_"), set(_String)));
//	TopLevel   
//	  =  Rule _? ';'
//	  ;
//		this.setRule("TopLevel", seq(n("Rule"), opt(n("_")), s(";"), opt(n("_"))));
		this.setRule("TopLevel", seq(
			opt(n("_")), choice(n("Rule"), n("Import")), opt(n("_")), s(";"), opt(n("_"))
		));
		this.check();
		this.show("TopLevel");
		return this;
	}
	
	public final static Grammar PegGrammar = new Grammar().loadPegGrammar();

}


