package test.expr;


import java.util.*;

public class ExprAttributes {

    private static int circularCalls;

    public static boolean circularInitial() {
        return false;
    }

    public static boolean circularTwice(TEIntLiteral literal) {
        circularCalls++;
        return literal.circularTwice() | literal.circularTwice();
    }

    public static Object cachedObject(TEIntLiteral literal) {
        return new byte[1024];
    }

    public static void resetCircularCalls() {
        circularCalls = 0;
    }

    public static int getCircularCalls() {
        return circularCalls;
    }

    public static String toString(TEBinaryExpr teBinaryExpr) {
        return null;
    }

    public static int evaluate(TEBinaryExpr teBinaryExpr) {
        return 0;
    }

    public static Set<String> getVariables(TEBinaryExpr teBinaryExpr) {
        return null;
    }

    public static String toString(TEUnaryExpr teUnaryExpr) {
        return null;
    }

    public static int evaluate(TEUnaryExpr teUnaryExpr) {
        return 0;
    }

    public static Set<String> getVariables(TEUnaryExpr teUnaryExpr) {
        return null;
    }

    public static String toString(TEVarRef teVarRef) {
        return null;
    }

    public static int evaluate(TEVarRef teVarRef) {
        return 0;
    }

    public static Set<String> getVariables(TEVarRef teVarRef) {
        return null;
    }

    public static String toString(TEIntLiteral teIntLiteral) {
        return null;
    }

    public static int evaluate(TEIntLiteral teIntLiteral) {
        return 0;
    }

    public static Set<String> getVariables(TEIntLiteral teIntLiteral) {
        return null;
    }

    public static String toString(TEBoolLiteral teBoolLiteral) {
        return null;
    }

    public static int evaluate(TEBoolLiteral teBoolLiteral) {
        return 0;
    }

    public static Set<String> getVariables(TEBoolLiteral teBoolLiteral) {
        return null;
    }
}
