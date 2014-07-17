package org.libbun;

import org.libbun.peg4d.ParserContext;
import org.libbun.peg4d.Grammar;

public class Namespace extends SymbolTable {
	public UMap<Grammar> ruleMap;
	public UList<String>   exportSymbolList;
	public BunDriver  driver;
	public BunTypeChecker checker;

	public Namespace(BunDriver driver) {
		super(null);
		this.root = this;
		this.ruleMap = new UMap<Grammar>();
		Grammar pegRule = new Grammar();
		pegRule.loadPegGrammar();
		this.ruleMap.put("peg", pegRule);
//		this.ruleMap.put("main", ruleSet);
		this.driver = driver;
		this.checker = new BunTypeChecker();
		this.addFunctor(Functor.ErrorFunctor);
	}
	
	public final String toString() {
		return "root";
	}

	public void importFrom(Namespace ns) {
		for(int i = 0; i < ns.exportSymbolList.size(); i++) {
			String symbol = ns.exportSymbolList.ArrayValues[i];
			this.setSymbol(symbol, ns.getSymbol(symbol));
		}
	}
	

	public final Grammar loadPegFile(String ruleNs, String fileName) {
		Grammar rules = this.ruleMap.get(fileName);
		if(rules == null) {
			rules = new Grammar();
			rules.loadPegFile(fileName);
			this.ruleMap.get(fileName);
		}
		if(ruleNs != null) {
			this.ruleMap.put(ruleNs, rules);
		}
		return rules;
	}
	
	public final Grammar getRuleSet(String ruleNs) {
		Grammar p = this.ruleMap.get(ruleNs);
		if(p == null) {
			p = new Grammar();
			p.loadPegFile("lib/peg/" + ruleNs + ".peg");
			this.ruleMap.put(ruleNs, p);
		}
		return p;
	}

	public void initParserRuleSet(ParserContext context, String lang) {
		if(lang == null) {
			lang = this.guessLang(context.source.fileName, "bun");
		}
		Grammar ruleSet = this.getRuleSet(lang);
		context.setRuleSet(ruleSet);
	}

	private String guessLang(String fileName, String defaultValue) {
		if(fileName != null) {
			int loc = fileName.lastIndexOf(".");
			if(loc > 0) {
				return fileName.substring(loc+1);
			}
		}
		return defaultValue;
	}




}
