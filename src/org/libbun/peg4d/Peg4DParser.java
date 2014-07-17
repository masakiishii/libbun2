package org.libbun.peg4d;

import java.util.LinkedHashMap;
import java.util.Map;

import org.libbun.Main;
import org.libbun.UList;
import org.libbun.peg4d.RecursiveDecentParser.ObjectMemo;

public class Peg4DParser extends RecursiveDecentParser {

	public Peg4DParser(ParserSource source) {
		super(source);
	}
	
	private Peg4DParser(ParserSource source, long startIndex, long endIndex, UList<Peg> pegList) {
		super(source, startIndex, endIndex, pegList);
	}

	public ParserContext newParserContext(ParserSource source, long startIndex, long endIndex) {
		return new Peg4DParser(source, startIndex, endIndex, this.pegList);
	}

	public void initMemo() {
		this.memoMap = new LinkedHashMap<Long, ObjectMemo>(FifoSize) {  //FIFO
			private static final long serialVersionUID = 6725894996600788028L;
			@Override
	        protected boolean removeEldestEntry(Map.Entry<Long, ObjectMemo> eldest)  {
				if(this.size() > FifoSize) {
					//System.out.println("removed pos="+eldest.getKey());
					unusedMemo(eldest.getValue());
					return true;			
				}
	            return false;
	        }
	    };
	}
	
	public PegObject matchNewObject(PegObject left, PegNewObject e) {
		long pos = this.getPosition();
		boolean isRepeated = e.isRepeatedCall(pos);
		if(Main.VerboseStatCall) {
			e.countCall(this, "object", pos);
		}
		ObjectMemo m = this.getMemo(e, pos, isRepeated);
		if(m != null) {
			if(m.generated == null) {
				return this.refoundFailure(e, pos+m.consumed);
			}
			setPosition(pos + m.consumed);
			return m.generated;
		}
		PegObject generated = super.matchNewObject(left, e);
		if(generated.isFailure()) {
			this.setMemo(pos, e, null, (int)(generated.startIndex - pos));
		}
		else {
			this.setMemo(pos, e, generated, (int)(this.getPosition() - pos));
		}
		return generated;
	}


}
