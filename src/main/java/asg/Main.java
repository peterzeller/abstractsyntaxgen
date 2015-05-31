package asg;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;

import asg.asts.FileGenerator;
import asg.asts.Generator;
import asg.asts.ast.Program;
import asg.asts.parser.AsgAntlrParserLexer;
import asg.asts.parser.AsgAntlrParserParser;
import asg.grammars.GrammarTranslation;
import asg.grammars.parser.GrammarsParserLexer;
import asg.grammars.parser.GrammarsParserParser;
import asg.grammars.parser.GrammarsParserParser.GrammarFileContext;

public class Main {

	/**
	 * @param args
	 * @throws FileNotFoundException 
	 */
	public static void main(String[] args) throws FileNotFoundException {
		try {
			if (args.length != 2) {
				System.out.println("2 parameters required.");
				System.out.println("parameter 1: input file");
				System.out.println("parameter 2: output folder");
				System.exit(2);
				return;
			}
			String inputFile = args[0];
			String outputFolder = args[1];
			
			//			AsgScanner scanner = new AsgScanner(new FileInputStream(inputFile));
//			AsgParser parser = new AsgParser(scanner);
//			Program prog = parser.parse();
			
			
			Program prog = compileAstSpec(inputFile, outputFolder);
			
			File out = new File(outputFolder, prog.getPackageName().replace('.', '/') + '/');
			
			FileGenerator fileGenerator = new FileGenerator(out);
			Generator gen = new Generator(fileGenerator, prog, outputFolder);
			gen.generate();
			
			
			String inputFileG = inputFile + ".g";
			if (new File(inputFileG).exists()) {
				compileGrammarSpec(fileGenerator, inputFileG, prog);
			}
			fileGenerator.removeOldFiles();
		} catch (Throwable t) {
			t.printStackTrace();
			System.out.println(t.getMessage());
			System.exit(3);
		}
	}

	public static Program compileAstSpec(String inputFile, String outputFolder)
			throws IOException {
		AsgAntlrParserLexer lexer = new AsgAntlrParserLexer(new ANTLRFileStream(inputFile));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		AsgAntlrParserParser parser = new AsgAntlrParserParser(tokens);
		
		ErrorListener errListener = new ErrorListener();
		parser.addErrorListener(errListener);
		
		Program prog = parser.spec().prog;
		
		if (errListener.getErrCount() > 0) {
			System.exit(1);
		}
		return prog;
	}
	
	public static void compileGrammarSpec(FileGenerator fileGenerator, String grammarFile, Program prog)
			throws IOException {
		
		GrammarsParserLexer lexer = new GrammarsParserLexer(new ANTLRFileStream(grammarFile));
		
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		
		GrammarsParserParser parser = new GrammarsParserParser(tokens);
		
		ErrorListener errListener = new ErrorListener();
		parser.addErrorListener(errListener);

		GrammarFileContext f = parser.grammarFile();
		f.result.program = prog;
		
		new GrammarTranslation(fileGenerator, f.result, prog).translate();
	}

}
