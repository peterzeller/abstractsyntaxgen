package test.inline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static test.inline.TI.*;
import static test.inline.TestLocalRemoval.programToString;

import java.util.*;

public class InlineTest {

    @Test
    public void testFlattenBugScenario() {
        // Create the original program that matches the WurstScript bug scenario:
        // var x = 1
        // function sideEffect(int r) returns int
        //    x = 4
        //    return r
        // init
        //    let y = x + sideEffect(2)
        //    if y == 3
        //       testSuccess()

        var sideEffectFunc = FunctionDef("sideEffect",
                ParameterList(Parameter(SimpleType("int"), "r")),
                SimpleType("int"),
                StatementList(
                        Assignment("x", IntLiteral(4)),
                        ReturnStatement(VarRef("r"))
                )
        );

        var program = Program(
                FunctionList(sideEffectFunc),
                StatementList(
                        VarDecl(SimpleType("int"), "x", IntLiteral(1)),
                        VarDecl(SimpleType("int"), "y",
                                BinaryExpr(
                                        VarRef("x"),
                                        Plus(),
                                        FunctionCallExpr(sideEffectFunc, ExprList(IntLiteral(2)))
                                )
                        ),
                        IfStatement(
                                BinaryExpr(VarRef("y"), Equals(), IntLiteral(3)),
                                StatementList(
                                        ExprStatement(FunctionCallExpr(null, ExprList())) // testSuccess() call
                                )
                        )
                )
        );

        System.out.println("BEFORE INLINING (Flatten Bug Scenario):");
        test.inline.InlinePrinter.print(program);

        // Perform inlining optimization
        var optimized = inlineFunction(program, "sideEffect");

        System.out.println("\nAFTER INLINING (Flatten Bug Scenario):");
        test.inline.InlinePrinter.print(optimized);

        // After inlining, we should have:
        // var x = 1
        // let temp0 = x  // saved reference to x (captures current value)
        // x = 4          // inlined assignment
        // let y = temp0 + 2  // inlined return value
        // if y == 3
        //    testSuccess()

        System.out.println("\nExpected structure:");
        System.out.println("  var x = 1");
        System.out.println("  var temp0 = x  // saved value of x");
        System.out.println("  x = 4         // inlined assignment");
        System.out.println("  var y = (temp0 + 2)  // inlined function call replaced");
        System.out.println("  if (y == 3)");
        System.out.println("    testSuccess()");
        System.out.println();

        assertEquals(0, optimized.getFunctions().size()); // sideEffect should be removed
        assertEquals(5, optimized.getInitBlock().size()); // Should have 5 statements now

        var statements = optimized.getInitBlock();

        System.out.println("Checking statements:");
        for (int i = 0; i < statements.size(); i++) {
            System.out.println("  Statement " + i + ": " + statements.get(i).getClass().getSimpleName());
        }
        System.out.println();

        // First statement: var x = 1 (unchanged)
        var stmt0 = (TIVarDecl) statements.get(0);
        System.out.println("Statement 0 - Expected: var x = 1, Actual: var " + stmt0.getName() + " = " +
                test.inline.InlinePrinter.printExpr(stmt0.getInitializer()));
        assertEquals("x", stmt0.getName());
        assertEquals(1, ((TIIntLiteral) stmt0.getInitializer()).getIntValue());

        // Second statement: let temp0 = x (saved reference to x)
        var stmt1 = (TIVarDecl) statements.get(1);
        System.out.println("Statement 1 - Expected: var temp0 = x, Actual: var " + stmt1.getName() + " = " +
                test.inline.InlinePrinter.printExpr(stmt1.getInitializer()));
        assertEquals("temp0", stmt1.getName());
        assertTrue(stmt1.getInitializer() instanceof TIVarRef);
        assertEquals("x", ((TIVarRef) stmt1.getInitializer()).getName());

        // Third statement: x = 4 (inlined from sideEffect)
        var stmt2 = (TIAssignment) statements.get(2);
        System.out.println("Statement 2 - Expected: x = 4, Actual: " + stmt2.getVarName() + " = " +
                test.inline.InlinePrinter.printExpr(stmt2.getValue()));
        assertEquals("x", stmt2.getVarName());
        assertEquals(4, ((TIIntLiteral) stmt2.getValue()).getIntValue());

        // Fourth statement: let y = temp0 + 2 (inlined function call replaced)
        var stmt3 = (TIVarDecl) statements.get(3);
        System.out.println("Statement 3 - Expected: var y = (temp0 + 2), Actual: var " + stmt3.getName() + " = " +
                test.inline.InlinePrinter.printExpr(stmt3.getInitializer()));
        assertEquals("y", stmt3.getName());
        var yInitExpr = (TIBinaryExpr) stmt3.getInitializer();
        assertTrue(yInitExpr.getLeft() instanceof TIVarRef);
        assertEquals("temp0", ((TIVarRef) yInitExpr.getLeft()).getName());
        assertEquals(2, ((TIIntLiteral) yInitExpr.getRight()).getIntValue());

        // Fifth statement: if y == 3 (unchanged)
        var stmt4 = (TIIfStatement) statements.get(4);
        System.out.println("Statement 4 - Expected: if (y == 3), Actual: if " +
                test.inline.InlinePrinter.printExpr(stmt4.getCondition()));
        var condition = (TIBinaryExpr) stmt4.getCondition();
        assertEquals("y", ((TIVarRef) condition.getLeft()).getName());
        assertEquals(3, ((TIIntLiteral) condition.getRight()).getIntValue());

        System.out.println("✓ testFlattenBugScenario passed!");
    }

