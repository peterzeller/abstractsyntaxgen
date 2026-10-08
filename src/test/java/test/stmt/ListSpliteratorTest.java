package test.stmt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Spliterator;

import static org.junit.jupiter.api.Assertions.*;
import static test.stmt.TS.*;

class ListSpliteratorTest {
    @Test
    void aStreamBindsWhenItsTerminalOperationStarts() {
        var first = ExprStatement(IntLiteral(1));
        var second = ExprStatement(IntLiteral(2));
        var list = StatementList(first);
        var stream = list.stream();
        list.add(second);
        assertEquals(List.of(first, second), stream.toList());
    }

    @Test
    void aBoundSpliteratorDetectsChangesBeforeDeliveringAnElement() {
        var list = StatementList(ExprStatement(IntLiteral(1)), ExprStatement(IntLiteral(2)));
        var split = list.spliterator();
        assertTrue(split.tryAdvance(e -> {}));
        list.clear();
        assertThrows(ConcurrentModificationException.class, () -> split.tryAdvance(e -> fail("delivered a stale element")));
    }

    @Test
    void splitTraversalsKeepTheirOrderAndDetectChanges() {
        var list = StatementList();
        for (int i = 0; i < 1000; i++) list.add(ExprStatement(IntLiteral(i)));
        assertEquals(new ArrayList<>(list), list.parallelStream().toList());
        var suffix = list.spliterator();
        var prefix = suffix.trySplit();
        assertNotNull(prefix);
        assertEquals(1000, prefix.estimateSize() + suffix.estimateSize());
        assertTrue(prefix.hasCharacteristics(Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED));
        var seen = new ArrayList<TSStatement>();
        prefix.forEachRemaining(seen::add);
        suffix.forEachRemaining(seen::add);
        assertEquals(new ArrayList<>(list), seen);

        suffix = list.spliterator();
        prefix = suffix.trySplit();
        list.remove(0);
        var boundPrefix = prefix;
        var boundSuffix = suffix;
        assertThrows(ConcurrentModificationException.class, () -> boundPrefix.tryAdvance(e -> {}));
        assertThrows(ConcurrentModificationException.class, () -> boundSuffix.tryAdvance(e -> {}));
    }

    @Test
    void callbackChangesAreDetectedByBothTraversalMethods() {
        var list = StatementList(ExprStatement(IntLiteral(1)), ExprStatement(IntLiteral(2)));
        assertThrows(ConcurrentModificationException.class, () -> list.spliterator().tryAdvance(e -> list.clear()));
        list.add(ExprStatement(IntLiteral(3)));
        list.add(ExprStatement(IntLiteral(4)));
        assertThrows(ConcurrentModificationException.class, () -> list.spliterator().forEachRemaining(e -> list.clear()));
    }

    @Test
    void replacementsRemainVisibleWithoutAStructuralChange() {
        var list = StatementList(ExprStatement(IntLiteral(1)));
        var split = list.spliterator();
        assertEquals(1, split.estimateSize());
        var replacement = ExprStatement(IntLiteral(2));
        list.set(0, replacement);
        list.trimToSize();
        var seen = new ArrayList<TSStatement>();
        split.forEachRemaining(seen::add);
        assertEquals(List.of(replacement), seen);
    }
}
