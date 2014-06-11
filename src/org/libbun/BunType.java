package org.libbun;

public abstract class BunType  {
	protected int         typeId    = -1;
	protected Object      typeInfo;  // used in Class<?> in JVM (for example)

	BunType() {
		this.typeInfo = null;
	}
	public final String getName() {
		UniStringBuilder sb = new UniStringBuilder();
		stringfy(sb);
		return sb.toString();
	}
	public final String toString() {
		UniStringBuilder sb = new UniStringBuilder();
		stringfy(sb);
		return sb.toString();
	}
	public int size() {
		return 0;
	}
	public BunType getParamType(int index) {
		return this;
	}
	public int getFuncParamSize() {
		return 0;
	}
	public BunType getFuncParamType(int index) {
		return this;
	}
	public BunType getReturnType() {
		return this;
	}
	public boolean hasVarType() {
		return false;
	}
	public BunType getRealType() {
		return this;
	}
	public boolean hasGreekType() {
		return false;
	}
	public BunType newVarGreekType(GreekList list, BunType[] buffer) {
		return this;
	}

	public abstract void    stringfy(UniStringBuilder sb);
	public boolean accept(SymbolTable gamma, PegObject node, boolean hasNextChoice) {
		BunType nodeType = node.getType(BunType.UntypedType);
		return this.is(nodeType) || this.checkCoercion(gamma, node, nodeType, hasNextChoice);
	}
	public abstract boolean is(BunType valueType);
	protected final boolean checkCoercion(SymbolTable gamma, PegObject node, BunType nodeType, boolean hasNextChoice) {
		String key = BunType.keyTypeRel("#coercion", nodeType, this);
		Functor f = gamma.getSymbol(key);
		if(f != null) {
			if(Main.EnableVerbose) {
				Main._PrintLine("found type coercion from " + nodeType + " to " + this);
			}
			node.typed = BunType.newTransType(key, nodeType, this, f);
			return true;
		}
		if(hasNextChoice) {
			return false;
		}
		return false;  // stupid cast
	}
	public void build(PegObject node, BunDriver driver) {
		node.matched.build(node, driver);
	}

	// ----------------------------------------------------------------------
	
	final static UniArray<BunType>   _TypeList;
	final static UniMap<BunType>     _TypeNameMap;

	public static BunType UntypedType;
		
	static {
		_TypeList = new UniArray<BunType>(new BunType[128]);
		_TypeNameMap = new UniMap<BunType>();
		UntypedType = new UntypedType();
		issueType("", UntypedType);
		setGenerics(new FuncType(0));
	}

	private static void issueType(String key, BunType t) {
		t.typeId = _TypeNameMap.size();
		_TypeNameMap.put(key, t);
	}
	
	public final static BunType newValueType(String name, Object typeInfo) {
		BunType t = _TypeNameMap.get(name, null);
		if(t == null) {
			t = new ValueType(name, typeInfo);
			issueType(name, t);
		}
		return t;
	}
	
	public static BunType newVarType(BunType premiseType) {
		return new VarType(premiseType);
	}

	public static BunType newVarType(PegObject node, BunType premiseType) {
		return new VarType(node, premiseType);
	}
	
	public final static void setGenerics(GenericType t) {
		issueType(t.baseName+"<>", t);
		t.genericId = t.typeId;
	}

	public final static BunType newGenericType(String baseName, UniArray<BunType> typeList) {
		GenericType gt = null;
		BunType t = _TypeNameMap.get(baseName+"<>", null);
		if(t instanceof GenericType) {
			gt = (GenericType)t;
		}
		else {
			gt = new GenericType(baseName, 0);
			setGenerics(gt);
		}
		return newGenericType(gt, typeList.ArrayValues, typeList.size());
	}

	public final static BunType newGenericType(GenericType baseType, BunType[] params) {
		return newGenericType(baseType, params, params.length);
	}
	
