package org.libbun;

import org.libbun.peg4d.PegObject;

public class BunTypeChecker {
	// SymbolTable
	private class NameFunctor extends Functor {
		//PegObject defNode = null;
		public NameFunctor(int flag, String name, BunType type, PegObject defNode) {
			super(flag, name, type);
			//this.defNode = defNode;
		}
		@Override
		public void build(PegObject node, BunDriver driver) {
			driver.pushName(node, this.name);
		}
	}
	private NameFunctor getName(SymbolTable gamma, String name) {
		Functor f = gamma.getSymbol(name);
		if(f instanceof NameFunctor) {
			return (NameFunctor)f;
		}
		return null;
	}
	private Functor setName(SymbolTable gamma, int flag, PegObject nameNode, BunType type, PegObject defNode) {
		String name = nameNode.getText();
		Functor defined = gamma.getLocalSymbol(name);
		if(defined != null) {
			gamma.report(nameNode, "notice", "duplicated name: " + name + " @" + gamma);
		}
		type = type.getRealType();
		if(nameNode.matched == null) {
			nameNode.matched = gamma.getSymbol("#name:0");
		}
		if(nameNode.typed == null) {
			nameNode.typed = type;
		}
		NameFunctor f = new NameFunctor(flag, name, type, defNode);
		gamma.setSymbol(name, f);
		return f;
	}
	private Functor setName(SymbolTable gamma, int flag, String name, BunType type) {
		NameFunctor f = new NameFunctor(flag, name, type.getRealType(), null);
		gamma.setSymbol(name, f);
		return f;
	}
	private class FunctionFunctor extends NameFunctor {
		public FunctionFunctor(int flag, String name, BunType type, PegObject funcNode, Functor defined) {
			super(flag, name, type, funcNode);
			this.nextChoice = defined;
		}
		@Override 
		public void build(PegObject node, BunDriver driver) {
			driver.pushApplyNode(name, node);
		}
	}
	private boolean checkReturnStatement(PegObject block) {
		for(int i = 0; i < block.size(); i++) {
			PegObject stmt = block.get(i);
			if(stmt.is("#return")) {
				return true;
			}
			if(stmt.is("#if") && stmt.size() == 3) {
				if(checkReturnStatement(stmt.get(1)) && checkReturnStatement(stmt.get(2))) {
					return true;
				}
			}
		}
		if(block.is("#return")) {
			return true;
		}
		return false;
	}
	
	private Functor setFunctionName(SymbolTable gamma, int flag, PegObject nameNode, BunType type, PegObject node) {
		String name = nameNode.getText();
		String key = name + ":" + type.getFuncParamSize();
		Functor defined = gamma.getSymbol(key);
		type = type.getRealType();
		name = gamma.root.driver.rename(flag, name);
		nameNode.optionalToken = name;
		defined = new FunctionFunctor(flag, name, type.getRealType(), node, defined);
		gamma.setSymbol(key, defined);
		return defined;
	}
	
	private int checkNameFlag(PegObject node, int flag) {
		if(node.findParentNode("#function") != null) {
			flag |= Functor._LocalFunctor;
		}
		if(node.findParentNode("#public") != null) {
			flag |= Functor._PublicFunctor;
		}
		if(node.findParentNode("#export") != null) {
			flag |= Functor._ExportFunctor;
		}
		return flag;
	}

	/***********************************************************************/
	
