package test.mutation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static test.mutation.nested.NC.*;

class NestedCountsTest {
    @Test
    void recursiveContainmentThroughCasesAndListsCountsEveryCountedAncestor() {
        var leaf = Leaf(0);
        var inner = Unit(ChildList(leaf));
        var outer = Unit(ChildList(Nested(inner)));
        leaf.setNumber(1);
        assertEquals(1, inner.modificationCount());
        assertEquals(1, outer.modificationCount());
        inner.getChildren().add(Leaf(2));
        assertEquals(2, inner.modificationCount());
        assertEquals(2, outer.modificationCount());
    }
}