	public final static BunType newGenericType(GenericType baseType, BunType[] t, int size) {
		if(checkVarType(t, size)) {
			return baseType.newCloneType(compactArray(t, size));
		}
		else {
			UniStringBuilder sb = new UniStringBuilder();
			sb.append(baseType.baseName);
			mangleTypes(sb, t, size);
			String key = sb.toString();
			BunType gt = _TypeNameMap.get(key, null);
			if(gt == null) {
				gt = baseType.newCloneType(compactArray(t, size));
				issueType(key, gt);
			}
			return gt;
		}
	}
	
	private static BunType[] compactArray(BunType[] t, int size) {
		if(t.length == size) {
			return t;
		}
		BunType[] newt = new BunType[size];
		System.arraycopy(t, 0, newt, 0, size);
		return newt;
	}

	public final static boolean checkVarType(BunType[] t, int size) {
		for(int i = 0; i < size; i++) {
			if(t[i].hasVarType()) {
				return true;
			}
		}
		return false;
	}

	private final static void mangleTypes(UniStringBuilder sb, BunType[] t, int size) {
		for(int i = 0; i < size; i++) {
			sb.append("+");
			sb.appendInt(t[i].typeId);
		}
	}
	
	public final static FuncType newFuncType(UniArray<BunType> typeList) {
		BunType funcType = newGenericType(FuncType.FuncBaseName, typeList);
		if(funcType instanceof FuncType) {
			return (FuncType)funcType;
		}
		return null;
	}

	public final static FuncType newFuncType(BunType t, BunType r) {
		UniArray<BunType> typeList = new UniArray<BunType>(new BunType[2]);
		typeList.add(t);
		typeList.add(r);
		return BunType.newFuncType(typeList);
	}

	// =======================================================================
	
	public static final BunType newGreekType(int greekIndex) {
		String key = "$" + greekIndex;
		BunType t = _TypeNameMap.get(key, null);
		if(t == null) {
			t = new GreekType(greekIndex);
			issueType(key, t);
		}
		return t;
	}

	public static BunType newVoidType(String name, Object typeInfo) {
		return new VoidType(name, typeInfo);
	}

	public static BunType newAnyType(String name, Object typeInfo) {
		return new AnyType(name, typeInfo);
	}

	public static BunType newGreekType(String name, int greekId, Object typeInfo) {
		return new ValueType(name, typeInfo);
	}

	public final static String keyTypeRel(String head, BunType fromType, BunType toType) {
		return head + "+" + fromType.typeId + "+" + toType.typeId;
	}
	
	public final static BunType newTransType(String key, BunType sourceType, BunType targetType, Functor f) {
		BunType t = _TypeNameMap.get(key, null);
		if(t == null) {
			t = new TransType(sourceType, targetType, f);
			_TypeNameMap.put(key, t);
		}
		return t;
	}
	
	public final static BunType[] emptyTypes = new BunType[0];

	public final static BunType[] addTypes (BunType[] types, BunType t) {
		for(int i = 0; i < types.length; i++) {
			if(types[i] == t) {
				return types;
			}
		}
		BunType[] newtypes = new BunType[types.length+1];
		System.arraycopy(types, 0, newtypes, 0, types.length);
		newtypes[types.length] = t;
		types = newtypes;
		sortTypes(types);
		return types;
	}
	
	private final static void sortTypes(BunType[] t) {
		for(int i = 0; i < t.length - 1; i++) {
			for(int j = i + 1; j < t.length; j++) {
				if(t[i].typeId > t[j].typeId) {
					BunType e = t[i];
					t[i] = t[j];
					t[j] = e;
				}
			}
		}
	}
}

class GreekList {
	public String name;
	public BunType premiseType;
	public GreekList next;
	
	public GreekList(String name, BunType premiseType) {
		this.name = name;
		this.premiseType = premiseType;
		this.next = null;
	}
	public void append(GreekList entry) {
		GreekList cur = this;
		while(cur.next != null) {
			cur = cur.next;
		}
		cur.next = entry;
	}
	public int size() {
		int size = 0;
		GreekList cur = this;
		while(cur != null) {
			size = size + 1;
			cur = cur.next;
		}
		return size;
	}
	public BunType getGreekType(String name) {
		int size = 0;
		GreekList cur = this;
		while(cur != null) {
			if(cur.name.equals(name)) {
				return BunType.newGreekType(size);
			}
			size = size + 1;
			cur = cur.next;
		}
		return null;
	}
	public final BunType getPremiseType(int greekIndex) {
		int size = 0;
		GreekList list = this;
		while(list != null) {
			if(greekIndex == size) {
				return list.premiseType;
			}
			size = size + 1;
			list = list.next;
		}
		return null;
	}
}