    @Test
    public void testInliningWithMultipleCalls() {
        // Test inlining when a function is called multiple times
        var simpleFunc = FunctionDef("simple",
            ParameterList(Parameter(SimpleType("int"), "param")),
            SimpleType("int"),
            StatementList(
                ReturnStatement(BinaryExpr(VarRef("param"), Plus(), IntLiteral(1)))
            )
        );
        
        var program = Program(
            FunctionList(simpleFunc),
            StatementList(
                VarDecl(SimpleType("int"), "a", 
                    FunctionCallExpr(simpleFunc, ExprList(IntLiteral(5)))),
                VarDecl(SimpleType("int"), "b", 
                    FunctionCallExpr(simpleFunc, ExprList(IntLiteral(10))))
            )
        );
        
        var optimized = inlineFunction(program, "simple");
        
        // Should inline both calls
        assertEquals(2, optimized.getInitBlock().size());
        
        var stmt0 = (TIVarDecl) optimized.getInitBlock().get(0);
        assertEquals("a", stmt0.getName());
        var aInit = (TIBinaryExpr) stmt0.getInitializer();
        assertEquals(5, ((TIIntLiteral) aInit.getLeft()).getIntValue());
        assertEquals(1, ((TIIntLiteral) aInit.getRight()).getIntValue());
        
        var stmt1 = (TIVarDecl) optimized.getInitBlock().get(1);
        assertEquals("b", stmt1.getName());
        var bInit = (TIBinaryExpr) stmt1.getInitializer();
        assertEquals(10, ((TIIntLiteral) bInit.getLeft()).getIntValue());
        assertEquals(1, ((TIIntLiteral) bInit.getRight()).getIntValue());
    }

