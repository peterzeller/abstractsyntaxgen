package asg.grammars.ast;

import org.antlr.v4.runtime.Token;

public class ProdLex extends ProdNamed {

	public final String lex;

	public ProdLex(Token name, Token op, Token lex) {
		super(name, op);
		this.lex = lex.getText().substring(1, lex.getText().length() - 1);
	}
	
	@Override
	public void print(StringBuilder tr) {
		super.printName(tr);
		tr.append("'").append(lex).append("'");
	}

	@Override
	public ProdType getType() {
		String n = name;
		if (n == null) {
			n = "anon";
		}
		return new ProdType(n, new SimpleTypeString());
	}

}
