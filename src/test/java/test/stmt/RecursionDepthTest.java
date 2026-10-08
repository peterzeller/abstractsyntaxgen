package test.stmt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static test.stmt.TS.*;

/**
 * Copying and comparing recurse for the first levels of a tree and continue iteratively below, so that they are fast
 * for the ordinary tree and do not need the stack for a deep one. These are the trees around that depth.
 */
class RecursionDepthTest {

    private static final List<Integer> DEPTHS = List.of(0, 1, 2, 100, 254, 255, 256, 257, 258, 300, 513, 1000, 5000);

    private static TSExpr chain(int depth) {
        TSExpr expression = IntLiteral(0);
        for (int i = 1; i <= depth; i++) {
            expression = BinaryExpr(expression, i % 2 == 0 ? Plus() : Less(), IntLiteral(i));
        }
        return expression;
    }

    private static TSStatementList nestedBlocks(int depth) {
        TSStatementList list = StatementList(ExprStatement(IntLiteral(depth)));
        for (int i = 0; i < depth; i++) {
            list = StatementList(Assignment("v" + i, IntLiteral(i)), Block(list));
        }
        return list;
    }

    private static void assertParentsAreRight(TSElement root) {
        var visitor = new TSElement.IterativeVisitor() {
            @Override public void visit(TSProgram e) { check(e); }
            @Override public void visit(TSAssignment e) { check(e); }
            @Override public void visit(TSIfStatement e) { check(e); }
            @Override public void visit(TSWhileLoop e) { check(e); }
            @Override public void visit(TSBlock e) { check(e); }
            @Override public void visit(TSExprStatement e) { check(e); }
            @Override public void visit(TSBinaryExpr e) { check(e); }
            @Override public void visit(TSStatementList e) { check(e); }

            void check(TSElement e) {
                for (int i = 0; i < e.size(); i++) {
                    assertSame(e, e.get(i).getParent());
                }
            }
        };
        visitor.traverse(root);
    }

    @Test
    void aCopyOfAnyDepthIsEqualAndSeparate() {
        for (int depth : DEPTHS) {
            var original = chain(depth);
            var copy = original.copy();

            assertNotSame(original, copy, "depth " + depth);
            assertTrue(original.structuralEquals(copy), "depth " + depth);
            assertTrue(copy.structuralEquals(original), "depth " + depth);
            assertNull(copy.getParent());
            assertParentsAreRight(copy);
            assertParentsAreRight(original);
        }
    }

    @Test
    void aDifferenceIsFoundAtAnyDepth() {
        for (int depth : DEPTHS) {
            if (depth == 0) {
                continue; // the root is the leaf: nothing to replace it in
            }
            var original = chain(depth);
            var copy = original.copy();
            // the deepest operand, which is the leaf at the end of the chain of left operands
            TSExpr deepest = copy;
            while (deepest instanceof TSBinaryExpr binary) {
                deepest = binary.getLeft();
            }
            deepest.replaceBy(IntLiteral(-1));

            assertFalse(original.structuralEquals(copy), "depth " + depth);
            assertFalse(copy.structuralEquals(original), "depth " + depth);
        }
    }

    @Test
    void anOperatorWhichDiffersAtTheDepthWhereTheRecursionEndsIsFound() {
        for (int depth : List.of(255, 256, 257, 258)) {
            var original = chain(depth + 10);
            var copy = (TSBinaryExpr) original.copy();
            // walk down the left operands to the level in question and swap the operator there
            TSBinaryExpr node = copy;
            for (int level = 0; level < depth; level++) {
                node = (TSBinaryExpr) node.getLeft();
            }
            node.setOperator(node.getOperator() instanceof TSPlus ? Equals() : Plus());

            assertFalse(original.structuralEquals(copy), "depth " + depth);
        }
    }

    @Test
    void nestedListsOfAnyDepthAreCopiedAndCompared() {
        for (int depth : DEPTHS) {
            var original = nestedBlocks(depth);
            var copy = original.copy();

            assertNotSame(original, copy, "depth " + depth);
            assertEquals(original.size(), copy.size());
            assertTrue(original.structuralEquals(copy), "depth " + depth);
            assertParentsAreRight(copy);
        }
    }

    @Test
    void aCopyWhichKeepsTheReferencesWorksAtAnyDepth() {
        for (int depth : List.of(1, 256, 600)) {
            var original = chain(depth);
            var copy = original.copyWithRefs();

            assertTrue(original.structuralEquals(copy), "depth " + depth);
        }
    }

    @Test
    void aTreeWithManyDeepBranchesIsCopied() {
        // each of the statements has a deep expression below it, so the iterative part is entered many times
        var list = StatementList();
        for (int i = 0; i < 20; i++) {
            list.add(ExprStatement(chain(300 + i)));
        }
        var copy = list.copy();

        assertTrue(list.structuralEquals(copy));
        assertParentsAreRight(copy);
    }
}
