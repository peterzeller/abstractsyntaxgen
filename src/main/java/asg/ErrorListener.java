package asg;

import org.antlr.v4.runtime.DiagnosticErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class ErrorListener extends DiagnosticErrorListener {
	private int errCount = 0;

	@Override
	public void syntaxError(Recognizer<?, ?> recognizer,
			Object offendingSymbol, int line, int charPositionInLine,
			String msg, RecognitionException e) {
		super.syntaxError(recognizer, offendingSymbol, line,
				charPositionInLine, msg, e);
		errCount++;
	}

	public int getErrCount() {
		return errCount;
	}


}
