package org.libbun.peg4d;

import org.libbun.BunType;
import org.libbun.Functor;
import org.libbun.Main;
import org.libbun.SourceBuilder;
import org.libbun.SymbolTable;
import org.libbun.UCharset;

public class PegObject {
	Peg                    createdPeg = null;
	public ParserSource    source = null;
	public long            startIndex = 0;
	public int             length = 0;
	public String          tag = null;
	public String          optionalToken = null;
	public PegObject       parent = null;
	public PegObject       AST[] = null;
	public SymbolTable     gamma = null;
	public Functor         matched = null;
	public BunType         typed   = null;
	//boolean                memoizationMode = false;

	public PegObject(String tag) {
		this.tag = tag;
	}

	public PegObject(String tag, ParserSource source, Peg createdPeg, long startIndex) {
		this.tag        = tag;
		this.source     = source;
		this.createdPeg = createdPeg;
		this.startIndex = startIndex;
		this.length     = 0;
	}

	public final boolean isFailure() {
		return (this.tag == null);
	}

	public final boolean is(String functor) {
		return this.tag.equals(functor);
	}

	public final boolean equals(PegObject o) {
		if(this != o) {
			if(this.startIndex == o.startIndex && this.length == o.length) {
				if(this.tag == null) {
					if(o.tag == null) {
						return true;
					}
				}
				else {
					if(o.tag != null && this.tag.equals(o.tag)) {
						return true;
					}
				}
			}
			if(Main.VerbosePeg) {
				System.out.println("@@diff: " + this.startIndex + "+" + this.length + this.tag + "  " + o.startIndex + "+" + o.length + o.tag);
			}
			return false;
		}
		return true;
	}
	
	
	public final void setSource(Peg createdPeg, ParserSource source, long startIndex) {
		this.createdPeg = createdPeg;
		this.source     = source;
		this.startIndex = startIndex;
		this.length     = 0;
	}

	public final void setSource(long startIndex, long endIndex) {
		this.startIndex = startIndex;
		this.length     = (int)(endIndex - startIndex);
	}

	
	public final String formatSourceMessage(String type, String msg) {
		return this.source.formatErrorMessage(type, this.startIndex, msg);
	}
	
	public final boolean isEmptyToken() {
		return this.length == 0;
	}
	
	public final String getText() {
		if(this.optionalToken != null) {
			return this.optionalToken;
		}
		if(this.source != null) {
			return this.source.substring(this.startIndex, this.startIndex + this.length);
		}
		return "";
	}

	// AST[]
	
	public final int size() {
		if(this.AST == null) {
			return 0;
		}
		return this.AST.length;
	}

	public final PegObject get(int index) {
		return this.AST[index];
	}

	public final PegObject get(int index, PegObject defaultValue) {
		if(index < this.size()) {
			return this.AST[index];
		}
		return defaultValue;
	}

	public final void set(int index, PegObject node) {
		if(!(index < this.size())){
			this.expandAstToSize(index+1);
		}
		this.AST[index] = node;
		node.parent = this;
	}
	
	public final void resizeAst(int size) {
		if(this.AST == null && size > 0) {
			this.AST = Main._NewPegObjectArray(size);
		}
		else if(this.AST.length != size) {
			PegObject[] newast = Main._NewPegObjectArray(size);
			if(size > this.AST.length) {
				Main._ArrayCopy(this.AST, 0, newast, 0, this.AST.length);
			}
			else {
				Main._ArrayCopy(this.AST, 0, newast, 0, size);
			}
			this.AST = newast;
		}
	}

	public final void expandAstToSize(int newSize) {
		if(newSize > this.size()) {
			this.resizeAst(newSize);
		}
	}

	public final void append(PegObject childNode) {
		int size = this.size();
		this.expandAstToSize(size+1);
		this.AST[size] = childNode;
		childNode.parent = this;
	}

	public final void insert(int index, PegObject childNode) {
		int oldsize = this.size();
		if(index < oldsize) {
			PegObject[] newast = Main._NewPegObjectArray(oldsize+1);
			if(index > 0) {
				Main._ArrayCopy(this.AST, 0, newast, 0, index);
			}
			newast[index] = childNode;
			childNode.parent = this;
			if(oldsize > index) {
				Main._ArrayCopy(this.AST, index, newast, index+1, oldsize - index);
			}
			this.AST = newast;
		}
		else {
			this.append(childNode);
		}
	}

