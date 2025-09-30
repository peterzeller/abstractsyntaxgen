package test.inline;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static test.inline.TI.*;

public class TestLocalRemoval {

    @Test
    public void test_tempVarRemover() {
        // Reproduce WurstScript test_tempVarRemover2 scenario:
        // let blablub = GetRandomInt(0,100)
        // println(I2S(blablub))
        //
        // Should be optimized to:
        // println(I2S(GetRandomInt(0,100)))
        // (eliminating the temporary variable 'blablub')

        // Create native functions (they don't have bodies, just signatures)
        var getRandomIntFunc = FunctionDef("GetRandomInt",
                ParameterList(
                        Parameter(SimpleType("int"), "a"),
                        Parameter(SimpleType("int"), "b")
                ),
                SimpleType("int"),
                StatementList() // Empty body for native functions
        );

        var i2sFunc = FunctionDef("I2S",
                ParameterList(Parameter(SimpleType("int"), "i")),
                SimpleType("string"),
                StatementList() // Empty body for native functions
        );

        var printlnFunc = FunctionDef("println",
                ParameterList(Parameter(SimpleType("string"), "s")),
                SimpleType("void"),
                StatementList() // Empty body for native functions
        );

        var program = Program(
                FunctionList(getRandomIntFunc, i2sFunc, printlnFunc),
                StatementList(
                        // let blablub = GetRandomInt(0,100)
                        VarDecl(SimpleType("int"), "blablub",
                                FunctionCallExpr(getRandomIntFunc, ExprList(IntLiteral(0), IntLiteral(100)))),
                        // println(I2S(blablub))
                        ExprStatement(
                                FunctionCallExpr(printlnFunc,
                                        ExprList(
                                                FunctionCallExpr(i2sFunc,
                                                        ExprList(VarRef("blablub"))
                                                )
                                        )
                                )
                        )
                )
        );

        System.out.println("BEFORE TEMP VAR ELIMINATION:");
        test.inline.InlinePrinter.print(program);

        // Perform temporary variable elimination
        var optimized = eliminateTemporaryVariables(program);

        System.out.println("\nAFTER TEMP VAR ELIMINATION:");
        test.inline.InlinePrinter.print(optimized);

        System.out.println("\nExpected structure:");
        System.out.println("  println(I2S(GetRandomInt(0, 100)))  // blablub eliminated");
        System.out.println();

        // After optimization:
        // 1. The 'blablub' variable declaration should be removed
        // 2. The variable reference should be replaced with the function call

        assertEquals(3, optimized.getFunctions().size()); // All native functions remain
        assertEquals(1, optimized.getInitBlock().size()); // Only the println statement remains

        // Check that we have only the println statement
        var stmt = (TIExprStatement) optimized.getInitBlock().get(0);
        var printlnCall = (TIFunctionCallExpr) stmt.getExpression();
        assertEquals("println", printlnCall.getFunc().getName());

        // Check that the argument is I2S(GetRandomInt(...))
        var i2sCall = (TIFunctionCallExpr) printlnCall.getArgs().get(0);
        assertEquals("I2S", i2sCall.getFunc().getName());

        var getRandomCall = (TIFunctionCallExpr) i2sCall.getArgs().get(0);
        assertEquals("GetRandomInt", getRandomCall.getFunc().getName());
        assertEquals(0, ((TIIntLiteral) getRandomCall.getArgs().get(0)).getIntValue());
        assertEquals(100, ((TIIntLiteral) getRandomCall.getArgs().get(1)).getIntValue());

        // Verify that 'blablub' variable is completely eliminated
        String programStr = programToString(optimized);
        assertFalse(programStr.contains("blablub"),
                "Variable 'blablub' should be completely eliminated from the program");

        System.out.println("✅ test_tempVarRemover passed - temporary variable eliminated!");
    }

    @Test
    public void test_tempVarRemoverMultipleUses() {
        // Test case where temp var elimination should NOT happen
        // because the variable is used multiple times

        var getRandomIntFunc = FunctionDef("GetRandomInt",
                ParameterList(
                        Parameter(SimpleType("int"), "a"),
                        Parameter(SimpleType("int"), "b")
                ),
                SimpleType("int"),
                StatementList()
        );

        var printlnFunc = FunctionDef("println",
                ParameterList(Parameter(SimpleType("string"), "s")),
                SimpleType("void"),
                StatementList()
        );

        var program = Program(
                FunctionList(getRandomIntFunc, printlnFunc),
                StatementList(
                        // let value = GetRandomInt(0,100)
                        VarDecl(SimpleType("int"), "value",
                                FunctionCallExpr(getRandomIntFunc, ExprList(IntLiteral(0), IntLiteral(100)))),
                        // Use 'value' twice - this should prevent elimination
                        ExprStatement(FunctionCallExpr(printlnFunc, ExprList(VarRef("value")))),
                        ExprStatement(FunctionCallExpr(printlnFunc, ExprList(VarRef("value"))))
                )
        );

        System.out.println("BEFORE TEMP VAR ELIMINATION (Multiple Uses):");
        test.inline.InlinePrinter.print(program);

        var optimized = eliminateTemporaryVariables(program);

        System.out.println("\nAFTER TEMP VAR ELIMINATION (Multiple Uses):");
        test.inline.InlinePrinter.print(optimized);

        // Variable should NOT be eliminated because it's used multiple times
        assertEquals(3, optimized.getInitBlock().size()); // All statements should remain

        // First statement should still be the variable declaration
        assertTrue(optimized.getInitBlock().get(0) instanceof TIVarDecl);
        var varDecl = (TIVarDecl) optimized.getInitBlock().get(0);
        assertEquals("value", varDecl.getName());

        System.out.println("✅ test_tempVarRemoverMultipleUses passed - variable kept due to multiple uses!");
    }

