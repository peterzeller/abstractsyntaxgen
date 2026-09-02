package test.stmt;

import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static test.stmt.TS.*;

class GeneratedPerformanceTest {

    @Test
    void bulkInsertionFromListDoesNotAllocateAnIterator() {
        var first = ExprStatement(IntLiteral(1));
        var second = ExprStatement(IntLiteral(2));
        List<TSStatement> source = new AbstractList<>() {
            @Override public TSStatement get(int index) {
                return switch (index) {
                    case 0 -> first;
                    case 1 -> second;
                    default -> throw new IndexOutOfBoundsException(index);
                };
            }

            @Override public int size() { return 2; }

            @Override public Iterator<TSStatement> iterator() {
                throw new AssertionError("List iterator must not be requested");
            }
        };

        var destination = StatementList();
        assertTrue(destination.addAll(source));
        assertSame(first, destination.get(0));
        assertSame(second, destination.get(1));
        assertSame(destination, first.getParent());
        assertSame(destination, second.getParent());
    }

    @Test
    void indexedBulkInsertionPreservesOrderAndParents() {
        var destination = StatementList(
                ExprStatement(IntLiteral(1)),
                ExprStatement(IntLiteral(4))
        );
        List<TSStatement> inserted = new ArrayList<>();
        inserted.add(ExprStatement(IntLiteral(2)));
        inserted.add(ExprStatement(IntLiteral(3)));

        destination.addAll(1, inserted);

        assertEquals(List.of(1, 2, 3, 4), destination.stream()
                .map(statement -> ((TSIntLiteral) ((TSExprStatement) statement).getExpression()).getIntValue())
                .toList());
        inserted.forEach(statement -> assertSame(destination, statement.getParent()));
    }

    @Test
    void failedBulkInsertionRollsBackPreparedParents() {
        var destination = StatementList();
        var unattached = ExprStatement(IntLiteral(1));
        var alreadyAttached = ExprStatement(IntLiteral(2));
        var owner = StatementList(alreadyAttached);

        assertThrows(Error.class, () -> destination.addAll(List.of(unattached, alreadyAttached)));

        assertTrue(destination.isEmpty());
        assertNull(unattached.getParent());
        assertSame(owner, alreadyAttached.getParent());
    }

    @Test
    void repeatedReplaceByKeepsTheLazyIdentityIndexCorrect() {
        var list = StatementList();
        var originals = new ArrayList<TSStatement>();
        for (int i = 0; i < 2_000; i++) {
            var statement = ExprStatement(IntLiteral(i));
            originals.add(statement);
            list.add(statement);
        }

        for (int i = 0; i < originals.size(); i++) {
            originals.get(i).replaceBy(ExprStatement(IntLiteral(-i)));
        }

        assertEquals(2_000, list.size());
        assertEquals(-1_999,
                ((TSIntLiteral) ((TSExprStatement) list.get(1_999)).getExpression()).getIntValue());
        originals.forEach(statement -> assertNull(statement.getParent()));
    }

    @Test
    void deepGeneratedOperationsAreStackSafe() {
        TSExpr expression = IntLiteral(0);
        int depth = 10_000;
        for (int i = 0; i < depth; i++) {
            expression = BinaryExpr(expression, Plus(), IntLiteral(i));
        }

        var copy = expression.copy();
        assertTrue(expression.structuralEquals(copy));
        assertDoesNotThrow(expression::clearAttributes);
        assertTrue(expression.toString().endsWith("..."));

        int[] visits = {0};
        var visitor = new TSElement.IterativeVisitor() {
            @Override public void visit(TSBinaryExpr binaryExpr) { visits[0]++; }
            @Override public void visit(TSPlus plus) { visits[0]++; }
            @Override public void visit(TSIntLiteral intLiteral) { visits[0]++; }
        };
        visitor.traverse(expression);
        assertEquals(depth * 3 + 1, visits[0]);
    }
}