abstract class QualifiedType extends BunType {
	BunType   innerType;
	public int size() {
		return this.innerType.size();
	}
	public BunType getParamType(int index) {
		return this.innerType.getParamType(index);
	}
	public int getFuncParamSize() {
		return this.innerType.getFuncParamSize();
	}
	public BunType getReturnType() {
		return this.innerType.getRealType();
	}
	public boolean hasVarType() {
		return this.innerType.hasVarType();
	}
	public BunType getRealType() {
		return this.innerType.getRealType();
	}
	public boolean hasGreekType() {
		return false;
	}
}

class VariableType extends QualifiedType {
	
}



class GreekType extends BunType {
	int greekIndex;
	public GreekType(int greekIndex) {
		super();
		this.greekIndex = greekIndex;
	}
	@Override
	public void stringfy(UniStringBuilder sb) {
		sb.append("$");
		sb.appendInt(this.greekIndex);
	}
	@Override
	public boolean is(BunType valueType) {
		return false;
	}
	@Override
	public boolean hasVarType() {
		return false;
	}
	@Override
	public boolean hasGreekType() {
		return true;
	}
	@Override
	public BunType newVarGreekType(GreekList list, BunType[] buffer) {
		if(buffer[this.greekIndex] == null) {
			buffer[this.greekIndex] = BunType.newVarType(list.getPremiseType(this.greekIndex));
		}
		return buffer[this.greekIndex];
	}
}

