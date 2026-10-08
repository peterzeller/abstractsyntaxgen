package test.stmt;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static test.stmt.TS.*;

class ListSplicingTest {

    private static TSStatement statement(int value) {
        return ExprStatement(IntLiteral(value));
    }

    private static int valueOf(TSStatement statement) {
        return ((TSIntLiteral) ((TSExprStatement) statement).getExpression()).getIntValue();
    }

    private static List<Integer> values(TSStatementList list) {
        return list.stream().map(ListSplicingTest::valueOf).toList();
    }

    private static void assertAllAttachedTo(TSStatementList list) {
        for (TSStatement statement : list) {
            assertSame(list, statement.getParent());
        }
    }

    @Test
    void replaceByAllPutsManyElementsInThePlaceOfOne() {
        var removed = statement(2);
        var list = StatementList(statement(1), removed, statement(3));

        removed.replaceByAll(List.of(statement(20), statement(21), statement(22)));

        assertEquals(List.of(1, 20, 21, 22, 3), values(list));
        assertNull(removed.getParent());
        assertAllAttachedTo(list);
    }

    @Test
    void replaceByAllWithNoElementsRemovesTheElement() {
        var removed = statement(2);
        var list = StatementList(statement(1), removed, statement(3));

        removed.replaceByAll(List.of());

        assertEquals(List.of(1, 3), values(list));
        assertNull(removed.getParent());
    }

    @Test
    void replaceByAllWithOneElementIsReplaceBy() {
        var replaced = statement(2);
        var list = StatementList(statement(1), replaced, statement(3));

        replaced.replaceByAll(List.of(statement(20)));

        assertEquals(List.of(1, 20, 3), values(list));
        assertNull(replaced.getParent());
    }

    @Test
    void replacingAfterASpliceStillFindsTheElementsBehindIt() {
        var list = StatementList();
        var statements = new ArrayList<TSStatement>();
        for (int i = 0; i < 10; i++) {
            statements.add(statement(i));
            list.add(statements.get(i));
        }

        statements.get(2).replaceByAll(List.of(statement(100), statement(101), statement(102)));
        statements.get(8).replaceBy(statement(800));
        statements.get(0).replaceByAll(List.of());

        assertEquals(List.of(1, 100, 101, 102, 3, 4, 5, 6, 7, 800, 9), values(list));
        assertAllAttachedTo(list);
    }

    @Test
    void anElementWhichIsNotInAListIsReplacedByExactlyOneElement() {
        var condition = BoolLiteral(true);
        var statement = IfStatement(condition, StatementList(), StatementList());

        assertThrows(RuntimeException.class, () -> condition.replaceByAll(List.of(BoolLiteral(false), BoolLiteral(false))));
        assertSame(condition, statement.getCondition());
        assertSame(statement, condition.getParent());

        var other = BoolLiteral(false);
        condition.replaceByAll(List.of(other));
        assertSame(other, statement.getCondition());
        assertNull(condition.getParent());
    }

    @Test
    void replaceByAllChangesNothingWhenAnElementIsInATreeAlready() {
        var first = statement(1);
        var second = statement(2);
        var list = StatementList(first, second);
        var fresh = statement(9);
        var owned = statement(7);
        var owner = StatementList(owned);

        assertThrows(Error.class, () -> first.replaceByAll(List.of(fresh, owned)));

        assertEquals(List.of(1, 2), values(list));
        assertSame(list, first.getParent());
        assertNull(fresh.getParent());
        assertSame(owner, owned.getParent());
    }

    @Test
    void replaceEachReplacesInOnePassAndKeepsTheOrderOfTheRest() {
        var statements = new ArrayList<TSStatement>();
        var list = StatementList();
        for (int i = 0; i < 10; i++) {
            statements.add(statement(i));
            list.add(statements.get(i));
        }
        Map<TSStatement, List<TSStatement>> replacements = new IdentityHashMap<>();
        replacements.put(statements.get(2), List.of(statement(20), statement(21)));
        replacements.put(statements.get(5), List.of());
        replacements.put(statements.get(9), List.of(statement(90)));

        int replaced = list.replaceEach(replacements);

        assertEquals(3, replaced);
        assertEquals(List.of(0, 1, 20, 21, 3, 4, 6, 7, 8, 90), values(list));
        assertAllAttachedTo(list);
        assertNull(statements.get(2).getParent());
        assertNull(statements.get(5).getParent());
        assertNull(statements.get(9).getParent());
        assertSame(list, statements.get(3).getParent());
    }

    @Test
    void replaceEachWithNothingToReplaceChangesNothing() {
        var kept = statement(1);
        var list = StatementList(kept, statement(2));
        Map<TSStatement, List<TSStatement>> replacements = new IdentityHashMap<>();
        replacements.put(statement(3), List.of(statement(30)));

        assertEquals(0, list.replaceEach(replacements));
        assertEquals(0, list.replaceEach(Map.of()));

        assertEquals(List.of(1, 2), values(list));
        assertSame(list, kept.getParent());
    }

    @Test
    void replaceEachUsesTheLookupOfTheMapItIsGiven() {
        // a map which compares by identity does not replace an element which is equal to a key by another test
        var first = statement(1);
        var list = StatementList(first, statement(1));
        Map<TSStatement, List<TSStatement>> replacements = new IdentityHashMap<>();
        replacements.put(first, List.of(statement(10)));

        assertEquals(1, list.replaceEach(replacements));

        assertEquals(List.of(10, 1), values(list));
    }

    @Test
    void replaceEachChangesNothingWhenAnElementIsInATreeAlready() {
        var second = statement(2);
        var fifth = statement(5);
        var list = StatementList(statement(1), second, statement(3), statement(4), fifth);
        var fresh = statement(20);
        var owned = statement(50);
        var owner = StatementList(owned);
        Map<TSStatement, List<TSStatement>> replacements = new IdentityHashMap<>();
        replacements.put(second, List.of(fresh));
        replacements.put(fifth, List.of(owned));

        assertThrows(Error.class, () -> list.replaceEach(replacements));

        assertEquals(List.of(1, 2, 3, 4, 5), values(list));
        assertSame(list, second.getParent());
        assertSame(list, fifth.getParent());
        assertNull(fresh.getParent());
        assertSame(owner, owned.getParent());
    }

    @Test
    void replaceEachTakesOnePassOverALargeList() {
        int size = 200_000;
        var list = StatementList();
        Map<TSStatement, List<TSStatement>> replacements = new IdentityHashMap<>();
        for (int i = 0; i < size; i++) {
            var statement = statement(i);
            list.add(statement);
            if (i % 2 == 0) {
                replacements.put(statement, List.of(statement(-i), statement(-i - 1)));
            }
        }

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertEquals(size / 2, list.replaceEach(replacements)));

        assertEquals(size / 2 * 3, list.size());
        // 0 becomes (0, -1), 1 stays, 2 becomes (-2, -3), 3 stays
        assertEquals(List.of(0, -1, 1, -2, -3, 3),
            List.of(valueOf(list.get(0)), valueOf(list.get(1)), valueOf(list.get(2)),
                valueOf(list.get(3)), valueOf(list.get(4)), valueOf(list.get(5))));
        assertAllAttachedTo(list);
    }
}
