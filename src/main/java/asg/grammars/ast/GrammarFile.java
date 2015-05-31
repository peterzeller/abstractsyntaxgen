package asg.grammars.ast;

import java.util.List;

import asg.asts.ast.Program;
import asg.grammars.parser.GrammarsParserParser.GrammarRuleContext;

import com.google.common.collect.Lists;

public class GrammarFile extends AstElement {

	public final List<Rule> rules;
	public Program program;
	
	public GrammarFile(List<GrammarRuleContext> rules) {
		this.rules = Lists.newArrayList();
		for (GrammarRuleContext g : rules) {
			this.rules.add(g.result);
			g.result.setParent(this);
		}
	}

}