class UnionType extends BunType {
	public BunType[] types;
	public UnionType() {
		super();
		this.types = BunType.emptyTypes;
	}
	@Override
	public void stringfy(UniStringBuilder sb) {
		for(int i = 0; i < types.length; i++) {
			if(i > 0) {
				sb.append("|");
			}
			types[i].stringfy(sb);
		}
	}
	public void add(BunType t) {
		assert(this.typeId == -1);
		t = t.getRealType();
		if(t instanceof UnionType) {
			UnionType ut = (UnionType)t;
			for(int i = 0; i < ut.types.length; i++) {
				this.types = BunType.addTypes(this.types, ut.types[i]);
			}
		}
		else {
			this.types = BunType.addTypes(this.types, t);
		}
	}
	@Override
	public boolean is(BunType valueType) {
		valueType = valueType.getRealType();
		if(this == valueType) {
			return true;
		}
		if(valueType instanceof UnionType) {
			UnionType ut = (UnionType)valueType;
			for(int i = 0; i < ut.types.length; i++) {
				if(!this.is(ut.types[i])) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean accept(SymbolTable gamma, PegObject node, boolean hasNextChoice) {
		BunType nodeType = node.getType(BunType.UntypedType);
		if(this.is(nodeType)) {
			return true;
		}
//		for(int i = 0; i < types.length; i++) {
//			if(types[i].accept(node)) {
//				return true;
//			}
//		}
		return false;
	}

	@Override
	public BunType getRealType() {
		return this;
	}

	@Override
	public boolean hasVarType() {
		for(int i = 0; i < this.types.length; i++) {
			if(this.types[i].hasVarType()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean hasGreekType() {
		for(int i = 0; i < this.types.length; i++) {
			if(this.types[i].hasGreekType()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public BunType newVarGreekType(GreekList list, BunType[] buffer) {
		if(!this.hasGreekType()) {
			return this;
		}
		UnionType ut = new UnionType();
		for(int i = 0; i < this.types.length; i++) {
			ut.add(this.types[i].newVarGreekType(list, buffer));
		}
		return ut;
	}
}

class ValueType extends BunType {
	protected String name;
	protected BunType[] superTypes;
	ValueType(String name, Object typeInfo) {
		super();
		this.name = name;
		superTypes = BunType.emptyTypes;
	}
	
	public void addSuperType(SymbolTable gamma, BunType t) {
		this.superTypes = BunType.addTypes(this.superTypes, t);
		gamma.addUpcast(this, t);
	}

	@Override
	public final boolean hasVarType() {
		return false;
	}
	
	@Override
	public void stringfy(UniStringBuilder sb) {
		sb.append(this.name);
	}

	public boolean is(BunType valueType) {
		valueType = valueType.getRealType();
		if(valueType == this) {
			return true;
		}
		return false;
	}
}

class UntypedType extends ValueType {
	UntypedType() {
		super("untyped", null);
	}
	public boolean is(BunType valueType, BunType[] greekContext) {
		return true;
	}
	
}

class VoidType extends ValueType {
	VoidType(String name, Object typeInfo) {
		super(name, typeInfo);
	}
	public boolean is(BunType valueType, BunType[] greekContext) {
		return true;
	}
}

class AnyType extends ValueType {
	AnyType(String name, Object typeInfo) {
		super(name, typeInfo);
	}
	public boolean is(BunType valueType, BunType[] greekContext) {
		return true;
	}
}

class VarType extends BunType {
	public  PegObject node = null;
	private BunType inferredType;
	private BunType premiseType;

	public VarType(BunType premise) {
		super();
		this.premiseType = premise;
		this.inferredType = null;
	}
	public VarType(PegObject node, BunType premise) {
		this(premise);
		this.node = node;
		node.typed = this;
	}
	public int size() {
		if(this.inferredType != null) {
			return this.inferredType.size();
		}
		return 0;
	}
	public BunType getParamType(int index) {
		if(this.inferredType != null) {
			return this.inferredType.getParamType(index);
		}
		return this;
	}
	public int getFuncParamSize() {
		if(this.inferredType != null) {
			return this.inferredType.getFuncParamSize();
		}
		return 0;
	}
	public BunType getReturnType() {
		if(this.inferredType != null) {
			return this.inferredType.getReturnType();
		}
		return this;
	}
	@Override
	public BunType getRealType() {
		if(this.inferredType != null) {
			return this.inferredType.getRealType();
		}
		return this;
	}
	@Override
	public boolean hasVarType() {
		return true;
	}
	@Override
	public void stringfy(UniStringBuilder sb) {
		if(this.inferredType != null) {
			sb.append("?");
			this.inferredType.stringfy(sb);
			sb.append("?");
			return;
		}
		sb.append("?");
	}
	public final boolean is(BunType valueType) {
		if(this == valueType) {
			return true;
		}
		if(this.inferredType == null) {
//			if(this.premiseType != null) {
//				this.premiseType.accept(valueType);
//			}
			this.inferredType = valueType;
			return true;
		}
		return this.inferredType.is(valueType);
	}
}

class TransType extends BunType {
	public BunType sourceType;
	public BunType targetType;
	public Functor  functor;
	public TransType(BunType sourceType, BunType targetType, Functor functor) {
		super();
		this.sourceType = sourceType;
		this.targetType = targetType;
		this.functor = functor;
	}
	@Override
	public boolean hasVarType() {
		return false;
	}
	@Override
	public BunType getRealType() {
		return targetType.getRealType();
	}
	@Override
	public void stringfy(UniStringBuilder sb) {
		this.targetType.stringfy(sb);
		sb.append("(<-");
		this.sourceType.stringfy(sb);
		sb.append(")");
	}
	@Override
	public boolean is(BunType valueType) {
		return this.targetType.is(valueType);
	}
	public void build(PegObject node, BunDriver driver) {
		PegObject o = new PegObject(this.functor.name);
		o.append(node);
		o.matched = this.functor;
		o.typed = this.targetType;
		node.typed = this.sourceType;
		driver.pushNode(o);
		//this.functor.build(o, driver);
		node.typed = this;
	}
}

// Mutable<T>, Assignable<T> = T
// T|null

class GenericType extends BunType {
	public final static BunType[] nullParams = new BunType[0];
	protected int    genericId;
	protected String baseName;
	protected BunType[] typeParams;

	public GenericType(String baseName, int genericId) {
		super();
		this.baseName = baseName;
		this.genericId = genericId;
		typeParams = nullParams;
	}
	
	public GenericType newCloneType(BunType[] typeParams) {
		GenericType gt = new GenericType(this.baseName, this.genericId);
		gt.typeParams = typeParams;
		return gt;
	}

	@Override
	public boolean hasVarType() {
		for(int i = 0; i < typeParams.length; i++) {
			if(typeParams[i].hasVarType()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public BunType getRealType() {
		if(this.typeId == -1) {
			boolean noVarType = true;
			for(int i = 0; i < typeParams.length; i++) {
				typeParams[i] = typeParams[i].getRealType();
				if(typeParams[i].hasVarType()) {
					noVarType = false;
				}
			}
			if(noVarType) {
				return BunType.newGenericType(this, this.typeParams);
			}
		}
		return this;
	}

	@Override
	public void stringfy(UniStringBuilder sb) {
		sb.append(this.baseName);
		sb.append("<");
		for(int i = 0; i < typeParams.length; i++) {
			if(i > 0) {
				sb.append(",");
			}
			typeParams[i].stringfy(sb);
		}
		sb.append(">");
	}

	@Override
	public boolean is(BunType valueType) {
		valueType = valueType.getRealType();
		if(this == valueType) {
			return true;
		}
		if(valueType instanceof GenericType) {
			GenericType gt = (GenericType)valueType;
			if(gt.genericId == this.genericId && gt.typeParams.length == this.typeParams.length) {
				for(int i = 0; i < this.typeParams.length; i++) {
					if(!this.typeParams[i].is(gt.typeParams[i])) {
						return false;
					}
				}
				return true;
			}
		}
		return false;
	}
	
	@Override
	public final boolean hasGreekType() {
		for(int i = 0; i< this.typeParams.length; i++) {
			if(this.typeParams[i].hasGreekType()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public final BunType newVarGreekType(GreekList list, BunType[] buffer) {
		if(!this.hasGreekType()) {
			return this;
		}
		BunType[] newparams = new BunType[this.typeParams.length];
		for(int i = 0; i< this.typeParams.length; i++) {
			newparams[i] = this.typeParams[i].newVarGreekType(list, buffer);
		}
		return this.newCloneType(newparams);
	}
}


class FuncType extends GenericType {
	public final static String FuncBaseName    = "Func";

	public FuncType(int genericId) {
		super(FuncBaseName, genericId);
	}

	@Override
	public GenericType newCloneType(BunType[] typeParams) {
		FuncType t = new FuncType(this.genericId);
		t.typeParams = typeParams;
		return t;
	}

	public final int getFuncParamSize() {
		return this.typeParams.length - 1;
	}

	public final BunType getFuncParamType(int index) {
		return this.typeParams[index];
	}

	public final BunType getReturnType() {
		return this.typeParams[this.typeParams.length - 1];
	}
}

class NodeType extends BunType {
	String symbol;
	NodeType(String symbol) {
		this.symbol = symbol;
	}
	@Override
	public void stringfy(UniStringBuilder sb) {
		sb.append(this.symbol);
	}
	@Override
	public boolean is(BunType valueType) {
		return false;
	}
	public boolean accept(SymbolTable gamma, PegObject node, boolean hasNextChoice) {
		if(this.symbol.equals(node.name)) {
			return true;
		}
		return false;
	}
}

class TokenType extends NodeType {
	TokenType(String symbol) {
		super(symbol);
	}
	@Override
	public void stringfy(UniStringBuilder sb) {
		sb.append("'");
		sb.append(this.symbol);
		sb.append("'");
	}
	@Override
	public boolean is(BunType valueType) {
		return false;
	}
	public boolean accept(SymbolTable gamma, PegObject node, boolean hasNextChoice) {
		if(this.symbol.equals(node.getText())) {
			return true;
		}
		return false;
	}
}

