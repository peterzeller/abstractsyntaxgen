package test.inline;

public class InlineAttributes {
    
    public static boolean hasFunction(TIExpr expr, String funcName) {
        if (expr instanceof TIFunctionCallExpr) {
            var funcCall = (TIFunctionCallExpr) expr;
            return funcCall.getFunc() != null && funcCall.getFunc().getName().equals(funcName);
        }
        
        // Check recursively in binary expressions
        if (expr instanceof TIBinaryExpr) {
            var binaryExpr = (TIBinaryExpr) expr;
            return hasFunction(binaryExpr.getLeft(), funcName) || 
                   hasFunction(binaryExpr.getRight(), funcName);
        }
        
        return false;
    }
    
    public static boolean hasFunction(TIStatement stmt, String funcName) {
        // Implementation for statements would check all expressions within the statement
        return false; // Simplified for this example
    }
}