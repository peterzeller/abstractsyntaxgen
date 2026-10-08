package test.stmt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static test.stmt.TS.*;

/**
 * A DefaultVisitor visits the elements of a list from the element above it, unless a subclass looks at lists: then the
 * lists are visited as nodes, as they always were. Either way the elements are visited in the same order.
 */
class DefaultVisitorListsTest {

    static class Recording extends TSElement.DefaultVisitor {
        final List<String> seen = new ArrayList<>();

        private void record(TSElement e) { seen.add(e.getClass().getSimpleName()); }

        @Override public void visit(TSProgram e) { record(e); super.visit(e); }
        @Override public void visit(TSAssignment e) { record(e); super.visit(e); }
        @Override public void visit(TSIfStatement e) { record(e); super.visit(e); }
        @Override public void visit(TSWhileLoop e) { record(e); super.visit(e); }
        @Override public void visit(TSBlock e) { record(e); super.visit(e); }
        @Override public void visit(TSExprStatement e) { record(e); super.visit(e); }
        @Override public void visit(TSBinaryExpr e) { record(e); super.visit(e); }
        @Override public void visit(TSVarRef e) { record(e); super.visit(e); }
        @Override public void visit(TSIntLiteral e) { record(e); super.visit(e); }
        @Override public void visit(TSBoolLiteral e) { record(e); super.visit(e); }
    }

    private static TSProgram program() {
        return Program(StatementList(
            Assignment("a", BinaryExpr(IntLiteral(1), Plus(), VarRef("b"))),
            IfStatement(BoolLiteral(true), StatementList(ExprStatement(VarRef("x")), ExprStatement(IntLiteral(2))), StatementList()),
            WhileLoop(BoolLiteral(false), StatementList()),
            Block(StatementList(Block(StatementList(Assignment("c", IntLiteral(3))))))));
    }

    private static int lists(TSElement root) {
        int[] count = {0};
        new TSElement.IterativeVisitor() {
            @Override public void visit(TSStatementList e) { count[0]++; }
        }.traverse(root);
        return count[0];
    }

    @Test
    void theElementsOfTheListsAreVisitedInTheOrderOfTheTree() {
        var visitor = new Recording();
        program().accept(visitor);

        assertEquals(List.of("TSProgramImpl", "TSAssignmentImpl", "TSBinaryExprImpl", "TSIntLiteralImpl", "TSVarRefImpl",
            "TSIfStatementImpl", "TSBoolLiteralImpl", "TSExprStatementImpl", "TSVarRefImpl", "TSExprStatementImpl", "TSIntLiteralImpl",
            "TSWhileLoopImpl", "TSBoolLiteralImpl",
            "TSBlockImpl", "TSBlockImpl", "TSAssignmentImpl", "TSIntLiteralImpl"), visitor.seen);
    }

    @Test
    void aVisitorWhichLooksAtListsIsToldOfEachOfThemAndSeesTheSameElements() {
        int[] lists = {0};
        var withLists = new Recording() {
            @Override public void visit(TSStatementList e) { lists[0]++; super.visit(e); }
        };
        var without = new Recording();

        var program = program();
        program.accept(withLists);
        program.accept(without);

        assertEquals(lists(program), lists[0]);
        assertEquals(without.seen, withLists.seen);
    }

    @Test
    void aVisitorBelowOneWhichLooksAtListsIsToldOfThemToo() {
        abstract class CountsLists extends Recording {
            int lists;
            @Override public void visit(TSStatementList e) { lists++; super.visit(e); }
        }
        var visitor = new CountsLists() { };

        var program = program();
        program.accept(visitor);

        assertEquals(lists(program), visitor.lists);
    }

    @Test
    void anEmptyListIsStillVisitedWhenTheVisitorLooksAtLists() {
        var program = Program(StatementList());
        int[] lists = {0};
        program.accept(new TSElement.DefaultVisitor() {
            @Override public void visit(TSStatementList e) { lists[0]++; super.visit(e); }
        });

        assertEquals(1, lists[0]);
    }
}
