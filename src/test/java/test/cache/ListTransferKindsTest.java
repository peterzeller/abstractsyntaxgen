package test.cache;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static test.cache.CP.*;

class ListTransferKindsTest {
    @Test
    void referenceListsCannotTransferOwnedChildren() {
        var node = Node(1);
        var owner = NodeList(node);
        var references = ReferenceList(node);
        var destination = ReferenceList();
        assertThrows(IllegalArgumentException.class, () -> destination.addAllMoved(references));
        assertThrows(IllegalArgumentException.class, () -> owner.addAllMoved(references));
        assertEquals(List.of(node), references.stream().toList());
        assertSame(owner, node.getParent());
        assertTrue(destination.isEmpty());
    }
}
