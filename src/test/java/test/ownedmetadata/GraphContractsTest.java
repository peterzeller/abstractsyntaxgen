package test.ownedmetadata;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphContractsTest {
    record Choice(boolean left,Object value) { }
    private static final OMNodeGraphs.Adapter CHOICE=new OMNodeGraphs.Adapter() {
        public OMNodeGraphs.Parts decompose(Object value) {
            return value instanceof Choice choice ? new OMNodeGraphs.Parts(choice.left() ? "left" : "right",List.of(choice.value())) : null;
        }
        public Object compose(String tag,List<Object> values) { return new Choice(tag.equals("left"),values.get(0)); }
    };
    private static OMGraph graph() {
        var first=OM.Leaf(new ArrayList<>(List.of("a")));var second=OM.Leaf(new ArrayList<>(List.of("b")));
        var map=new LinkedHashMap<OMLeaf,List<OMLeaf>>();map.put(first,new ArrayList<>(List.of(second)));
        var graph=OM.Graph(OM.Nodes(first,second),map,new Choice(true,first));
        graph.setMemo(new ArrayList<>(List.of(second)));return graph;
    }
    @Test void legacyCopyWithRefsRepairsStandardNestedReferencesWithoutSharingMetadata() {
        var graph=graph();var copy=graph.copyWithRefs();
        assertSame(copy.getNodes().get(1),copy.getLinks().get(copy.getNodes().get(0)).get(0));
        assertSame(copy.getNodes().get(1),copy.getMemo().get(0));
        assertSame(copy.getNodes().get(0),((Choice)copy.getVariant()).value());
        copy.getMemo().clear();assertEquals(1,graph.getMemo().size());
    }
    @Test void materializationPreservesMetadataAliasesAndSortedContainerContracts() {
        var shared=new ArrayList<>(List.of("shared"));
        var groups=new TreeMap<String,List<String>>();groups.put("b",shared);
        var root=OM.Root(shared,groups,new TreeSet<>(List.of("b")));root.setHistory(root.getNames());
        var snapshot=OMNodeGraphs.freeze(root);var copy=(OMRoot)snapshot.materialize();
        assertSame(copy.getNames(),copy.getGroups().get("b"));assertSame(copy.getNames(),copy.getHistory());
        copy.getGroups().put("a",new ArrayList<>());copy.getTags().add("a");
        assertEquals(List.of("a","b"),new ArrayList<>(copy.getGroups().keySet()));
        assertEquals(List.of("a","b"),new ArrayList<>(copy.getTags()));
        int before=copy.modificationCount();copy.getHistory().add("changed");assertNotEquals(before,copy.modificationCount());
        assertEquals(List.of("shared"),root.getNames());
    }
    @Test void adaptersCannotHideMutableValuesInFrozenSymbolsAndContainerCyclesAreRejected() {
        var cyclic=new ArrayList<Object>();cyclic.add(cyclic);
        var graph=OM.Graph(OM.Nodes(),new LinkedHashMap<>(),cyclic);
        assertThrows(IllegalArgumentException.class,()->OMNodeGraphs.freeze(graph));
        assertThrows(IllegalArgumentException.class,()->new OMNodeGraphs.MetadataMapper(n->n,OMNodeGraphs.Adapter.DEFAULT).map(cyclic));
    }
    @Test void legacyReferenceListsKeepExternalTargetsAndRepairOnlyOwnedSiblings() {
        var internal=OM.Leaf(new ArrayList<>());var external=OM.Leaf(new ArrayList<>());
        var observer=OM.WithRefs(OM.Nodes(internal),OM.References(internal,external));
        var bindings=new LinkedHashMap<OMLeaf,OMLeaf>();bindings.put(internal,external);observer.getReferences().setBindings(bindings);
        var copy=observer.copyWithRefs();
        assertSame(copy.getNodes().get(0),copy.getReferences().get(0));
        assertSame(external,copy.getReferences().get(1));assertNull(external.getParent());
        assertSame(external,copy.getReferences().getBindings().get(copy.getNodes().get(0)));
        copy.getReferences().getBindings().clear();assertEquals(1,observer.getReferences().getBindings().size());
        var references=observer.getReferences().copy();assertSame(internal,references.get(0));
    }
    @Test void frozenSemanticComparisonIgnoresOnlySourceFieldsAndNotBodyChanges() {
        var first=OM.Revisions(OM.Leaf(new ArrayList<>(List.of("body"))),OM.Leaf(new ArrayList<>()),new ArrayList<>(),new ArrayList<>());
        var before=OMNodeGraphs.freeze(first);
        first.getPosition().getData().add("position shifted");first.getLines().add("new location");
        var relocated=OMNodeGraphs.freeze(first);
        assertNotEquals(before,relocated);assertTrue(before.sameSemantics(relocated));
        first.getBody().getData().add("body edit");assertFalse(before.sameSemantics(OMNodeGraphs.freeze(first)));
        var a=OM.Leaf(new ArrayList<>(List.of(new String("same"),new String("same"))));
        var b=OM.Leaf(new ArrayList<>(List.of("same","same")));
        assertEquals(OMNodeGraphs.freeze(a),OMNodeGraphs.freeze(b));
    }
    @Test void externalReferenceSlotsStayOpaqueAndIncludeTheirSchemaField() {
        var target=new ArrayList<>(List.of(OM.Leaf(new ArrayList<>())));var external=OM.External(target);
        List<String> fields=new ArrayList<>();List<Object> values=new ArrayList<>();
        OMNodeGraphs.referenceSlots(external,(field,value)->{fields.add(field);values.add(value);},OMNodeGraphs.Adapter.DEFAULT);
        assertEquals(List.of("OMExternal.externalTarget"),fields);assertSame(target,values.get(0));
    }
    @Test void nestedReferencesAreVisitedWithoutFollowingTheirTargets() {
        var graph=graph();List<OMElement> references=new ArrayList<>();
        OMNodeGraphs.references(graph,references::add,CHOICE);
        assertEquals(4,references.size());
        assertEquals(2,references.stream().filter(n->n==graph.getNodes().get(0)).count());
        assertEquals(2,references.stream().filter(n->n==graph.getNodes().get(1)).count());
    }
    @Test void schemaCopyRepairsNestedMapKeysValuesVariantsAndPersistentFields() {
        var graph=graph();var copy=(OMGraph)OMNodeGraphs.copy(graph,CHOICE);
        assertNotSame(graph.getNodes().get(0),copy.getNodes().get(0));
        assertSame(copy.getNodes().get(1),copy.getLinks().get(copy.getNodes().get(0)).get(0));
        assertSame(copy.getNodes().get(0),((Choice)copy.getVariant()).value());
        assertSame(copy.getNodes().get(1),copy.getMemo().get(0));
        copy.getLinks().clear();assertEquals(1,graph.getLinks().size());
        assertEquals(0,copy.getNodes().get(0).modificationCount());
    }
    @Test void frozenSnapshotsRetainNeitherMutableContainersNorNodes() {
        var graph=graph();var frozen=OMNodeGraphs.freeze(graph,CHOICE);
        assertEquals(OMNodeCodec.SCHEMA,frozen.schema());
        assertThrows(UnsupportedOperationException.class,()->frozen.nodes().clear());
        graph.getNodes().get(0).getData().clear();graph.getLinks().clear();graph.getMemo().clear();
        var first=(OMGraph)frozen.materialize(CHOICE);var second=(OMGraph)frozen.materialize(CHOICE);
        assertEquals(List.of("a"),first.getNodes().get(0).getData());
        assertSame(first.getNodes().get(1),first.getLinks().get(first.getNodes().get(0)).get(0));
        assertSame(first.getNodes().get(0),((Choice)first.getVariant()).value());
        assertNotSame(first.getNodes().get(0),second.getNodes().get(0));
        first.getNodes().get(0).getData().clear();assertEquals(List.of("a"),second.getNodes().get(0).getData());
    }
    @Test void snapshotsAndPrivateCopiesReconstructMutualAndSelfReferences() {
        var first=OM.Recursive(null,1);var second=OM.Recursive(first,2);first.setTarget(second);
        var pair=OM.Pair(first,second);
        for(var copy:List.of((OMPair)OMNodeGraphs.freeze(pair).materialize(),(OMPair)OMNodeGraphs.copy(pair,OMNodeGraphs.Adapter.DEFAULT))) {
            assertSame(copy.getSecond(),copy.getFirst().getTarget());assertSame(copy.getFirst(),copy.getSecond().getTarget());
        }
        first.setTarget(first);var restored=(OMPair)OMNodeGraphs.freeze(pair).materialize();
        assertSame(restored.getFirst(),restored.getFirst().getTarget());
    }
    @Test void referenceListsResolveToSymbolsWithoutDiscoveringOrCloningTheirTargets() {
        var leaf=OM.Leaf(new ArrayList<>(List.of("external")));var owner=OM.Outer(leaf);var refs=OM.References(leaf);
        assertThrows(IllegalArgumentException.class,()->OMNodeGraphs.freeze(refs));
        var adapter=new OMNodeGraphs.Adapter() {
            public OMNodeGraphs.Symbol symbol(String field,Object value) { assertSame(leaf,value);return new OMNodeGraphs.Symbol("leaf","one"); }
            public Object resolve(OMNodeGraphs.Symbol symbol) { assertEquals("one",symbol.key());return leaf; }
        };
        var restored=(OMReferences)OMNodeGraphs.freeze(refs,adapter).materialize(adapter);
        assertSame(leaf,restored.get(0));assertSame(owner,leaf.getParent());
        var copy=(OMReferences)OMNodeGraphs.copy(refs,OMNodeGraphs.Adapter.DEFAULT);assertSame(leaf,copy.get(0));
    }
    @Test void unknownMutableMetadataRequiresAnExplicitDetachedAdapter() {
        var input=new StringBuilder("before");var external=OM.External(input);
        var failure=assertThrows(IllegalArgumentException.class,()->OMNodeGraphs.freeze(external));assertTrue(failure.getMessage().contains("OMExternal.externalTarget"));
        var adapter=new OMNodeGraphs.Adapter() {
            public OMNodeGraphs.Symbol symbol(String field,Object value) { return new OMNodeGraphs.Symbol("text",value.toString()); }
            public Object resolve(OMNodeGraphs.Symbol symbol) { return new StringBuilder(symbol.key()); }
        };
        var snapshot=OMNodeGraphs.freeze(external,adapter);input.append(" changed");
        assertEquals("before",((OMExternal)snapshot.materialize(adapter)).getExternalTarget().toString());
    }
    @Test void deepOwnedTreesAndMetadataDoNotUseTheJavaStack() {
        OMTree tree=OM.Tip();for(int i=0;i<10000;i++) tree=OM.Branch(tree);
        var copy=OMNodeGraphs.freeze(tree).materialize();int depth=0;
        while(copy instanceof OMBranch branch) { depth++;copy=branch.getSubtree(); }
        assertEquals(10000,depth);
        List<Object> nested=new ArrayList<>();for(int i=0;i<10000;i++) nested=new ArrayList<>(List.of(nested));
        var raw=(OMRaw)OMNodeGraphs.freeze(OM.Raw(nested)).materialize();
        List<?> current=raw.getValues();for(int i=0;i<10000;i++) current=(List<?>)current.get(0);assertTrue(current.isEmpty());
    }
    @Test void remappingCollisionsAreDiagnosedBeforeOverwritingMapBindings() {
        var a=OM.Leaf(new ArrayList<>());var b=OM.Leaf(new ArrayList<>());
        var map=new LinkedHashMap<OMLeaf,Integer>();map.put(a,1);map.put(b,2);
        var mapper=new OMNodeGraphs.MetadataMapper(leaf->a,OMNodeGraphs.Adapter.DEFAULT);
        assertThrows(IllegalArgumentException.class,()->mapper.map(map));assertEquals(2,map.size());
    }
    @Test void rejectedChildAttachmentsLeaveExistingParentsAndBodiesUntouched() {
        var first=OM.Branch(OM.Tip());var second=OM.Branch(OM.Tip());var previous=first.getSubtree();
        assertThrows(Error.class,()->first.setSubtree(second.getSubtree()));
        assertSame(previous,first.getSubtree());assertSame(first,previous.getParent());
        assertThrows(IllegalArgumentException.class,()->first.setSubtree(first));assertSame(previous,first.getSubtree());
        var node=OM.Recursive(null,0);assertThrows(IllegalArgumentException.class,()->OM.Pair(node,node));assertNull(node.getParent());
    }
    @Test void mutationScopesRejectForeignSettersMetadataViewsAndListWritesBeforeChanges() {
        var graph=graph();var foreign=graph();
        try(var scope=OMNodeOwnership.scope(graph)) {
            graph.getNodes().get(0).getData().add("allowed");
            assertThrows(IllegalStateException.class,()->foreign.getNodes().get(0).setData(new ArrayList<>()));
            assertThrows(IllegalStateException.class,()->foreign.getNodes().get(0).getData().clear());
            assertThrows(IllegalStateException.class,()->foreign.getNodes().clear());
            assertThrows(IllegalStateException.class,()->foreign.getLinks().keySet().clear());
            var fresh=OM.Leaf(new ArrayList<>(List.of("new")));graph.getNodes().add(fresh);
            assertSame(graph.getNodes(),fresh.getParent());
            assertThrows(IllegalStateException.class,()->graph.getNodes().add(foreign.getNodes().get(0)));
        }
        assertEquals(2,foreign.getNodes().size());assertEquals(List.of("a"),foreign.getNodes().get(0).getData());assertEquals(1,foreign.getLinks().size());
    }
}