	public final void removeAt(int index) {
		int oldsize = this.size();
		if(oldsize > 1) {
			PegObject[] newast = Main._NewPegObjectArray(oldsize-1);
			if(index > 0) {
				Main._ArrayCopy(this.AST, 0, newast, 0, index);
			}
			if(oldsize - index > 1) {
				Main._ArrayCopy(this.AST, index+1, newast, index, oldsize - index - 1);
			}
			this.AST = newast;
		}
		else {
			this.AST = null;
		}
	}
	
	public void replace(PegObject oldone, PegObject newone) {
		for(int i = 0; i < this.size(); i++) {
			if(this.AST[i] == oldone) {
				this.AST[i] = newone;
				newone.parent = this;
			}
		}
	}
	
	public final int count() {
		int count = 1;
		for(int i = 0; i < this.size(); i++) {
			count = count + this.get(i).count();
		}
		return count;
	}

	public final void checkNullEntry() {
		for(int i = 0; i < this.size(); i++) {
			if(this.AST[i] == null) {
				this.AST[i] = new PegObject("#empty", this.source, null, this.startIndex);
				this.AST[i].parent = this;
			}
		}
	}

	public final String textAt(int index, String defaultValue) {
		if(index < this.size()) {
			return this.AST[index].getText();
		}
		return defaultValue;
	}
	
	public BunType typeAt(SymbolTable gamma, int index, BunType defaultType) {
		if(index < this.size()) {
			PegObject node = this.AST[index];
			if(node.typed != null) {
				return node.typed;
			}
			if(node.matched == null && gamma != null) {
				node = gamma.tryMatch(node, true);
			}
			if(node.matched != null) {
				return node.matched.getReturnType(defaultType);
			}
		}
		return defaultType;
	}

	@Override
	public String toString() {
		SourceBuilder sb = new SourceBuilder(null);
		this.stringfy(sb);
		return sb.toString();
	}

	final void stringfy(SourceBuilder sb) {
		if(this.isFailure()) {
			sb.append(this.formatSourceMessage("syntax error", this.info()));
		}
		else if(this.AST == null) {
			sb.appendNewLine("{"+ this.tag+ " ", UCharset._QuoteString('\'', this.getText(), '\''), "}");
		}
		else {
			sb.appendNewLine("");
			sb.openIndent("{" + this.tag);
			for(int i = 0; i < this.size(); i++) {
				this.AST[i].stringfy(sb);
			}
			sb.closeIndent("}");
		}
	}

	private String info() {
		if(this.matched == null) {
			if(this.source != null && Main.VerbosePeg) {
				return "         ## by peg : " + this.createdPeg;
			}
			return "";
		}
		else {
			return "      :: " + this.getType(null) + " by " + this.matched;
		}
	}

	public final PegObject findParentNode(String name) {
		PegObject node = this;
		while(node != null) {
			if(node.is(name)) {
				return node;
			}
			node = node.parent;
		}
		return null;
	}

	public final SymbolTable getSymbolTable() {
		PegObject node = this;
		while(node.gamma == null) {
			node = node.parent;
		}
		return node.gamma;
	}

	public final SymbolTable getLocalSymbolTable() {
		if(this.gamma == null) {
			SymbolTable gamma = this.getSymbolTable();
			gamma = new SymbolTable(gamma.getNamespace(), this);
			// NOTE: this.gamma was set in SymbolTable constructor
			assert(this.gamma != null);
		}
		return this.gamma;
	}
	
	public final BunType getType(BunType defaultType) {
		if(this.typed == null) {
			if(this.matched != null) {
				this.typed = this.matched.getReturnType(defaultType);
			}
			if(this.typed == null) {
				return defaultType;
			}
		}
		return this.typed;
	}
	
	public boolean isVariable() {
		// TODO Auto-generated method stub
		return true;
	}

	public void setVariable(boolean flag) {
	}

	public final int countUnmatched(int c) {
		for(int i = 0; i < this.size(); i++) {
			PegObject o = this.get(i);
			c = o.countUnmatched(c);
		}
		if(this.matched == null) {
			return c+1;
		}
		return c;
	}

	public final PegObject getParent() {
		return this.parent;
	}

	public final boolean isUntyped() {
		return this.typed == null || this.typed == BunType.UntypedType;
	}
}

