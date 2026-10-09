package test.counted;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static test.counted.TC.*;

/**
 * Unit and Root count the modifications of themselves and of everything below them; a Block or an expression does not.
 * The count is how an analysis knows that the unit it saw is the unit it sees.
 */
class ModificationCountsTest {

    /** The counts of the two units and the root, to see what changed. */
    private record Counts(int first, int second, int root) {
        static Counts of(TCUnit first, TCUnit second, TCRoot root) {
            return new Counts(first.modificationCount(), second.modificationCount(), root.modificationCount());
        }
    }

    private final TCAssign assign = Assign("x", Add(Num(1), Var("y")));
    private final TCPrint print = Print(Var("x"));
    private final TCBranch branch = Branch(Var("c"), StmtList(Print(Num(1))), StmtList());
    private final TCUnit first = Unit("first", StmtList(assign, print, branch));
    private final TCUnit second = Unit("second", StmtList(Print(Num(2))));
    private final TCRoot root = Root(UnitList(first, second), "title");

    private Counts counts() {
        return Counts.of(first, second, root);
    }

    @Test
    void aNewTreeHasHadNoModification() {
        assertEquals(new Counts(0, 0, 0), counts());
    }

    @Test
    void readingModifiesNothing() {
        root.copy();
        root.copyWithRefs();
        root.structuralEquals(root.copy());
        root.toString();
        root.accept(new TCElement.DefaultVisitor() { });
        new TCElement.IterativeVisitor() { }.traverse(root);
        first.getBody().stream().toList();
        first.getBody().iterator().next();

        assertEquals(new Counts(0, 0, 0), counts());
    }

    @Test
    void aSetterBelowAUnitCountsForTheUnitAndTheRootOnly() {
        assign.setVariable("z");

        assertEquals(new Counts(1, 0, 1), counts());
    }

    @Test
    void aSetterOfTheUnitCountsForTheUnitAndTheRoot() {
        first.setName("renamed");
        second.setName("other");

        assertEquals(new Counts(1, 1, 2), counts());
    }

    @Test
    void aSetterOfTheRootCountsForTheRoot() {
        root.setTitle("other");

        assertEquals(new Counts(0, 0, 1), counts());
    }

    @Test
    void aReplacementDeepBelowAUnitCountsForIt() {
        TCAdd add = (TCAdd) assign.getValue();
        add.getLeft().replaceBy(Num(7));

        assertEquals(new Counts(1, 0, 1), counts());
    }

    @Test
    void aReplacementOfAStatementInAListCountsForTheUnit() {
        print.replaceBy(Print(Num(9)));

        assertEquals(new Counts(1, 0, 1), counts());
    }

    @Test
    void aChangeOfAChildCountsForTheUnitItIsIn() {
        assign.setValue(Num(5));

        assertEquals(new Counts(1, 0, 1), counts());
    }

    @Test
    void everyKindOfChangeToAListCounts() {
        TCStmtList body = first.getBody();
        int before = first.modificationCount();

        body.add(Print(Num(10)));
        assertTrue(first.modificationCount() > before, "add");
        before = first.modificationCount();

        body.add(0, Print(Num(11)));
        assertTrue(first.modificationCount() > before, "add at an index");
        before = first.modificationCount();

        body.addAll(List.of(Print(Num(12)), Print(Num(13))));
        assertTrue(first.modificationCount() > before, "addAll");
        before = first.modificationCount();

        body.set(0, Print(Num(14)));
        assertTrue(first.modificationCount() > before, "set");
        before = first.modificationCount();

        body.remove(0);
        assertTrue(first.modificationCount() > before, "remove at an index");
        before = first.modificationCount();

        body.remove(body.get(0));
        assertTrue(first.modificationCount() > before, "remove an element");
        before = first.modificationCount();

        body.removeIf(statement -> statement instanceof TCPrint);
        assertTrue(first.modificationCount() > before, "removeIf");
        before = first.modificationCount();

        body.add(Print(Num(15)));
        before = first.modificationCount();
        body.retainAll(List.of());
        assertTrue(first.modificationCount() > before, "retainAll");
        before = first.modificationCount();

        body.add(Print(Num(16)));
        body.sort(Comparator.comparing(statement -> statement.toString()));
        assertTrue(first.modificationCount() > before, "sort");
        before = first.modificationCount();

        body.clear();
        assertTrue(first.modificationCount() > before, "clear");

        assertEquals(second.modificationCount(), 0);
    }