    @Test
    public void testInliningWithSideEffects() {
        // Test the specific bug scenario: function with side effects
        var globalVar = VarDecl(SimpleType("int"), "global", IntLiteral(0));

        var sideEffectFunc = FunctionDef("increment",
                ParameterList(),
                SimpleType("int"),
                StatementList(
                        Assignment("global", BinaryExpr(VarRef("global"), Plus(), IntLiteral(1))),
                        ReturnStatement(VarRef("global"))
                )
        );

        var program = Program(
                FunctionList(sideEffectFunc),
                StatementList(
                        globalVar,
                        VarDecl(SimpleType("int"), "result",
                                BinaryExpr(
                                        VarRef("global"),
                                        Plus(),
                                        FunctionCallExpr(sideEffectFunc, ExprList())
                                )
                        )
                )
        );

        System.out.println("BEFORE INLINING:");
        test.inline.InlinePrinter.print(program);

        var optimized = inlineFunction(program, "increment");

        System.out.println("\nAFTER INLINING:");
        test.inline.InlinePrinter.print(optimized);

        // After inlining:
        // var global = 0
        // let temp = 0  // saved value of global
        // global = global + 1  // inlined assignment
        // let result = temp + global  // function call replaced with global (return value)

        assertEquals(4, optimized.getInitBlock().size());

        // Check that we have the temp variable
        var tempDecl = (TIVarDecl) optimized.getInitBlock().get(1);
        assertEquals("temp0", tempDecl.getName());

        // Check that the assignment is inlined
        var assignment = (TIAssignment) optimized.getInitBlock().get(2);
        assertEquals("global", assignment.getVarName());

        // Check that the result uses temp instead of the original global reference
        var resultDecl = (TIVarDecl) optimized.getInitBlock().get(3);
        assertEquals("result", resultDecl.getName());
        var resultInit = (TIBinaryExpr) resultDecl.getInitializer();
        assertEquals("temp0", ((TIVarRef) resultInit.getLeft()).getName());
        assertEquals("global", ((TIVarRef) resultInit.getRight()).getName());
    }

    @Test
    public void testInliningPreservesStructure() {
        // Test that inlining preserves parent-child relationships correctly
        var func = FunctionDef("test",
            ParameterList(Parameter(SimpleType("int"), "x")),
            SimpleType("int"),
            StatementList(ReturnStatement(VarRef("x")))
        );
        
        var program = Program(
            FunctionList(func),
            StatementList(
                VarDecl(SimpleType("int"), "result",
                    FunctionCallExpr(func, ExprList(IntLiteral(42))))
            )
        );
        
        var optimized = inlineFunction(program, "test");
        
        // Verify parent relationships are correct
        var resultDecl = (TIVarDecl) optimized.getInitBlock().get(0);
        var initializer = resultDecl.getInitializer();
        
        assertEquals(resultDecl, initializer.getParent());
        assertEquals(optimized.getInitBlock(), resultDecl.getParent());
    }

    // The main inlining implementation
    private TIProgram inlineFunction(TIProgram program, String funcName) {
        // Find the function to inline
        TIFunctionDef targetFunc = null;
        for (TIFunctionDef func : program.getFunctions()) {
            if (func.getName().equals(funcName)) {
                targetFunc = func;
                break;
            }
        }
        
        if (targetFunc == null) {
            return program; // Function not found, return unchanged
        }
        
        // Create new program without the inlined function
        var newFunctions = FunctionList();
        for (TIFunctionDef func : program.getFunctions()) {
            if (!func.getName().equals(funcName)) {
                newFunctions.add(func.copy());
            }
        }
        
        // Inline function calls in the init block
        var newInitBlock = inlineInStatements(program.getInitBlock(), targetFunc);
        
        return Program(newFunctions, newInitBlock);
    }
    
    private TIStatementList inlineInStatements(TIStatementList statements, TIFunctionDef targetFunc) {
        var newStatements = StatementList();
        
        for (TIStatement stmt : statements) {
            if (stmt instanceof TIVarDecl) {
                var varDecl = (TIVarDecl) stmt;
                var inlinedStmt = inlineInVarDecl(varDecl, targetFunc);
                newStatements.addAll(inlinedStmt);
            } else if (stmt instanceof TIIfStatement) {
                var ifStmt = (TIIfStatement) stmt;
                var newBody = inlineInStatements(ifStmt.getBody(), targetFunc);
                newStatements.add(IfStatement(ifStmt.getCondition().copy(), newBody));
            } else {
                newStatements.add(stmt.copy());
            }
        }
        
        return newStatements;
    }
    
    private List<TIStatement> inlineInVarDecl(TIVarDecl varDecl, TIFunctionDef targetFunc) {
        var result = new ArrayList<TIStatement>();
        
        var inlineResult = inlineInExpression(varDecl.getInitializer(), targetFunc);
        
        // Add any pre-statements (like temp variable declarations)
        result.addAll(inlineResult.preStatements);
        
        // Create the modified variable declaration
        result.add(VarDecl(varDecl.getVarType().copy(), varDecl.getName(), inlineResult.expression));
        
        return result;
    }

