package asg.asts.ast;

import java.util.List;

import com.google.common.collect.Lists;

public class ConstructorDef extends AstBaseTypeDefinition {

	public final List<Parameter> parameters;
	private final String name;

	public ConstructorDef(String name, List<Parameter> parameters) {
		this.name = name;
		this.parameters = parameters;
	}

	public ConstructorDef(String name) {
		this.name = name;
		this.parameters = Lists.newArrayList();
	}

	@Override
	public String getName() {
		return name;
	}
	

	@Override
	public String toString() {
		String result = name + "(";
		boolean first = true;
		for (Parameter p : parameters) {
			if (!first) {
				result += ", ";
			}
			result += p;
			first = false;
		}
		result +=")";
		return result;
	}

	public void addParam(boolean ref, boolean ignoreEquality, String type, String name) {
		parameters.add(new Parameter(ref, ignoreEquality, type, name));
	}
	
	
}
