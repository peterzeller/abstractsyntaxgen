package asg.asts.ast;

public final class Parameter {

	private final boolean ignoreEquality;
	private final String typ;
	public final  String name;
	public final boolean isRef;
	

	public Parameter(boolean isRef, boolean ignoreEquality, String typ, String name) {
		this.isRef = isRef;
		this.ignoreEquality = ignoreEquality;
		this.typ = typ;
		this.name = name;
	}
	
	public Parameter(String typ, String name) {
		this.ignoreEquality = false;
		this.isRef = false;
		this.typ = typ;
		this.name = name;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Parameter) {
			Parameter parameter = (Parameter) obj;
			return getTyp().equals(parameter.getTyp())
					&& name.equals(parameter.name);
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return name.hashCode() ^ getTyp().hashCode();
	}
	
	@Override
	public String toString() {
		return getTyp() + " " + name;
	}

	public String getTyp() {
		return typ;
	}

	public boolean isIgnoreEquality() {
		return ignoreEquality;
	}

}
