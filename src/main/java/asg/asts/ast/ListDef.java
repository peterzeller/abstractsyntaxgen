package asg.asts.ast;

public class ListDef extends AstBaseTypeDefinition {

	private final  String name;
	public final  boolean ref;
	public final  String itemType;

	public ListDef(String name, boolean ref, String itemType) {
		this.name = name;
		this.ref = ref;
		this.itemType = itemType;
	}

	
	@Override
	public String getName() {
		return name;
	}

	
	@Override
	public String toString() {
		return name + " * " + itemType;
	}
	
}
