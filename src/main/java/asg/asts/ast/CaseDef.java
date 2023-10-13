package asg.asts.ast;

import java.util.List;

import com.google.common.collect.Lists;

public class CaseDef extends AstEntityDefinition {

	public final  List<Alternative> alternatives;
	private final  String name;

	
	public CaseDef(String supertype, List<Alternative> alternatives) {
		this.name = supertype;
		this.alternatives = alternatives;
	}

	public CaseDef(String name) {
		this.name = name;
		alternatives = Lists.newArrayList();
	}

	@Override
	public String getName() {
		return name;
	}

	
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder(name + " = ");
		boolean first = true;
		for (Alternative a : alternatives) {
			if (!first) result.append(" | ");
			result.append(a.name);
			first = false;
		}
		return result.toString();
	}

	public void addAlternative(String alternative) {
		alternatives.add(new Alternative(alternative));
	}
}