    @Test
    public void test_tempVarRemoverWithSideEffects() {
        // Test case where temp var elimination should NOT happen
        // because there are side effects between declaration and use

        var getRandomIntFunc = FunctionDef("GetRandomInt",
                ParameterList(
                        Parameter(SimpleType("int"), "a"),
                        Parameter(SimpleType("int"), "b")
                ),
                SimpleType("int"),
                StatementList()
        );

        var sideEffectFunc = FunctionDef("sideEffect",
                ParameterList(),
                SimpleType("void"),
                StatementList(Assignment("globalVar", IntLiteral(42)))
        );

        var program = Program(
                FunctionList(getRandomIntFunc, sideEffectFunc),
                StatementList(
                        VarDecl(SimpleType("int"), "globalVar", IntLiteral(0)),
                        // let temp = GetRandomInt(0,100)
                        VarDecl(SimpleType("int"), "temp",
                                FunctionCallExpr(getRandomIntFunc, ExprList(IntLiteral(0), IntLiteral(100)))),
                        // Side effect between declaration and use
                        ExprStatement(FunctionCallExpr(sideEffectFunc, ExprList())),
                        // Use temp after side effect
                        Assignment("result", VarRef("temp"))
                )
        );

        System.out.println("BEFORE TEMP VAR ELIMINATION (With Side Effects):");
        test.inline.InlinePrinter.print(program);

        var optimized = eliminateTemporaryVariables(program);

        System.out.println("\nAFTER TEMP VAR ELIMINATION (With Side Effects):");
        test.inline.InlinePrinter.print(optimized);

        // Variable should NOT be eliminated due to intervening side effect
        assertEquals(4, optimized.getInitBlock().size());

        // Check that temp variable declaration is still there
        var tempDecl = (TIVarDecl) optimized.getInitBlock().get(1);
        assertEquals("temp", tempDecl.getName());

        System.out.println("✅ test_tempVarRemoverWithSideEffects passed - variable kept due to side effects!");
    }

    // Implementation of temporary variable elimination
    private TIProgram eliminateTemporaryVariables(TIProgram program) {
        var newInitBlock = StatementList();
        var eliminatedVars = new HashSet<String>();

        // First pass: identify variables that can be eliminated
        var candidates = findEliminationCandidates(program.getInitBlock());

        System.out.println("Elimination candidates: " + candidates.keySet());

        // Second pass: eliminate variables and substitute their uses
        for (TIStatement stmt : program.getInitBlock()) {
            if (stmt instanceof TIVarDecl) {
                var varDecl = (TIVarDecl) stmt;
                if (candidates.containsKey(varDecl.getName())) {
                    // Don't add this variable declaration - it will be eliminated
                    eliminatedVars.add(varDecl.getName());
                    System.out.println("Eliminating variable: " + varDecl.getName());
                    continue;
                }
            }

            // Process statement and substitute eliminated variables
            var processedStmt = substituteVariables(stmt, candidates);
            newInitBlock.add(processedStmt);
        }

        return Program(program.getFunctions().copy(), newInitBlock);
    }

    // Find variables that are candidates for elimination
    private Map<String, TIExpr> findEliminationCandidates(TIStatementList statements) {
        var varDefs = new HashMap<String, TIExpr>();
        var varUses = new HashMap<String, Integer>();
        var hasSideEffectsBetween = new HashSet<String>();

        // Collect variable definitions and count uses
        for (int i = 0; i < statements.size(); i++) {
            TIStatement stmt = statements.get(i);

            if (stmt instanceof TIVarDecl) {
                var varDecl = (TIVarDecl) stmt;
                varDefs.put(varDecl.getName(), varDecl.getInitializer());
                varUses.put(varDecl.getName(), 0);

                // Check if there are side effects after this declaration
                if (hasSideEffectsAfter(statements, i + 1, varDecl.getName())) {
                    hasSideEffectsBetween.add(varDecl.getName());
                }
            }

            // Count variable uses in this statement
            countVariableUses(stmt, varUses);
        }

        // Filter candidates: only variables with exactly 1 use and no side effects
        var candidates = new HashMap<String, TIExpr>();
        for (var entry : varDefs.entrySet()) {
            String varName = entry.getKey();
            TIExpr initializer = entry.getValue();

            if (varUses.get(varName) == 1 && !hasSideEffectsBetween.contains(varName)) {
                candidates.put(varName, initializer);
            }
        }

        return candidates;
    }

