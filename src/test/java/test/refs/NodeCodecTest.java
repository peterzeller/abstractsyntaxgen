package test.refs;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static test.refs.TR.*;

class NodeCodecTest {
    sealed interface Value permits Atom, Child, Reference { }
    record Atom(Object value) implements Value { }
    record Child(TRNodeCodec.Data<Value> node) implements Value { }
    record Reference(int slot) implements Value { }

    static final class Codec implements TRNodeCodec.Encoder<Value>, TRNodeCodec.Decoder<Value> {
        final Map<TRElement, Integer> symbols = new IdentityHashMap<>();
        final Map<Integer, TRElement> targets = new HashMap<>();
        int nodes;
        public Value child(TRElement node) { nodes++; return new Child(TRNodeCodec.encode(node, this)); }
        public Value reference(TRElement node) {
            if (node == null) return new Reference(-1);
            Integer slot = symbols.get(node);
            if (slot == null) throw new IllegalArgumentException("unregistered reference");
            return new Reference(slot);
        }
        public Value attribute(String field, Object value) {
            return new Atom(value instanceof List<?> list ? List.copyOf(list) : value);
        }
        public TRElement child(Value value) { return TRNodeCodec.decode(((Child) value).node(), this); }
        public TRElement reference(Value value) {
            int slot = ((Reference) value).slot();
            if (slot == -1) return null;
            return Objects.requireNonNull(targets.get(slot));
        }
        public Object restoreAttribute(String field, Value value) {
            Object atom = ((Atom) value).value();
            return atom instanceof List<?> list ? new ArrayList<>(list) : atom;
        }
    }

    @Test
    void bodyRoundTripUsesOwnedChildrenAndResolvedExternalReferences() {
        var local = VarDecl(SimpleType("int"), "x", IntLiteral(1));
        var callee = FunctionDef("callee", ParameterList(), SimpleType("void"), StatementList());
        callee.getBody().add(FunctionCall(callee, ExprList()));
        var original = StatementList(Assignment(local, IntLiteral(2)), FunctionCall(callee, ExprList(VarAccess(local))),
            ReturnStmt(BinaryExpr(BoolLiteral(true), Equals(), BoolLiteral(false))), ReturnStmt(VarAccess(null)));
        var codec = new Codec();
        codec.symbols.put(local, 0);
        codec.symbols.put(callee, 1);
        Value frozen = codec.child(original);
        int encoded = codec.nodes;
        var reboundLocal = local.copyWithRefs();
        var reboundCallee = callee.copyWithRefs();
        codec.targets.put(0, reboundLocal);
        codec.targets.put(1, reboundCallee);
        var copy = (TRStatementList) codec.child(frozen);
        assertNotSame(original, copy);
        assertSame(reboundLocal, ((TRAssignment) copy.get(0)).getTarget());
        assertSame(reboundCallee, ((TRFunctionCall) copy.get(1)).getFunc());
        assertSame(reboundLocal, ((TRVarAccess) ((TRFunctionCall) copy.get(1)).getArgs().get(0)).getVariable());
        assertNull(((TRVarAccess) ((TRReturnStmt) copy.get(3)).getValue()).getVariable());
        assertSame(original, original.get(0).getParent());
        assertSame(copy, copy.get(0).getParent());
        assertEquals(13, encoded); // references do not traverse the local declaration or recursive callee
        assertSame(callee, ((TRFunctionCall) callee.getBody().get(0)).getFunc());
    }

    @Test
    void mutableMetadataIsDetachedByAdapterAndIgnoredEqualityFieldsAreStillEncoded() {
        var values = new ArrayList<>(List.of("before"));
        var original = CodecMetadata(values);
        original.setCodecNote("note");
        original.setMemo(new ArrayList<>(List.of("memo")));
        var codec = new Codec();
        Value frozen = codec.child(original);
        original.getMemo().clear();
        original.setCodecNote("changed");
        values.set(0, "after");
        var first = (TRCodecMetadata) codec.child(frozen);
        first.getValues().add("private work");
        var second = (TRCodecMetadata) codec.child(frozen);
        assertEquals(List.of("before"), second.getValues());
        assertEquals(List.of("memo"), second.getMemo());
        assertEquals("note", second.getCodecNote());
        assertEquals(List.of("after"), original.getValues());
        var ignored = (TRIgnoredMetadata) codec.child(codec.child(IgnoredMetadata("keep this")));
        assertEquals("keep this", ignored.getMarker());
    }

    @Test
    void referenceListsPreserveAliasingAndDoNotAcquireTheirTargets() {
        var variable = VarDecl(SimpleType("int"), "x", IntLiteral(1));
        var codec = new Codec();
        codec.symbols.put(variable, 4);
        codec.targets.put(4, variable);
        var original = ReferenceVars(variable, variable);
        original.setCodecNote("reference list");
        var copy = (TRReferenceVars) codec.child(codec.child(original));
        assertSame(variable, copy.get(0));
        assertSame(copy.get(0), copy.get(1));
        assertNull(variable.getParent());
        assertEquals("reference list", copy.getCodecNote());
        assertEquals(1, codec.nodes);
    }

    @Test
    void schemaEncodingDoesNotRecurseIntoDeepChildren() {
        TRExpr expression = IntLiteral(0);
        for (int i = 0; i < 10000; i++) expression = BinaryExpr(expression, Plus(), IntLiteral(i));
        int[] children = {0};
        var data = TRNodeCodec.encode(expression, new TRNodeCodec.Encoder<Integer>() {
            public Integer child(TRElement node) { return ++children[0]; }
            public Integer reference(TRElement node) { throw new AssertionError("unexpected reference"); }
            public Integer attribute(String field, Object value) { return 0; }
        });
        assertEquals(3, children[0]);
        assertEquals("TRBinaryExpr", data.kind());
    }

    @Test
    void immutableContainersAndMalformedDataFailExplicitly() {
        List<Value> fields = new ArrayList<>(List.of(new Atom(4)));
        var data = new TRNodeCodec.Data<>("TRIntLiteral", fields, List.<Value>of());
        fields.clear();
        assertEquals(1, data.fields().size());
        assertThrows(UnsupportedOperationException.class, () -> data.fields().clear());
        var codec = new Codec();
        assertThrows(IllegalArgumentException.class, () -> TRNodeCodec.decode(new TRNodeCodec.Data<Value>("missing", List.of(), List.of()), codec));
        assertThrows(IllegalArgumentException.class, () -> TRNodeCodec.decode(new TRNodeCodec.Data<Value>("TRIntLiteral", List.of(), List.of()), codec));
        assertThrows(IllegalArgumentException.class, () -> TRNodeCodec.decode(new TRNodeCodec.Data<Value>("TRExprList", List.of(new Atom(1), new Atom(2)), List.of()), codec));
    }
}
