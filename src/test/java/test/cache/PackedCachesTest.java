package test.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static test.cache.CP.*;

class PackedCachesTest {
    private static void exercise(CPCached node) throws Exception {
        CacheAttributes.calls = 0;
        assertEquals(5, node.nested());
        assertEquals(6, CacheAttributes.calls);
        for (int pass = 0; pass < 2; pass++) {
            // Highest slots in a word, and the following word: unsigned reads and live state updates matter.
            for (int i = 69; i >= 0; i--) assertEquals(1, CPCached.class.getMethod("a" + i).invoke(node));
        }
        assertEquals(71, CacheAttributes.calls);
        assertFalse(node.circularTwice());
        assertFalse(node.circularTwice());
        assertEquals(72, CacheAttributes.calls);
        var object = node.object();
        assertSame(object, node.object());
        assertThrows(CyclicDependencyError.class, node::cycleA);
        assertEquals(1, node.a0()); // A cycle must not corrupt an unrelated cached state.
        var cache = node.getClass().getDeclaredField("zzattr_object_cache");
        cache.setAccessible(true);
        node.clearAttributesLocal();
        assertNull(cache.get(node));
        CacheAttributes.calls = 0;
        for (int i = 0; i < 70; i++) assertEquals(1, CPCached.class.getMethod("a" + i).invoke(node));
        assertEquals(70, CacheAttributes.calls);
        assertNotSame(object, node.object());
        assertFalse(node.circularTwice());
        assertThrows(CyclicDependencyError.class, node::cycleA);
    }

    @Test void inheritedNodeCachesSurviveNestedEvaluationAndClearing() throws Exception { exercise(Node(1)); }
    @Test void inheritedListCachesSurviveNestedEvaluationAndClearing() throws Exception { exercise(NodeList()); }

    @Test
    void smallAndMediumNodesKeepTheHighestSlotIndependent() throws Exception {
        for (CPElement node : new CPElement[]{Small(), Medium()}) {
            CacheAttributes.calls = 0;
            int count = node instanceof CPSmall ? 8 : 16;
            for (int pass = 0; pass < 2; pass++) {
                for (int i = count - 1; i >= 0; i--) {
                    var method = (node instanceof CPSmall ? CPSmall.class : CPMedium.class).getMethod("s" + i);
                    assertEquals(1, method.invoke(node));
                }
            }
            assertEquals(count, CacheAttributes.calls);
            node.clearAttributesLocal();
            var method = (node instanceof CPSmall ? CPSmall.class : CPMedium.class).getMethod("s" + (count - 1));
            assertEquals(1, method.invoke(node));
            assertEquals(count + 1, CacheAttributes.calls);
        }
    }

    @Test
    void copiesStartWithUncomputedCacheStates() {
        var node = Node(1);
        node.a31(); node.a32(); node.a63(); node.a64();
        CacheAttributes.calls = 0;
        var copy = node.copy();
        var withRefs = node.copyWithRefs();
        copy.a31(); copy.a32(); copy.a63(); copy.a64();
        withRefs.a31(); withRefs.a32(); withRefs.a63(); withRefs.a64();
        assertEquals(8, CacheAttributes.calls);
    }
}
