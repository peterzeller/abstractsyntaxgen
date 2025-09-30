package test.refs;

import org.junit.jupiter.api.Test;
import test.stmt.*;

import static org.junit.jupiter.api.Assertions.*;
import static test.stmt.TS.*;

public class StmtTest {

    @Test
    public void testBasicProgramConstruction() {
        var program = Program(
                StatementList(
                        Assignment("x", IntLiteral(5)),
                        Assignment("y", IntLiteral(10))
                )
        );

        assertNotNull(program);
        assertEquals(2, program.getStatements().size());

        // Test first assignment
        var firstStmt = (TSAssignment) program.getStatements().get(0);
        assertEquals("x", firstStmt.getVarName());
        assertEquals(5, ((TSIntLiteral) firstStmt.getValue()).getIntValue());
    }

    @Test
    public void testIfStatementConstruction() {
        var ifStmt = IfStatement(
                BoolLiteral(true),
                StatementList(Assignment("x", IntLiteral(1))),
                StatementList(Assignment("x", IntLiteral(2)))
        );

        assertNotNull(ifStmt);
        assertTrue(ifStmt.getCondition() instanceof TSBoolLiteral);
        assertEquals(1, ifStmt.getThenBranch().size());
        assertEquals(1, ifStmt.getElseBranch().size());

        // Test condition
        var condition = (TSBoolLiteral) ifStmt.getCondition();
        assertTrue(condition.getBoolValue());
    }

    @Test
    public void testWhileLoopConstruction() {
        var whileLoop = WhileLoop(
                BinaryExpr(VarRef("i"), Less(), IntLiteral(10)),
                StatementList(
                        Assignment("i", BinaryExpr(VarRef("i"), Plus(), IntLiteral(1)))
                )
        );

        assertNotNull(whileLoop);
        assertTrue(whileLoop.getCondition() instanceof TSBinaryExpr);
        assertEquals(1, whileLoop.getBody().size());

        // Test condition structure
        var condition = (TSBinaryExpr) whileLoop.getCondition();
        assertTrue(condition.getLeft() instanceof TSVarRef);
        assertTrue(condition.getOperator() instanceof TSLess);
        assertTrue(condition.getRight() instanceof TSIntLiteral);
    }

    @Test
    public void testNestedBlockStructure() {
        var program = Program(
                StatementList(
                        Block(
                                StatementList(
                                        Assignment("x", IntLiteral(1)),
                                        Block(
                                                StatementList(
                                                        Assignment("y", IntLiteral(2))
                                                )
                                        )
                                )
                        )
                )
        );

        var outerBlock = (TSBlock) program.getStatements().get(0);
        assertEquals(2, outerBlock.getStatements().size());

        var innerBlock = (TSBlock) outerBlock.getStatements().get(1);
        assertEquals(1, innerBlock.getStatements().size());
    }

    @Test
    public void testStatementParentRelationships() {
        var assignment = Assignment("x", IntLiteral(5));
        var block = Block(StatementList(assignment));

        assertEquals(block.getStatements(), assignment.getParent());
        assertEquals(block, block.getStatements().getParent());
    }

    @Test
    public void testStatementCopy() {
        var original = IfStatement(
                BoolLiteral(false),
                StatementList(Assignment("x", IntLiteral(1))),
                StatementList(Assignment("y", IntLiteral(2)))
        );

        var copy = original.copy();

        assertTrue(original.structuralEquals(copy));
        assertNotSame(original, copy);
        assertNotSame(original.getCondition(), copy.getCondition());
        assertNotSame(original.getThenBranch(), copy.getThenBranch());
        assertNotSame(original.getElseBranch(), copy.getElseBranch());
    }

    @Test
    public void testStatementMatcher() {
        var stmt = Assignment("x", IntLiteral(42));

        String result = stmt.match(new TSStatement.Matcher<String>() {
            @Override
            public String case_Assignment(TSAssignment assignment) {
                return "assign " + assignment.getVarName();
            }

            @Override
            public String case_IfStatement(TSIfStatement ifStatement) {
                return "if statement";
            }

            @Override
            public String case_WhileLoop(TSWhileLoop whileLoop) {
                return "while loop";
            }

            @Override
            public String case_Block(TSBlock block) {
                return "block";
            }

            @Override
            public String case_ExprStatement(TSExprStatement exprStatement) {
                return "expression statement";
            }
        });

        assertEquals("assign x", result);
    }

