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
		PegOptimizer optimizer = new PegOptimizer(this.ruleSet, this.pegCache);
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
		
	protected final static int FifoSize = 64;

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