    private InlineResult inlineInExpression(TIExpr expr, TIFunctionDef targetFunc) {
        if (expr instanceof TIFunctionCallExpr) {
            var funcCall = (TIFunctionCallExpr) expr;
            if (funcCall.getFunc() != null && funcCall.getFunc().getName().equals(targetFunc.getName())) {
                return inlineFunctionCall(funcCall, targetFunc);
            }
        } else if (expr instanceof TIBinaryExpr) {
            var binaryExpr = (TIBinaryExpr) expr;

            // Check if this binary expression contains a function call that needs inlining
            boolean leftHasCall = containsFunctionCall(binaryExpr.getLeft(), targetFunc.getName());
            boolean rightHasCall = containsFunctionCall(binaryExpr.getRight(), targetFunc.getName());

            if (leftHasCall || rightHasCall) {
                // We need to handle side effects - save variables that might be modified
                var savedVars = new HashMap<String, String>();
                var preStatements = new ArrayList<TIStatement>();
                var tempVarCounter = 0;

                // Create temp variables for variables that might be modified by the function
                for (TIStatement stmt : targetFunc.getBody()) {
                    if (stmt instanceof TIAssignment) {
                        var assignment = (TIAssignment) stmt;
                        String varName = assignment.getVarName();
                        if (!savedVars.containsKey(varName)) {
                            String tempName = "temp" + (tempVarCounter++);
                            savedVars.put(varName, tempName);
                            preStatements.add(VarDecl(SimpleType("int"), tempName, VarRef(varName)));
                        }
                    }
                }

                // Process left side
                TIExpr newLeft;
                if (leftHasCall) {
                    var leftResult = inlineInExpression(binaryExpr.getLeft(), targetFunc);
                    preStatements.addAll(leftResult.preStatements);
                    newLeft = leftResult.expression;
                } else {
                    // Replace variable references with saved temps in non-function-call expressions
                    newLeft = replaceSavedVariables(binaryExpr.getLeft(), savedVars);
                }

                // Process right side
                TIExpr newRight;
                if (rightHasCall) {
                    var rightResult = inlineInExpression(binaryExpr.getRight(), targetFunc);
                    preStatements.addAll(rightResult.preStatements);
                    newRight = rightResult.expression;
                } else {
                    newRight = binaryExpr.getRight().copy();
                }

                return new InlineResult(
                        BinaryExpr(newLeft, binaryExpr.getOperator().copy(), newRight),
                        preStatements
                );
            } else {
                // No function calls, process normally
                var leftResult = inlineInExpression(binaryExpr.getLeft(), targetFunc);
                var rightResult = inlineInExpression(binaryExpr.getRight(), targetFunc);

                var preStatements = new ArrayList<TIStatement>();
                preStatements.addAll(leftResult.preStatements);
                preStatements.addAll(rightResult.preStatements);

                return new InlineResult(
                        BinaryExpr(leftResult.expression, binaryExpr.getOperator().copy(), rightResult.expression),
                        preStatements
                );
            }
        }

        return new InlineResult(expr.copy(), new ArrayList<>());
    }

    // Helper method to check if an expression contains a function call
    private boolean containsFunctionCall(TIExpr expr, String funcName) {
        if (expr instanceof TIFunctionCallExpr) {
            var funcCall = (TIFunctionCallExpr) expr;
            return funcCall.getFunc() != null && funcCall.getFunc().getName().equals(funcName);
        } else if (expr instanceof TIBinaryExpr) {
            var binaryExpr = (TIBinaryExpr) expr;
            return containsFunctionCall(binaryExpr.getLeft(), funcName) ||
                    containsFunctionCall(binaryExpr.getRight(), funcName);
        }
        return false;
    }

