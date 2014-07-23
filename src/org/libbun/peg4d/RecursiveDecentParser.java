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
	
	public void initMemo() {
		this.memoMap = new NoMemo();
	}
	
	public ParserContext newParserContext(ParserSource source, long startIndex, long endIndex) {
		return new RecursiveDecentParser(source, startIndex, endIndex, this.pegList);
	}
	
	@Override
	public void setRuleSet(Grammar ruleSet) {
		this.rules = ruleSet;
		this.pegCache = new UMap<Peg>();
		this.pegList = new UList<Peg>(new Peg[this.rules.pegMap.size()]);
		UList<String> list = ruleSet.pegMap.keys();
		PegOptimizer optimizer = new PegOptimizer(this.rules, this.pegCache);
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
		this.statOptimizedPeg = optimizer.statOptimizedPeg;
	}

	public final Peg getRule(String name) {
		return this.pegCache.get(name, null);
	}

}

