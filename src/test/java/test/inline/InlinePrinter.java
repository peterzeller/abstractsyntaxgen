package test.inline;

public class InlinePrinter {
    
    public static void print(TIProgram program) {
        System.out.println("=== PROGRAM ===");
        
        if (!program.getFunctions().isEmpty()) {
            System.out.println("Functions:");
            for (TIFunctionDef func : program.getFunctions()) {
                printFunction(func, "  ");
            }
            System.out.println();
        }
        
        if (!program.getInitBlock().isEmpty()) {
            System.out.println("Init Block:");
            for (TIStatement stmt : program.getInitBlock()) {
                printStatement(stmt, "  ");
            }
        }
        
        System.out.println("=== END PROGRAM ===");
    }
    
    private static void printFunction(TIFunctionDef func, String indent) {
        System.out.print(indent + "function " + func.getName() + "(");
        
        boolean first = true;
        for (TIParameter param : func.getParams()) {
            if (!first) System.out.print(", ");
            System.out.print(printType(param.getParamType()) + " " + param.getName());
            first = false;
        }
        
        System.out.println(") returns " + printType(func.getReturnType()));
        
        for (TIStatement stmt : func.getBody()) {
            printStatement(stmt, indent + "  ");
        }
    }
    
    private static void printStatement(TIStatement stmt, String indent) {
        if (stmt instanceof TIVarDecl) {
            var varDecl = (TIVarDecl) stmt;
            System.out.println(indent + "var " + varDecl.getName() + " = " + printExpr(varDecl.getInitializer()));
        } else if (stmt instanceof TIAssignment) {
            var assignment = (TIAssignment) stmt;
            System.out.println(indent + assignment.getVarName() + " = " + printExpr(assignment.getValue()));
        } else if (stmt instanceof TIIfStatement) {
            var ifStmt = (TIIfStatement) stmt;
            System.out.println(indent + "if " + printExpr(ifStmt.getCondition()));
            for (TIStatement bodyStmt : ifStmt.getBody()) {
                printStatement(bodyStmt, indent + "  ");
            }
        } else if (stmt instanceof TIReturnStatement) {
            var returnStmt = (TIReturnStatement) stmt;
            System.out.println(indent + "return " + printExpr(returnStmt.getValue()));
        } else if (stmt instanceof TIExprStatement) {
            var exprStmt = (TIExprStatement) stmt;
            System.out.println(indent + printExpr(exprStmt.getExpression()));
        } else if (stmt instanceof TIFunctionCall) {
            var funcCall = (TIFunctionCall) stmt;
            String funcName = funcCall.getFunc() != null ? funcCall.getFunc().getName() : "null";
            System.out.println(indent + funcName + "(" + printExprList(funcCall.getArgs()) + ")");
        }
    }
    
    protected static String printExpr(TIExpr expr) {
        if (expr instanceof TIIntLiteral) {
            return String.valueOf(((TIIntLiteral) expr).getIntValue());
        } else if (expr instanceof TIBoolLiteral) {
            return String.valueOf(((TIBoolLiteral) expr).getBoolValue());
        } else if (expr instanceof TIVarRef) {
            return ((TIVarRef) expr).getName();
        } else if (expr instanceof TIBinaryExpr) {
            var binaryExpr = (TIBinaryExpr) expr;
            return "(" + printExpr(binaryExpr.getLeft()) + " " + 
                   printOperator(binaryExpr.getOperator()) + " " + 
                   printExpr(binaryExpr.getRight()) + ")";
        } else if (expr instanceof TIFunctionCallExpr) {
            var funcCall = (TIFunctionCallExpr) expr;
            String funcName = funcCall.getFunc() != null ? funcCall.getFunc().getName() : "null";
            return funcName + "(" + printExprList(funcCall.getArgs()) + ")";
        }
        return expr.getClass().getSimpleName();
    }
    
    private static String printExprList(TIExprList exprs) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (TIExpr expr : exprs) {
            if (!first) sb.append(", ");
            sb.append(printExpr(expr));
            first = false;
        }
        return sb.toString();
    }
    
    private static String printOperator(TIOperator op) {
        if (op instanceof TIPlus) return "+";
        if (op instanceof TIEquals) return "==";
        return op.getClass().getSimpleName();
    }
    
    private static String printType(TITypeRef typeRef) {
        if (typeRef instanceof TISimpleType) {
            return ((TISimpleType) typeRef).getTypeName();
        }
        return typeRef.getClass().getSimpleName();
    }
}