    private InlineResult inlineFunctionCall(TIFunctionCallExpr funcCall, TIFunctionDef targetFunc) {
        var preStatements = new ArrayList<TIStatement>();

        // Inline the function body
        for (TIStatement stmt : targetFunc.getBody()) {
            if (stmt instanceof TIAssignment) {
                var assignment = (TIAssignment) stmt;
                // Replace parameter references in the assignment
                var newValue = replaceParameterReferences(assignment.getValue(), targetFunc, funcCall.getArgs());
                preStatements.add(Assignment(assignment.getVarName(), newValue));
            } else if (stmt instanceof TIReturnStatement) {
                var returnStmt = (TIReturnStatement) stmt;
                var returnValue = replaceParameterReferences(returnStmt.getValue(), targetFunc, funcCall.getArgs());

                return new InlineResult(returnValue, preStatements);
            }
        }

        // If no return statement, return a default value
        return new InlineResult(IntLiteral(0), preStatements);
    }
    
    private TIExpr replaceParameterReferences(TIExpr expr, TIFunctionDef func, TIExprList args) {
        if (expr instanceof TIVarRef) {
            var varRef = (TIVarRef) expr;
            // Find parameter index
            for (int i = 0; i < func.getParams().size(); i++) {
                if (func.getParams().get(i).getName().equals(varRef.getName())) {
                    return args.get(i).copy();
                }
            }
        } else if (expr instanceof TIBinaryExpr) {
            var binaryExpr = (TIBinaryExpr) expr;
            return BinaryExpr(
                replaceParameterReferences(binaryExpr.getLeft(), func, args),
                binaryExpr.getOperator().copy(),
                replaceParameterReferences(binaryExpr.getRight(), func, args)
            );
        }
        
        return expr.copy();
    }
    
    private TIExpr replaceSavedVariables(TIExpr expr, Map<String, String> savedVars) {
        if (expr instanceof TIVarRef) {
            var varRef = (TIVarRef) expr;
            if (savedVars.containsKey(varRef.getName())) {
                return VarRef(savedVars.get(varRef.getName()));
            }
        } else if (expr instanceof TIBinaryExpr) {
            var binaryExpr = (TIBinaryExpr) expr;
            return BinaryExpr(
                replaceSavedVariables(binaryExpr.getLeft(), savedVars),
                binaryExpr.getOperator().copy(),
                replaceSavedVariables(binaryExpr.getRight(), savedVars)
            );
        }
        
        return expr.copy();
    }
    
    // Helper class for inlining results
    private static class InlineResult {
        final TIExpr expression;
        final List<TIStatement> preStatements;
        
        InlineResult(TIExpr expression, List<TIStatement> preStatements) {
            this.expression = expression;
            this.preStatements = preStatements;
        }
    }