    @Test
    public void testDeadCodeElimination() {
        // Test optimization scenario: if (false) { ... } should be eliminated
        var program = Program(
                StatementList(
                        Assignment("x", IntLiteral(5)),
                        IfStatement(
                                BoolLiteral(false), // Always false condition
                                StatementList(Assignment("dead", IntLiteral(99))),
                                StatementList(Assignment("y", IntLiteral(10)))
                        ),
                        Assignment("z", IntLiteral(15))
                )
        );

        var optimized = eliminateDeadCode(program);

        // Should have: x=5, y=10 (from else branch), z=15
        assertEquals(3, optimized.getStatements().size());

        // Verify the assignments are correct
        var stmt1 = (TSAssignment) optimized.getStatements().get(0);
        assertEquals("x", stmt1.getVarName());

        var stmt2 = (TSAssignment) optimized.getStatements().get(1);
        assertEquals("y", stmt2.getVarName());

        var stmt3 = (TSAssignment) optimized.getStatements().get(2);
        assertEquals("z", stmt3.getVarName());

        // Check that dead assignment is not present
        for (TSStatement stmt : optimized.getStatements()) {
            if (stmt instanceof TSAssignment) {
                var assignment = (TSAssignment) stmt;
                assertNotEquals("dead", assignment.getVarName());
            }
        }
    }

    @Test
    public void testConstantFolding() {
        // Test optimization: 2 + 3 should become 5
        var expr = BinaryExpr(IntLiteral(2), Plus(), IntLiteral(3));
        var optimized = foldConstants(expr);

        assertTrue(optimized instanceof TSIntLiteral);
        assertEquals(5, ((TSIntLiteral) optimized).getIntValue());
    }

    @Test
    public void testStatementVisitor() {
        var program = Program(
                StatementList(
                        Assignment("x", VarRef("a")),
                        Assignment("y", VarRef("b")),
                        IfStatement(
                                VarRef("c"),
                                StatementList(Assignment("z", VarRef("d"))),
                                StatementList()
                        )
                )
        );

        // Count variable references using visitor
        var varCounter = new TSElement.DefaultVisitor() {
            int count = 0;

            @Override
            public void visit(TSVarRef varRef) {
                count++;
            }
        };

        program.accept(varCounter);
        assertEquals(4, varCounter.count); // a, b, c, d
    }

    // Helper methods for optimization tests
    private TSProgram eliminateDeadCode(TSProgram program) {
        var newStatements = StatementList();

        for (TSStatement stmt : program.getStatements()) {
            if (stmt instanceof TSIfStatement) {
                var ifStmt = (TSIfStatement) stmt;
                if (ifStmt.getCondition() instanceof TSBoolLiteral) {
                    var condition = (TSBoolLiteral) ifStmt.getCondition();
                    if (condition.getBoolValue()) {
                        // Move then branch (removes from original parent)
                        var thenStmts = ifStmt.getThenBranch().removeAll();
                        newStatements.addAll(thenStmts);
                    } else {
                        // Move else branch (removes from original parent)
                        var elseStmts = ifStmt.getElseBranch().removeAll();
                        newStatements.addAll(elseStmts);
                    }
                    continue;
                }
            }
            // For other statements, we need to copy since we're building a new tree
            newStatements.add(stmt.copy());
        }

        return Program(newStatements);
    }

    private TSExpr foldConstants(TSExpr expr) {
        if (expr instanceof TSBinaryExpr) {
            var binaryExpr = (TSBinaryExpr) expr;
            if (binaryExpr.getLeft() instanceof TSIntLiteral &&
                    binaryExpr.getRight() instanceof TSIntLiteral &&
                    binaryExpr.getOperator() instanceof TSPlus) {

                int leftVal = ((TSIntLiteral) binaryExpr.getLeft()).getIntValue();
                int rightVal = ((TSIntLiteral) binaryExpr.getRight()).getIntValue();
                return IntLiteral(leftVal + rightVal);
            }
        }
        return expr;
    }
}