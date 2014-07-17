package org.libbun.peg4d;

import java.util.HashMap;

import org.libbun.Main;
import org.libbun.UCharset;
import org.libbun.UList;
import org.libbun.UMap;
import org.libbun.peg4d.RecursiveDecentParser.ObjectMemo;

public class ValidParserContext extends RecursiveDecentParser {
	public ValidParserContext(ParserSource source) {
		super(source);
	}
	
	private ValidParserContext(ParserSource source, long startIndex, long endIndex, UList<Peg> pegList) {
		super(source, startIndex, endIndex, pegList);
	}

	public ParserContext newParserContext(ParserSource source, long startIndex, long endIndex) {
		return new ValidParserContext(source, startIndex, endIndex, this.pegList);
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
	
	public PegObject matchNonTerminal(PegObject left, PegNonTerminal e) {
		long pos = this.getPosition();
		ObjectMemo m = this.getMemo(e, pos);
		if(m != null) {
			if(m.generated == null) {
				return this.refoundFailure(e, pos+m.consumed);
			}
			setPosition(pos + m.consumed);
			return m.generated;
		}
		PegObject generated = super.matchNonTerminal(left, e);
		if(generated.isFailure()) {
			this.setMemo(pos, e, null, (int)(generated.startIndex - pos));
		}
		else {
			this.setMemo(pos, e, generated, (int)(this.getPosition() - pos));
		}
		return generated;
	}

	
	public PegObject matchNewObject(PegObject left, PegNewObject e) {
		PegObject leftNode = left;
		long startIndex = this.getPosition();
		int markerId = this.pushNewMarker();
		PegObject newnode = this.newPegObject(e.nodeName);
		newnode.setSource(e, this.source, startIndex);
		if(e.leftJoin) {
			this.pushSetter(newnode, -1, leftNode);
		}
		for(int i = 0; i < e.size(); i++) {
			PegObject node = e.get(i).performMatch(newnode, this);
			if(node.isFailure()) {
				if(Main.ValidateJsonMode) {
					String inValidName = node.createdPeg.toString();
					this.setInvalidLine((int) sourcePosition, source, inValidName);
				}
				this.popBack(markerId);
				this.rollback(startIndex);
				return node;
			}
			//			if(node != newnode) {
			//				e.warning("dropping @" + newnode.name + " " + node);
			//			}
		}
		this.popNewObject(newnode, startIndex, markerId);
		return newnode;
	}
	
	public static String InvalidLine = "";
	
	public void setInvalidLine(int pos, ParserSource source, String inValidName) {
		InvalidLine = "\nINVALID" + "\nnot found : " + inValidName
					+ "(error " + source.fileName + " line" + source.getLineNumber(pos) + ")";
		Main.ValidateJsonMode = false;
	}

}