    @Test
    public void test_mult3rewrite() {
        // Reproduce WurstScript test_mult3rewrite scenario:
        // int ghs = 0
        // function foo() returns int
        //    ghs += 2
        //    return 4 + ghs
        // init
        //    let blub_c = foo() + foo()
        //    println(I2S(blub_c))
        //
        // This tests the interaction between flattening and inlining:
        // 1. Flatten: foo() + foo() -> temp = foo(); temp + foo()
        // 2. Inline: Replace foo() calls with inlined bodies
        // 3. Result should be correct regardless of optimization level

        var i2sFunc = FunctionDef("I2S",
                ParameterList(Parameter(SimpleType("int"), "i")),
                SimpleType("string"),
                StatementList() // Native function
        );

        var printlnFunc = FunctionDef("println",
                ParameterList(Parameter(SimpleType("string"), "s")),
                SimpleType("void"),
                StatementList() // Native function
        );

        var fooFunc = FunctionDef("foo",
                ParameterList(), // No parameters
                SimpleType("int"),
                StatementList(
                        // ghs += 2 (equivalent to ghs = ghs + 2)
                        Assignment("ghs", BinaryExpr(VarRef("ghs"), Plus(), IntLiteral(2))),
                        // return 4 + ghs
                        ReturnStatement(BinaryExpr(IntLiteral(4), Plus(), VarRef("ghs")))
                )
        );

        var program = Program(
                FunctionList(i2sFunc, printlnFunc, fooFunc),
                StatementList(
                        // int ghs = 0
                        VarDecl(SimpleType("int"), "ghs", IntLiteral(0)),
                        // let blub_c = foo() + foo()
                        VarDecl(SimpleType("int"), "blub_c",
                                BinaryExpr(
                                        FunctionCallExpr(fooFunc, ExprList()),
                                        Plus(),
                                        FunctionCallExpr(fooFunc, ExprList())
                                )
                        ),
                        // println(I2S(blub_c))
                        ExprStatement(
                                FunctionCallExpr(printlnFunc,
                                        ExprList(
                                                FunctionCallExpr(i2sFunc, ExprList(VarRef("blub_c")))
                                        )
                                )
                        )
                )
        );

        System.out.println("ORIGINAL PROGRAM (test_mult3rewrite):");
        test.inline.InlinePrinter.print(program);

        // Test 1: Flatten only (no inlining) - should preserve foo() calls but make order explicit
        var flattened = flattenExpressions(program);

        System.out.println("\nAFTER FLATTENING ONLY:");
        test.inline.InlinePrinter.print(flattened);

        // Test 2: Flatten + Inline - should have no foo() calls
        var flattenedAndInlined = inlineFunction(flattened, "foo");

        System.out.println("\nAFTER FLATTENING + INLINING:");
        test.inline.InlinePrinter.print(flattenedAndInlined);

        System.out.println("\nExpected behavior:");
        System.out.println("1. Flattened only: should contain 'foo()' calls but in explicit order");
        System.out.println("2. Flattened + Inlined: should contain NO 'foo()' calls");
        System.out.println();

        // Assertions for flattened version (should still contain foo() calls)
        String flattenedStr = programToString(flattened);
        assertTrue(flattenedStr.contains("foo"),
                "Flattened version should still contain foo() calls");

        // Should have temp variable for the first foo() call
        assertEquals(4, flattened.getInitBlock().size()); // ghs, temp, blub_c, println

        // Check that we have a temp variable
        var tempStmt = (TIVarDecl) flattened.getInitBlock().get(1);
        assertTrue(tempStmt.getName().startsWith("temp") || tempStmt.getName().startsWith("flatten"),
                "Should have a temporary variable for flattening");

        // Assertions for flattened + inlined version (should NOT contain foo() calls)
        String inlinedStr = programToString(flattenedAndInlined);
        assertFalse(inlinedStr.contains("foo"),
                "Inlined version should NOT contain foo() calls");

        // Should have foo function removed
        assertEquals(2, flattenedAndInlined.getFunctions().size()); // Only I2S and println

        // Verify semantic correctness by simulating execution
        int expectedResult = simulateExecution(program);
        int flattenedResult = simulateExecution(flattened);
        int inlinedResult = simulateExecution(flattenedAndInlined);

        System.out.println("Execution results:");
        System.out.println("  Original: " + expectedResult);
        System.out.println("  Flattened: " + flattenedResult);
        System.out.println("  Inlined: " + inlinedResult);

        assertEquals(expectedResult, flattenedResult,
                "Flattening should preserve semantics");
        assertEquals(expectedResult, inlinedResult,
                "Inlining should preserve semantics");

        System.out.println("✅ test_mult3rewrite passed - flattening and inlining work correctly!");
    }

    @Test
    public void test_flattenWithShortCircuit() {
        // Test flattening with short-circuit operators where order matters even more

        var isZeroFunc = FunctionDef("isZero",
                ParameterList(Parameter(SimpleType("int"), "x")),
                SimpleType("boolean"),
                StatementList(
                        Assignment("callCount", BinaryExpr(VarRef("callCount"), Plus(), IntLiteral(1))),
                        ReturnStatement(BinaryExpr(VarRef("x"), Equals(), IntLiteral(0)))
                )
        );

        var program = Program(
                FunctionList(isZeroFunc),
                StatementList(
                        VarDecl(SimpleType("int"), "callCount", IntLiteral(0)),
                        // This would be problematic without flattening in a short-circuit context
                        VarDecl(SimpleType("boolean"), "result",
                                BinaryExpr(
                                        FunctionCallExpr(isZeroFunc, ExprList(IntLiteral(0))), // true
                                        Plus(), // Using Plus instead of logical AND for simplicity
                                        FunctionCallExpr(isZeroFunc, ExprList(IntLiteral(1)))  // false
                                )
                        )
                )
        );

        System.out.println("BEFORE FLATTENING (Short Circuit Test):");
        test.inline.InlinePrinter.print(program);

        var flattened = flattenExpressions(program);

        System.out.println("\nAFTER FLATTENING (Short Circuit Test):");
        test.inline.InlinePrinter.print(flattened);

        // Should have explicit ordering
        assertTrue(flattened.getInitBlock().size() > program.getInitBlock().size(),
                "Flattening should create additional statements for explicit ordering");

        System.out.println("✅ test_flattenWithShortCircuit passed!");
    }