	abstract class TypeChecker {
		public abstract PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping);
	}
	class TvarChecker extends TypeChecker {
		@Override
		public PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping) {
			node.typed = BunType.newVarType();
			return node;
		}
	}
	class VarChecker extends TypeChecker {
		@Override
		public PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping) {
			int flag = Functor._SymbolFunctor;
			BunType type = node.typeAt(gamma, 1, BunType.UntypedType);
			gamma.checkTypeAt(node, 2, type, true);
			if(node.findParentNode("#function") == null) {
			}
			else {
				PegObject block = node.findParentNode("#block");
				gamma = block.getLocalSymbolTable();
				flag |= Functor._LocalFunctor;
			}
			PegObject nameNode = node.get(0);
			setName(gamma, flag, nameNode, type, node);
//			nameNode.matched = gamma.getSymbol("#name:0");
//			nameNode.typed = type;
			return node;
		}
	}
	class NameChecker extends TypeChecker {
		@Override
		public PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping) {
			String name = node.getText().trim();
			NameFunctor f = getName(gamma, name);
			if(f != null) {
				node.matched = f;
				node.typed = f.getReturnType(BunType.UntypedType);
				return node;
			}
			return gamma.typeErrorNode(node, "undefined name: " + name, isStrongTyping);
		}
	}
	class FieldChecker extends TypeChecker {
		@Override
		public PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping) {
			if(node.isUntyped()) {
				node.matched = null;
				return gamma.typeErrorNode(node, "undefined field: " + node.textAt(1, "") + " of " + node.get(0).typed, isStrongTyping);
			}
			return node;
		}
	}
	class ParamChecker extends TypeChecker {
		@Override
		public PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping) {
			gamma.checkTypeAt(node, 1, BunType.AnyType, isStrongTyping);
			BunType type = node.typeAt(gamma, 1, BunType.UntypedType);
			PegObject nameNode = node.get(0);
			setName(gamma, Functor._SymbolFunctor|Functor._LocalFunctor, nameNode, type, node);
			return node;
		}
	}
	class FunctionChecker extends TypeChecker {
		@Override
		public PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping) {
			PegObject block = node.get(3, null);
			if(block.gamma == null) {
				//System.out.println("**1 first time");
				SymbolTable blockgamma = block.getLocalSymbolTable();
				blockgamma.checkTypeAt(node, 1, BunType.VoidType, isStrongTyping);
				PegObject params = node.get(1, null);
				UList<BunType> typeList = new UList<BunType>(new BunType[params.size()+1]);
				for(int i = 0; i < params.size(); i++) {
					PegObject p = params.get(i);
					BunType ptype = p.typeAt(gamma, 1, null);
					typeList.add(ptype);
				}
				blockgamma.checkTypeAt(node, 2, BunType.AnyType, isStrongTyping);
				BunType returnType = node.typeAt(gamma, 2, null);
				typeList.add(returnType);
				node.typed = BunType.newFuncType(typeList);
				setName(blockgamma, Functor._SymbolFunctor|Functor._LocalFunctor, node.get(0), node.typed, node);
				setName(blockgamma, Functor._SymbolFunctor|Functor._LocalFunctor, "return", returnType);
				if(!checkReturnStatement(block)) {
					block.append(new PegObject("#return"));
				}
				block = blockgamma.tryMatch(block, false);
				node.set(3, block);
				int flag = checkNameFlag(node, 0);
				setFunctionName(gamma, flag, node.get(0), node.typed, node);
			}
			else {
				System.out.println("** second time");
				node.set(3, block.gamma.tryMatch(block, isStrongTyping));
			}
			return node;
		}
	}
	class Apply2Checker extends TypeChecker {
		@Override
		public PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping) {
			gamma.checkTypeAt(node, 0, BunType.AnyType, false);
			PegObject f = node.get(0);
			if(f.matched != null) {
				gamma.checkTypeAt(node, 1, BunType.AnyType, isStrongTyping);
				return checkFuncType(gamma, node, f.typed.getRealType(), isStrongTyping);
			}
			else {
				PegObject args = node.get(1);
				if(f.is("#name")) {
					String name = f.getText();
					Functor fc = gamma.getFunctor(name, args.size());
					gamma.tryMatchImpl("function", name, fc, args, isStrongTyping);
					if(args.matched != null) {
						args.tag = name; // for readability
						return args;
					}
				}
				if(f.is("#field")) {
					PegObject[] stackAST = args.AST;
					args.insert(0, f.get(0));
					String name = f.textAt(1, "");
					Functor fc = gamma.getFunctor(name, args.size());
					gamma.tryMatchImpl("method", name, fc, args, isStrongTyping);
					if(args.matched != null) {
						args.tag = name; // for readability
						return args;
					}
					args.AST = stackAST;
				}
			}
			return node;
		}

		private PegObject checkFuncType(SymbolTable gamma, PegObject node, BunType funcType, boolean isStrongTyping) {
			PegObject args = node.get(1);
			if(Main.VerboseMode) {
				System.out.println("FirstClassFunction : " + funcType);
			}
			if(args.size() != funcType.getFuncParamSize()) {
				return gamma.typeErrorNode(args, "mismatched function parameter: " + funcType, isStrongTyping);
			}
			for(int i = 0; i < args.size(); i++) {
				gamma.checkTypeAt(args, i, funcType.get(i), isStrongTyping);
			}
			args.typed = funcType.getReturnType();
			node.typed = args.typed;
			return node;
		}
	}

	class Return0Checker extends TypeChecker {
		@Override
		public PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping) {
			NameFunctor f = getName(gamma, "return");
			BunType type = f.funcType.getRealType();
			//System.out.println("return0 = " + type);
			gamma.checkTypeAt(node, 0, type, isStrongTyping);
			return node;
		}
	}
	class Return1Checker extends TypeChecker {
		@Override
		public PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping) {
			NameFunctor f = getName(gamma, "return");
			BunType type = f.funcType.getRealType();
			//System.out.println("return1 = " + type);
			gamma.checkTypeAt(node, 0, type, isStrongTyping);
			return node;
		}
	}
	class BlockChecker extends TypeChecker {
		@Override
		public PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping) {
			for(int i = 0; i < node.size(); i++) {
				gamma.checkTypeAt(node, i, BunType.VoidType, isStrongTyping);
			}
			return node;
		}
	}

