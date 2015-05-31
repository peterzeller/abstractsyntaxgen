package asg.grammars.ast;

import org.antlr.v4.runtime.Token;



public class ProdRepeat extends Production {

	public final Production prod;
	public final RepeatType repType;

	public ProdRepeat(Production prod, RepeatType repType) {
		this.prod = prod;
		this.repType = repType;
		prod.setParent(this);
	}

	public static Production create(Production p, Token mod) {
		if (mod == null) {
			return p;
		}
		RepeatType repType;
		if (mod.getText().equals("+")) {
			repType = RepeatType.AT_LEAST_ONCE;
		} else if (mod.getText().equals("*")) {
			repType = RepeatType.ARBITRARY;
		} else if (mod.getText().equals("?")) {
			repType = RepeatType.ZERO_OR_ONCE;
		} else {
			throw new Error(mod.getText());
		}
		return new ProdRepeat(p, repType);
	}

	@Override
	public void print(StringBuilder tr) {
		tr.append("(");
		prod.print(tr);
		tr.append(")");
		switch (repType) {
		case ARBITRARY:
			tr.append("*");
			break;
		case AT_LEAST_ONCE:
			tr.append("+");
			break;
		case ZERO_OR_ONCE:
			tr.append("+");
			break;
		}
	}

	@Override
	public ProdType getType() {
		// TODO add repeat- and optional- types?
		return prod.getType();
	}
	
	
	
}
