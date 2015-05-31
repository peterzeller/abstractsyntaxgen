package asg.grammars.ast;

import asg.asts.ast.AstEntityDefinition;

public abstract class SimpleType {
	
	
	
	
}

class SimpleTypeVoid extends SimpleType {
	@Override
	public String toString() {
		return "void";
	}
}

class SimpleTypeAst extends SimpleType {

	private AstEntityDefinition astType;

	public SimpleTypeAst(AstEntityDefinition astType) {
		this.astType = astType;
	}
	
	@Override
	public String toString() {
		return "ast<" + astType.getName() + ">";
	}
	
}

class SimpleTypeString extends SimpleType {
	@Override
	public String toString() {
		return "string";
	}
}