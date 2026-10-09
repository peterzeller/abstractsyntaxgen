package test.stmt;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ConcurrentModificationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static test.stmt.TS.*;

class ListTransferTest {
    @Test
    void anEmptyDestinationTakesTheChildrenAndInvalidatesBothIterators() {
        var first = ExprStatement(IntLiteral(1));
        var second = ExprStatement(IntLiteral(2));
        var source = StatementList(first, second);
        var destination = StatementList();
        var sourceIterator = source.iterator();
        var destinationIterator = destination.iterator();
        assertTrue(destination.addAllMoved(source));
        assertTrue(source.isEmpty());
        assertEquals(List.of(first, second), destination.stream().toList());
        assertSame(destination, first.getParent());
        assertSame(destination, second.getParent());
        assertThrows(ConcurrentModificationException.class, sourceIterator::next);
        assertThrows(ConcurrentModificationException.class, destinationIterator::next);
        source.add(ExprStatement(IntLiteral(3)));
        assertEquals(2, destination.size());
        destination.get(0).replaceBy(ExprStatement(IntLiteral(4)));
    }

    @Test
    void indexedTransferPreservesThePrefixAndSuffix() {
        var a = ExprStatement(IntLiteral(1));
        var b = ExprStatement(IntLiteral(2));
        var c = ExprStatement(IntLiteral(3));
        var d = ExprStatement(IntLiteral(4));
        var source = StatementList(b, c);
        var destination = StatementList(a, d);
        destination.addAllMoved(1, source);
        assertEquals(List.of(a, b, c, d), destination.stream().toList());
        destination.forEach(e -> assertSame(destination, e.getParent()));
        assertTrue(source.isEmpty());
    }

    @Test
    void emptyTransfersDoNotInvalidateIteratorsAndBadInputsDoNotMoveChildren() {
        var destination = StatementList(ExprStatement(IntLiteral(1)));
        var iterator = destination.iterator();
        assertFalse(destination.addAllMoved(StatementList()));
        assertSame(destination.get(0), iterator.next());
        assertThrows(IllegalArgumentException.class, () -> destination.addAllMoved(destination));
        var source = StatementList(ExprStatement(IntLiteral(2)));
        assertThrows(IndexOutOfBoundsException.class, () -> destination.addAllMoved(2, source));
        assertThrows(NullPointerException.class, () -> destination.addAllMoved(null));
        assertSame(source, source.get(0).getParent());
        assertEquals(1, destination.size());
    }

    @Test
    void movingAnAncestorIntoItsDescendantIsRejected() {
        var inner = StatementList();
        var block = Block(inner);
        var source = StatementList(block);
        assertThrows(IllegalArgumentException.class, () -> inner.addAllMoved(source));
        assertSame(source, block.getParent());
        assertSame(block, inner.getParent());
        assertTrue(inner.isEmpty());
        assertEquals(1, source.size());
    }

    @Test
    void anAttachmentFailureRestoresAllParentsAndListContents() {
        var destination = StatementList(ExprStatement(IntLiteral(9)));
        var delegate = ExprStatement(IntLiteral(2));
        var rejected = (TSExprStatement) Proxy.newProxyInstance(TSExprStatement.class.getClassLoader(),
            new Class<?>[]{TSExprStatement.class}, (proxy, method, args) -> {
                if (method.getName().equals("setParent") && args[0] == destination) throw new Error("Rejected attachment");
                if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                try { return method.invoke(delegate, args); }
                catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
        var first = ExprStatement(IntLiteral(1));
        var source = StatementList(first, rejected);
        var sourceIterator = source.iterator();
        var destinationIterator = destination.iterator();
        assertThrows(Error.class, () -> destination.addAllMoved(source));
        assertEquals(List.of(first, rejected), source.stream().toList());
        assertSame(source, first.getParent());
        assertSame(source, rejected.getParent());
        assertSame(first, sourceIterator.next());
        assertSame(destination.get(0), destinationIterator.next());
        assertEquals(1, destination.size());
    }
}