//	class ApplyChecker extends TypeChecker {
//		@Override
//		public void check(PegObject node) {
//			System.out.println("typeCheckApply: " + node);
////			SymbolTable gamma = node.getSymbolTable();
//		}
//	}
//	class FuncDecl extends TypeChecker {
//		@Override
//		public void check(PegObject node) {
//			//System.out.println("FuncDeclCommand node: " + node);
//			SymbolTable gamma = node.getSymbolTable();
//			for(int i = 0; i < node.size(); i++) {
//				gamma.checkTypeAt(node, i, BunType.VoidType, false);
//			}
//			DefinedNameFunctor f = gamma.getName("return");
//			BunType returnType = f.getReturnType(BunType.UntypedType);
//			System.out.println("returnType="+returnType);
//			gamma.checkTypeAt(node, 0, returnType, false);
//		}
//	}
	
	private UMap<TypeChecker> checkerMap = null;
	
	public BunTypeChecker() {
		if(this.checkerMap == null) {
			this.checkerMap = new UMap<TypeChecker>();
			this.set("#Tvar:0", new TvarChecker());
			this.set("#var:3",  new VarChecker());
			this.set("#let:3",  new VarChecker());
			this.set("#name:0", new NameChecker());
			this.set("#field:2", new FieldChecker());
			
			this.set("#function:4", new FunctionChecker());
			this.set("#param:2", new ParamChecker());
			this.set("#apply:2", new Apply2Checker());
			
			this.set("#return:0", new Return0Checker());
			this.set("#return:1", new Return1Checker());
			this.set("#block:*",  new BlockChecker());
			
			this.set("#toplevel:*",  this.checkerMap.get("#block:*"));
			this.set("#params:*",    this.checkerMap.get("#block:*"));
		}
	}

	public final void set(String nodeKey, TypeChecker c) {
		this.checkerMap.put(nodeKey, c);
	}

	public final PegObject check(SymbolTable gamma, PegObject node, boolean isStrongTyping) {
		String nodeKey = node.tag + ":" + node.size();
		TypeChecker c = this.checkerMap.get(nodeKey, null);
		if(c == null) {
			c = this.checkerMap.get(node.tag+":*", null);
		}
		if(c != null) {
			if(Main.VerboseMode) {
				System.out.println("Using extended type checker: " + nodeKey);
			}
			return c.check(gamma, node, isStrongTyping);
		}
		if(Main.VerboseMode) {
			System.out.println("Unusing extended type checker: '" + nodeKey + "' c =" + c);
		}
		return node;
	}
	
}
