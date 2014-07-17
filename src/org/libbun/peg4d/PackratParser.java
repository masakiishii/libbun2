package org.libbun.peg4d;

import java.util.HashMap;
import java.util.LinkedHashMap;

import org.libbun.Main;
import org.libbun.UList;

public class PackratParser extends RecursiveDecentParser {

	public PackratParser(ParserSource source) {
		super(source);
	}
	
	private PackratParser(ParserSource source, long startIndex, long endIndex, UList<Peg> pegList) {
		super(source, startIndex, endIndex, pegList);
	}

	public ParserContext newParserContext(ParserSource source, long startIndex, long endIndex) {
		return new PackratParser(source, startIndex, endIndex, this.pegList);
	}

	public void initMemo() {
//		this.memoMap = new LinkedHashMap<Long, ObjectMemo>(FifoSize) {  //FIFO
//			private static final long serialVersionUID = 6725894996600788028L;
//			@Override
//	        protected boolean removeEldestEntry(Map.Entry<Long, ObjectMemo> eldest)  {
//				if(this.size() > FifoSize) {
//					//System.out.println("removed pos="+eldest.getKey());
//					unusedMemo(eldest.getValue());
//					return true;			
//				}
//	            return false;
//	        }
//	    };
		this.memoMap = new HashMap<Long, ObjectMemo>();
	}
	
	public PegObject matchNonTerminal(PegObject left, PegNonTerminal label) {
		if(Main.OptimizedLevel == 0) {
			return this.matchNonTerminal0(left, label);  // it has bugs
		}
		else {
			return this.matchNonTerminal1(left, label);
		}
	}
	
	public PegObject matchNonTerminal0(PegObject left, PegNonTerminal label) {
		Peg next = this.getRule(label.symbol);
		long pos = this.getPosition();
		ObjectMemo m = this.getMemo(label, pos);
		if(m != null) {
			if(m.generated == null) {
				return this.refoundFailure(label, pos+m.consumed);
			}
			setPosition(pos + m.consumed);
			return m.generated;
		}
		if(Main.VerboseStatCall) {
			next.countCall(this, label.symbol, pos);
		}
		PegObject generated = next.performMatch(left, this);
		if(generated.isFailure()) {
			this.setMemo(pos, label, null, (int)(generated.startIndex - pos));
		}
		else {
			this.setMemo(pos, label, generated, (int)(this.getPosition() - pos));
		}
		return generated;
	}

	public PegObject matchNonTerminal1(PegObject left, PegNonTerminal label) {
		Peg next = this.getRule(label.symbol);
		long pos = this.getPosition();
		ObjectMemo m = this.getMemo(next, pos);
		if(m != null) {
			if(m.generated == null) {
				return this.refoundFailure(label, pos+m.consumed);
			}
			setPosition(pos + m.consumed);
			return m.generated;
		}
		if(Main.VerboseStatCall) {
			next.countCall(this, label.symbol, pos);
		}
		PegObject generated = next.performMatch(left, this);
		if(generated.isFailure()) {
			this.setMemo(pos, next, null, (int)(generated.startIndex - pos));
		}
		else {
			this.setMemo(pos, next, generated, (int)(this.getPosition() - pos));
		}
		return generated;
	}

}
