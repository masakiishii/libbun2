package org.libbun.peg4d;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.libbun.Main;
import org.libbun.UList;
import org.libbun.peg4d.Memo.ObjectMemo;

public class PEG4dParser extends RecursiveDecentParser {

	public PEG4dParser(ParserSource source) {
		super(source);
	}
	
	private PEG4dParser(ParserSource source, long startIndex, long endIndex, UList<Peg> pegList) {
		super(source, startIndex, endIndex, pegList);
	}

	public ParserContext newParserContext(ParserSource source, long startIndex, long endIndex) {
		return new PEG4dParser(source, startIndex, endIndex, this.pegList);
	}

	public void initMemo() {
		if(Main.MemoFactor == -1) {  /* default */
			this.memoMap = new FastFifoMemo(256);
		}
		else if(Main.MemoFactor == 0) {
			this.memoMap = new NoMemo(); //new PackratMemo(this.source.length());
		}
		else {
			this.memoMap = new FastFifoMemo(Main.MemoFactor);
		}
	}
	
	public PegObject matchNewObject(PegObject left, PegNewObject e) {
		long pos = this.getPosition();
		ObjectMemo m = this.memoMap.getMemo(e, pos);
		if(m != null) {
			if(m.generated == null) {
				return this.refoundFailure(e, pos+m.consumed);
			}
			setPosition(pos + m.consumed);
			return m.generated;
		}
		PegObject generated = super.matchNewObject(left, e);
		if(generated.isFailure()) {
			this.memoMap.setMemo(pos, e, null, (int)(generated.startIndex - pos));
		}
		else {
			this.memoMap.setMemo(pos, e, generated, (int)(this.getPosition() - pos));
		}
		return generated;
	}


}