    // Implementation of expression flattening
    private TIProgram flattenExpressions(TIProgram program) {
        var newInitBlock = StatementList();
        var tempVarCounter = 0;

        for (TIStatement stmt : program.getInitBlock()) {
            var flattenResult = flattenStatement(stmt, tempVarCounter);
            newInitBlock.addAll(flattenResult.preStatements);
            tempVarCounter = flattenResult.nextTempCounter;
        }

        return Program(program.getFunctions().copy(), newInitBlock);
    }

    private FlattenResult flattenStatement(TIStatement stmt, int tempVarCounter) {
        var statements = new ArrayList<TIStatement>();

        if (stmt instanceof TIVarDecl) {
            var varDecl = (TIVarDecl) stmt;
            var flattenResult = flattenExpression(varDecl.getInitializer(), tempVarCounter);

            statements.addAll(flattenResult.preStatements);
            statements.add(VarDecl(varDecl.getVarType().copy(), varDecl.getName(), flattenResult.expression));

            return new FlattenResult(statements, flattenResult.nextTempCounter);
        } else if (stmt instanceof TIExprStatement) {
            var exprStmt = (TIExprStatement) stmt;
            var flattenResult = flattenExpression(exprStmt.getExpression(), tempVarCounter);

            statements.addAll(flattenResult.preStatements);
            statements.add(ExprStatement(flattenResult.expression));

            return new FlattenResult(statements, flattenResult.nextTempCounter);
        }

        // For other statement types, just copy
        statements.add(stmt.copy());
        return new FlattenResult(statements, tempVarCounter);
    }

    private FlattenResult flattenExpression(TIExpr expr, int tempVarCounter) {
        if (expr instanceof TIBinaryExpr) {
            var binaryExpr = (TIBinaryExpr) expr;

            // Check if both sides have function calls that could have side effects
            boolean leftHasFunctionCall = hasFunctionCall(binaryExpr.getLeft());
            boolean rightHasFunctionCall = hasFunctionCall(binaryExpr.getRight());

            if (leftHasFunctionCall && rightHasFunctionCall) {
                // Both sides have function calls - need to flatten
                System.out.println("Flattening binary expression with function calls on both sides");

                var preStatements = new ArrayList<TIStatement>();

                // Flatten left side and create temp variable
                var leftFlatten = flattenExpression(binaryExpr.getLeft(), tempVarCounter);
                preStatements.addAll(leftFlatten.preStatements);

                String tempName = "flattenTemp" + leftFlatten.nextTempCounter;
                preStatements.add(VarDecl(SimpleType("int"), tempName, leftFlatten.expression));

                // Flatten right side
                var rightFlatten = flattenExpression(binaryExpr.getRight(), leftFlatten.nextTempCounter + 1);
                preStatements.addAll(rightFlatten.preStatements);

                // Create new binary expression with temp variable
                var newExpr = BinaryExpr(
                        VarRef(tempName),
                        binaryExpr.getOperator().copy(),
                        rightFlatten.expression
                );

                return new FlattenResult(newExpr, preStatements, rightFlatten.nextTempCounter);
            } else {
                // Regular case - flatten recursively
                var leftFlatten = flattenExpression(binaryExpr.getLeft(), tempVarCounter);
                var rightFlatten = flattenExpression(binaryExpr.getRight(), leftFlatten.nextTempCounter);

                var preStatements = new ArrayList<TIStatement>();
                preStatements.addAll(leftFlatten.preStatements);
                preStatements.addAll(rightFlatten.preStatements);

                var newExpr = BinaryExpr(
                        leftFlatten.expression,
                        binaryExpr.getOperator().copy(),
                        rightFlatten.expression
                );

                return new FlattenResult(newExpr, preStatements, rightFlatten.nextTempCounter);
            }
        } else if (expr instanceof TIFunctionCallExpr) {
            var funcCall = (TIFunctionCallExpr) expr;

            // Flatten arguments
            var preStatements = new ArrayList<TIStatement>();
            var newArgs = ExprList();
            int currentTempCounter = tempVarCounter;

            for (TIExpr arg : funcCall.getArgs()) {
                var argFlatten = flattenExpression(arg, currentTempCounter);
                preStatements.addAll(argFlatten.preStatements);
                newArgs.add(argFlatten.expression);
                currentTempCounter = argFlatten.nextTempCounter;
            }

            return new FlattenResult(
                    FunctionCallExpr(funcCall.getFunc(), newArgs),
                    preStatements,
                    currentTempCounter
            );
        }

        // For other expressions, no flattening needed
        return new FlattenResult(expr.copy(), new ArrayList<>(), tempVarCounter);
    }

