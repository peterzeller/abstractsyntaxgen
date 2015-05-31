package asg.asts.ast;

public abstract class AstEntityDefinition {
	
	@Deprecated
	public abstract String getName();
	
	public String getName(String prefix) {
		return prefix + getName();
	}
}
