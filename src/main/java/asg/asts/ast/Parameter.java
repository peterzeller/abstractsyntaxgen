package asg.asts.ast;

public final class Parameter {

	private final boolean ignoreEquality;
    private final boolean tracked;
    private boolean source;
    public boolean isSource() { return source; }
    public void setSource(boolean source) { this.source = source; }
	private final String typ;
	public final  String name;
	public final boolean isRef;
	

	public Parameter(boolean isRef, boolean ignoreEquality, String typ, String name) {
        this(isRef, ignoreEquality, false, typ, name);
    }

    public Parameter(boolean isRef, boolean ignoreEquality, boolean tracked, String typ, String name) {
        this.tracked = tracked;
		this.isRef = isRef;
		this.ignoreEquality = ignoreEquality;
		this.typ = typ;
		this.name = name;
	}
	
	public Parameter(String typ, String name) {
        this.tracked = false;
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

	public boolean isTracked() { return tracked; }

    public boolean isIgnoreEquality() {
		return ignoreEquality;
	}

}
