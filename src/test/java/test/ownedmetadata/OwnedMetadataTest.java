package test.ownedmetadata;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OwnedMetadataTest {
    private static OMRoot root() {
        return OM.Root(new ArrayList<>(List.of("b", "a")), new LinkedHashMap<>(), new LinkedHashSet<>(List.of("x", "y")));
    }
    private static void changes(OMRoot root, Runnable action) {
        int before = root.modificationCount();
        action.run();
        assertNotEquals(before, root.modificationCount(), "owned metadata mutation must notify its owner");
    }
    @Test void listsIteratorsAndViewsNotifyTheirOwner() {
        var root = root();
        changes(root, () -> root.getNames().add("c"));
        changes(root, () -> root.getNames().listIterator().add("first"));
        changes(root, () -> root.getNames().subList(0, 1).clear());
        changes(root, () -> root.getNames().replaceAll(String::toUpperCase));
        changes(root, () -> root.getNames().sort(String::compareTo));
        changes(root, () -> root.getNames().removeIf(v -> v.equals("C")));
    }
    @Test void incomingContainersAreCopiedDeeply() {
        var names = new ArrayList<>(List.of("before"));
        var values = new ArrayList<>(List.of("nested"));
        var groups = new LinkedHashMap<String, List<String>>();
        groups.put("a", values);
        var tags = new LinkedHashSet<>(List.of("tag"));
        var root = OM.Root(names, groups, tags);
        int before = root.modificationCount();
        names.clear(); values.clear(); groups.clear(); tags.clear();
        assertEquals(List.of("before"), root.getNames());
        assertEquals(List.of("nested"), root.getGroups().get("a"));
        assertEquals(Set.of("tag"), root.getTags());
        assertEquals(before, root.modificationCount());
    }
    @Test void nestedMapValuesAndEntryMutationsAreTracked() {
        var root = root();
        changes(root, () -> root.getGroups().put("a", new ArrayList<>(List.of("v"))));
        changes(root, () -> root.getGroups().get("a").add("next"));
        changes(root, () -> root.getGroups().entrySet().iterator().next().setValue(new ArrayList<>(List.of("replaced"))));
        changes(root, () -> root.getGroups().values().iterator().next().clear());
        changes(root, () -> root.getGroups().keySet().remove("a"));
    }
    @Test void computeAndMergeReturnTheOwnedValue() {
        var root = root();
        var list = root.getGroups().computeIfAbsent("a", k -> new ArrayList<>());
        assertSame(list, root.getGroups().get("a"));
        changes(root, () -> list.add("a"));
        var computed = root.getGroups().compute("b", (k, old) -> new ArrayList<>(List.of("b")));
        assertSame(computed, root.getGroups().get("b"));
        changes(root, () -> computed.add("next"));
        var merged = root.getGroups().merge("c", new ArrayList<>(List.of("c")), (a, b) -> b);
        assertSame(merged, root.getGroups().get("c"));
        changes(root, () -> merged.add("next"));
    }
    @Test void setsAndTheirIteratorsAreTracked() {
        var root = root();
        changes(root, () -> root.getTags().add("z"));
        var iterator = root.getTags().iterator(); iterator.next();
        changes(root, iterator::remove);
        changes(root, () -> root.getTags().removeIf(v -> v.equals("y")));
    }
    @Test void replacedAndRemovedViewsStopNotifyingTheOwner() {
        var root = root();
        var previous = root.getNames();
        root.setNames(new ArrayList<>(List.of("replacement")));
        int before = root.modificationCount(); previous.add("orphan");
        assertEquals(before, root.modificationCount());
        assertEquals(List.of("replacement"), root.getNames());
        root.getGroups().put("a", new ArrayList<>(List.of("value")));
        var removed = root.getGroups().remove("a");
        before = root.modificationCount(); removed.add("orphan");
        assertEquals(before, root.modificationCount());
    }
    @Test void aliasesArePreservedInsideOneOwnerAndDetachedOnlyAfterTheLastEdge() {
        var shared = new ArrayList<>(List.of("shared"));
        var groups = new LinkedHashMap<String, List<String>>(); groups.put("a", shared); groups.put("b", shared);
        var root = OM.Root(new ArrayList<>(), groups, new LinkedHashSet<>());
        var value = root.getGroups().get("a");
        assertSame(value, root.getGroups().get("b"));
        root.getGroups().remove("a");
        changes(root, () -> value.add("still owned"));
        root.getGroups().remove("b");
        int before = root.modificationCount(); value.add("detached");
        assertEquals(before, root.modificationCount());
    }
    @Test void copyingValuesBetweenOwnersDoesNotShareMutableMetadata() {
        var first = root(); var second = root();
        second.setNames(first.getNames());
        int before = first.modificationCount();
        changes(second, () -> second.getNames().add("second"));
        assertEquals(before, first.modificationCount());
        assertNotEquals(first.getNames(), second.getNames());
    }
    @Test void persistentMetadataFieldsUseTheSameContract() {
        var root = root(); var incoming = new ArrayList<>(List.of("event"));
        root.setHistory(incoming); incoming.clear();
        assertEquals(List.of("event"), root.getHistory());
        changes(root, () -> root.getHistory().add("next"));
        root.setHistory(null); assertNull(root.getHistory());
    }
    @Test void aliasesAcrossFieldsOfOneNodeArePreserved() {
        var shared = new ArrayList<>(List.of("shared"));
        var groups = new LinkedHashMap<String,List<String>>(); groups.put("same", shared);
        var root = OM.Root(shared, groups, new LinkedHashSet<>());
        assertSame(root.getNames(), root.getGroups().get("same"));
        root.setHistory(root.getNames());
        assertSame(root.getNames(), root.getHistory());
        var alias = root.getNames();
        root.setNames(new ArrayList<>()); root.getGroups().remove("same");
        changes(root, () -> alias.add("still in history"));
        root.setHistory(null);
        int before = root.modificationCount(); alias.add("fully detached");
        assertEquals(before, root.modificationCount());
    }
    @Test void copyVariantsHavePrivateMetadataAndFreshListeners() {
        var root = root(); root.setHistory(root.getNames());
        for (OMRoot copy : List.of(root.copy(), root.copyWithRefs())) {
            assertNotSame(root.getNames(), copy.getNames());
            assertSame(copy.getNames(), copy.getHistory());
            int before = root.modificationCount();
            changes(copy, () -> copy.getNames().add("copy only"));
            assertEquals(before, root.modificationCount());
        }
    }
    @Test void deeplyNestedMetadataIsCopiedAndTrackedWithoutRecursion() {
        List<Object> nested = new ArrayList<>();
        for (int i=0;i<10000;i++) nested = new ArrayList<>(List.of(nested));
        var root = OM.Raw(nested);
        var expected=root.copyWithRefs();
        for (OMRaw copy : List.of(root, root.copy(), root.copyWithRefs())) {
            assertTrue(copy.structuralEquals(expected));
            List<?> current = copy.getValues();
            for(int i=0;i<10000;i++) current=(List<?>)current.get(0);
            int before=copy.modificationCount(); current.add(null);
            assertNotEquals(before,copy.modificationCount());
        }
    }
    @Test void invalidatedEntryViewsFailBeforeTheyCanTouchMetadata() {
        var root=root(); root.getGroups().put("a",new ArrayList<>(List.of("value")));
        var entry=root.getGroups().entrySet().iterator().next();
        root.getGroups().remove("a"); int before=root.modificationCount();
        assertThrows(ConcurrentModificationException.class,()->entry.setValue(new ArrayList<>()));
        assertEquals(before,root.modificationCount()); assertTrue(root.getGroups().isEmpty());
    }
    @Test void changesFollowOwnershipAndNeverPropagateThroughReferenceLeaves() {
        var leaf=OM.Leaf(new ArrayList<>(List.of("leaf")));
        var owner=OM.Outer(leaf);
        var observer=OM.ReferenceOwner(new ArrayList<>(List.of(leaf)));
        assertSame(owner,leaf.getParent());
        int beforeOwner=owner.modificationCount(), beforeObserver=observer.modificationCount();
        leaf.getData().add("changed");
        assertNotEquals(beforeOwner,owner.modificationCount());
        assertEquals(beforeObserver,observer.modificationCount());
        beforeOwner=owner.modificationCount(); observer.getTargets().clear();
        assertEquals(beforeOwner,owner.modificationCount()); assertSame(owner,leaf.getParent());
    }
    @Test void sortedInputsKeepTheirOrderingAfterLaterEdits() {
        var groups=new TreeMap<String,List<String>>(Comparator.reverseOrder()); groups.put("b",new ArrayList<>());
        var tags=new TreeSet<String>(Comparator.reverseOrder()); tags.add("b");
        var root=OM.Root(new ArrayList<>(),groups,tags);
        root.getGroups().put("c",new ArrayList<>());root.getGroups().put("a",new ArrayList<>());
        root.getTags().add("c");root.getTags().add("a");
        assertEquals(List.of("c","b","a"),new ArrayList<>(root.getGroups().keySet()));
        assertEquals(List.of("c","b","a"),new ArrayList<>(root.getTags()));
    }
    @Test void sourceOwnedListsPropagateTheSourceDomainForStructuralAndNestedEdits() {
        var root=OM.SourceLists(OM.Nodes(),OM.Nodes());
        int semantic=root.semanticModificationCount(),source=root.sourceModificationCount();
        root.getOriginNodes().add(OM.Leaf(new ArrayList<>()));
        assertEquals(semantic,root.semanticModificationCount());assertNotEquals(source,root.sourceModificationCount());
        source=root.sourceModificationCount();root.getOriginNodes().get(0).getData().add("source");
        assertEquals(semantic,root.semanticModificationCount());assertNotEquals(source,root.sourceModificationCount());
        source=root.sourceModificationCount();root.getNodes().add(OM.Leaf(new ArrayList<>()));
        assertNotEquals(semantic,root.semanticModificationCount());assertEquals(source,root.sourceModificationCount());
    }
    @Test void revisionsDistinguishSourceAndSemanticsAcrossOwnedEdgesAndMetadataAliases() {
        var shared=new ArrayList<>(List.of("both"));
        var node=OM.Revisions(OM.Leaf(new ArrayList<>()), OM.Leaf(new ArrayList<>()),shared,shared);
        int semantic=node.semanticModificationCount(), source=node.sourceModificationCount();
        node.getPosition().getData().add("line shift");
        assertEquals(semantic,node.semanticModificationCount());
        assertNotEquals(source,node.sourceModificationCount());
        source=node.sourceModificationCount();
        node.getBody().getData().add("body edit");
        assertNotEquals(semantic,node.semanticModificationCount());
        assertEquals(source,node.sourceModificationCount());
        semantic=node.semanticModificationCount();
        node.getLines().add("shared metadata");
        assertNotEquals(semantic,node.semanticModificationCount());
        assertNotEquals(source,node.sourceModificationCount());
        node.setFacts(new ArrayList<>());
        semantic=node.semanticModificationCount(); source=node.sourceModificationCount();
        node.getLines().add("source only");
        assertEquals(semantic,node.semanticModificationCount());
        assertNotEquals(source,node.sourceModificationCount());
        var copy=node.copyWithRefs();
        assertEquals(0,copy.modificationCount());assertEquals(0,copy.semanticModificationCount());assertEquals(0,copy.sourceModificationCount());
    }
    @Test void cyclicContainersFailBeforeTheyCanChangeTheOwner() {
        var cyclic = new ArrayList<Object>(); cyclic.add(cyclic);
        assertThrows(IllegalArgumentException.class, () -> OM.Raw(cyclic));
        var raw = OM.Raw(new ArrayList<>());
        int before = raw.modificationCount();
        assertThrows(IllegalArgumentException.class, () -> raw.getValues().add(raw.getValues()));
        assertEquals(before, raw.modificationCount()); assertTrue(raw.getValues().isEmpty());
    }
}