    private boolean hasFunctionCall(TIExpr expr) {
        if (expr instanceof TIFunctionCallExpr) {
            return true;
        } else if (expr instanceof TIBinaryExpr) {
            var binaryExpr = (TIBinaryExpr) expr;
            return hasFunctionCall(binaryExpr.getLeft()) || hasFunctionCall(binaryExpr.getRight());
        }
        return false;
    }

    // Helper class for flattening results
    private static class FlattenResult {
        final TIExpr expression;
        final List<TIStatement> preStatements;
        final int nextTempCounter;

        FlattenResult(TIExpr expression, List<TIStatement> preStatements, int nextTempCounter) {
            this.expression = expression;
            this.preStatements = preStatements;
            this.nextTempCounter = nextTempCounter;
        }

        FlattenResult(List<TIStatement> statements, int nextTempCounter) {
            this.expression = null;
            this.preStatements = statements;
            this.nextTempCounter = nextTempCounter;
        }
    }

    // Simulate execution to verify semantic correctness
    private int simulateExecution(TIProgram program) {
        var state = new HashMap<String, Integer>();

        // Initialize variables and execute statements
        for (TIStatement stmt : program.getInitBlock()) {
            executeStatement(stmt, state, program);
        }

        // Return the value of blub_c (the main result)
        return state.getOrDefault("blub_c", 0);
    }

    private void executeStatement(TIStatement stmt, Map<String, Integer> state, TIProgram program) {
        if (stmt instanceof TIVarDecl) {
            var varDecl = (TIVarDecl) stmt;
            int value = evaluateExpression(varDecl.getInitializer(), state, program);
            state.put(varDecl.getName(), value);
        } else if (stmt instanceof TIAssignment) {
            var assignment = (TIAssignment) stmt;
            int value = evaluateExpression(assignment.getValue(), state, program);
            state.put(assignment.getVarName(), value);
        }
        // For other statement types, we don't need to simulate them for this test
    }

    private int evaluateExpression(TIExpr expr, Map<String, Integer> state, TIProgram program) {
        if (expr instanceof TIIntLiteral) {
            return ((TIIntLiteral) expr).getIntValue();
        } else if (expr instanceof TIVarRef) {
            var varRef = (TIVarRef) expr;
            return state.getOrDefault(varRef.getName(), 0);
        } else if (expr instanceof TIBinaryExpr) {
            var binaryExpr = (TIBinaryExpr) expr;
            int left = evaluateExpression(binaryExpr.getLeft(), state, program);
            int right = evaluateExpression(binaryExpr.getRight(), state, program);

            if (binaryExpr.getOperator() instanceof TIPlus) {
                return left + right;
            }
            // Add other operators as needed
        } else if (expr instanceof TIFunctionCallExpr) {
            var funcCall = (TIFunctionCallExpr) expr;
            if (funcCall.getFunc().getName().equals("foo")) {
                // Simulate foo() execution: ghs += 2; return 4 + ghs
                int currentGhs = state.getOrDefault("ghs", 0);
                int newGhs = currentGhs + 2;
                state.put("ghs", newGhs);
                return 4 + newGhs;
            }
        }

        return 0; // Default value
    }
}