    // Check if there are side effects after a variable declaration
    private boolean hasSideEffectsAfter(TIStatementList statements, int startIndex, String varName) {
        for (int i = startIndex; i < statements.size(); i++) {
            TIStatement stmt = statements.get(i);

            // Check if this statement uses the variable
            if (usesVariable(stmt, varName)) {
                break; // Stop checking after we find the use
            }

            // Check if this statement has side effects
            if (hasSideEffects(stmt)) {
                return true;
            }
        }
        return false;
    }

    // Check if a statement has side effects (assignments, function calls with side effects)
    private boolean hasSideEffects(TIStatement stmt) {
        if (stmt instanceof TIAssignment) {
            return true; // Assignments are side effects
        }
        if (stmt instanceof TIExprStatement) {
            var exprStmt = (TIExprStatement) stmt;
            return hasSideEffects(exprStmt.getExpression());
        }
        return false;
    }

    private boolean hasSideEffects(TIExpr expr) {
        if (expr instanceof TIFunctionCallExpr) {
            var funcCall = (TIFunctionCallExpr) expr;
            // For simplicity, assume all function calls have side effects
            // In a real implementation, you'd check function annotations/properties
            return true;
        }
        if (expr instanceof TIBinaryExpr) {
            var binaryExpr = (TIBinaryExpr) expr;
            return hasSideEffects(binaryExpr.getLeft()) || hasSideEffects(binaryExpr.getRight());
        }
        return false;
    }

    // Check if a statement uses a specific variable
    private boolean usesVariable(TIStatement stmt, String varName) {
        var checker = new VariableUseChecker(varName);
        stmt.accept(checker);
        return checker.found;
    }

    // Count uses of variables in a statement
    private void countVariableUses(TIStatement stmt, Map<String, Integer> varUses) {
        stmt.accept(new TIElement.DefaultVisitor() {
            @Override
            public void visit(TIVarRef varRef) {
                String name = varRef.getName();
                if (varUses.containsKey(name)) {
                    varUses.put(name, varUses.get(name) + 1);
                }
            }
        });
    }

    // Substitute eliminated variables with their initializers
    private TIStatement substituteVariables(TIStatement stmt, Map<String, TIExpr> substitutions) {
        if (stmt instanceof TIExprStatement) {
            var exprStmt = (TIExprStatement) stmt;
            return ExprStatement(substituteInExpression(exprStmt.getExpression(), substitutions));
        } else if (stmt instanceof TIAssignment) {
            var assignment = (TIAssignment) stmt;
            return Assignment(assignment.getVarName(),
                    substituteInExpression(assignment.getValue(), substitutions));
        }
        return stmt.copy(); // For other statement types, just copy
    }

    private TIExpr substituteInExpression(TIExpr expr, Map<String, TIExpr> substitutions) {
        if (expr instanceof TIVarRef) {
            var varRef = (TIVarRef) expr;
            if (substitutions.containsKey(varRef.getName())) {
                return substitutions.get(varRef.getName()).copy();
            }
        } else if (expr instanceof TIFunctionCallExpr) {
            var funcCall = (TIFunctionCallExpr) expr;
            var newArgs = ExprList();
            for (TIExpr arg : funcCall.getArgs()) {
                newArgs.add(substituteInExpression(arg, substitutions));
            }
            return FunctionCallExpr(funcCall.getFunc(), newArgs);
        } else if (expr instanceof TIBinaryExpr) {
            var binaryExpr = (TIBinaryExpr) expr;
            return BinaryExpr(
                    substituteInExpression(binaryExpr.getLeft(), substitutions),
                    binaryExpr.getOperator().copy(),
                    substituteInExpression(binaryExpr.getRight(), substitutions)
            );
        }
        return expr.copy();
    }

    // Helper class to check if a variable is used
    private static class VariableUseChecker extends TIElement.DefaultVisitor {
        private final String targetVar;
        boolean found = false;

        VariableUseChecker(String targetVar) {
            this.targetVar = targetVar;
        }

        @Override
        public void visit(TIVarRef varRef) {
            if (varRef.getName().equals(targetVar)) {
                found = true;
            }
        }
    }

    // Helper method to convert program to string for assertions
    public static String programToString(TIProgram program) {
        var sb = new StringBuilder();
        for (TIStatement stmt : program.getInitBlock()) {
            sb.append(statementToString(stmt)).append("\n");
        }
        return sb.toString();
    }

    public static  String statementToString(TIStatement stmt) {
        if (stmt instanceof TIVarDecl) {
            var varDecl = (TIVarDecl) stmt;
            return "var " + varDecl.getName() + " = " + test.inline.InlinePrinter.printExpr(varDecl.getInitializer());
        } else if (stmt instanceof TIExprStatement) {
            var exprStmt = (TIExprStatement) stmt;
            return test.inline.InlinePrinter.printExpr(exprStmt.getExpression());
        }
        return stmt.getClass().getSimpleName();
    }
}