    @Test
    void aReplacementByManyAndTheBulkReplacementCount() {
        var one = Print(Num(1));
        var other = Print(Num(2));
        var body = StmtList(one, other);
        var unit = Unit("u", body);
        assertEquals(0, unit.modificationCount());

        one.replaceByAll(List.of(Print(Num(3)), Print(Num(4))));
        assertEquals(1, unit.modificationCount());

        Map<TCStmt, List<TCStmt>> replacements = new IdentityHashMap<>();
        replacements.put(other, List.of());
        body.replaceEach(replacements);
        assertEquals(2, unit.modificationCount());
    }

    @Test
    void theIteratorAndASubListCount() {
        var body = first.getBody();
        int before = first.modificationCount();

        var iterator = body.listIterator();
        iterator.next();
        iterator.set(Print(Num(20)));
        assertTrue(first.modificationCount() > before, "iterator set");
        before = first.modificationCount();

        iterator.next();
        iterator.remove();
        assertTrue(first.modificationCount() > before, "iterator remove");
        before = first.modificationCount();

        iterator.add(Print(Num(21)));
        assertTrue(first.modificationCount() > before, "iterator add");
        before = first.modificationCount();

        body.subList(0, 1).add(Print(Num(22)));
        assertTrue(first.modificationCount() > before, "sub list add");
    }

    @Test
    void movingAStatementBetweenUnitsCountsForBoth() {
        var moved = print;
        first.getBody().remove(moved);
        assertEquals(new Counts(1, 0, 1), counts());

        second.getBody().add(moved);
        assertEquals(new Counts(1, 1, 2), counts());
    }

    @Test
    void aChangeBelowOneUnitDoesNotCountForTheOther() {
        for (int i = 0; i < 5; i++) {
            assign.setVariable("v" + i);
        }

        assertEquals(new Counts(5, 0, 5), counts());
    }

    @Test
    void aCopyStartsAtZeroAndDoesNotCountForTheOriginal() {
        first.setName("a");
        first.setName("b");
        var copy = first.copy();

        assertEquals(2, first.modificationCount());
        assertEquals(0, copy.modificationCount());
        assertEquals(2, first.modificationCount());

        var rootCopy = root.copyWithRefs();
        assertEquals(0, rootCopy.modificationCount());
        assertEquals(0, rootCopy.getUnits().get(0).modificationCount());
    }

    @Test
    void changingACopyDoesNotCountForTheOriginal() {
        var copy = first.copy();
        ((TCAssign) copy.getBody().get(0)).setVariable("changed");

        assertEquals(1, copy.modificationCount());
        assertEquals(new Counts(0, 0, 0), counts());
    }

    @Test
    void whatIsNotInATreeCountsNothingBelowIt() {
        // a statement which is in no unit: changing it is a change of nothing which counts
        var loose = Assign("a", Num(1));
        loose.setVariable("b");
        loose.setValue(Num(2));

        assertEquals(new Counts(0, 0, 0), counts());
    }

    @Test
    void aUnitBuiltOutsideTheTreeCountsAndPutIntoItCountsForTheRoot() {
        var fresh = Unit("fresh", StmtList());
        fresh.getBody().add(Print(Num(1)));
        assertEquals(1, fresh.modificationCount());

        root.getUnits().add(fresh);

        assertEquals(1, root.modificationCount());
        assertEquals(1, fresh.modificationCount());
    }

    @Test
    void theCountOfAnUnchangedUnitStaysTheSameWhateverHappensToTheOther() {
        int seen = second.modificationCount();

        first.getBody().add(Print(Num(1)));
        first.setName("again");
        root.getUnits().add(Unit("third", StmtList()));

        assertEquals(seen, second.modificationCount());
    }
}
