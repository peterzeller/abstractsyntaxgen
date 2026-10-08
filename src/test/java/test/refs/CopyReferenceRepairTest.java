package test.refs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static test.refs.TR.*;

class CopyReferenceRepairTest {
    @Test
    void skippedLeavesStillParticipateInReferenceRepairAndTargetMapping() {
        var target = LeafTarget();
        var original = LeafPair(LeafReference(target), target);
        var copy = original.copyWithRefs();
        assertNotSame(target, copy.getLeafTarget());
        assertSame(copy.getLeafTarget(), copy.getReference().getLeafTarget());
        assertSame(copy, copy.getLeafTarget().getParent());
        assertSame(original, target.getParent());
        assertSame(target, original.getReference().getLeafTarget());
    }

    @Test
    void deepCopiesRepairReferencesInTheirIterativeSubtree() {
        var target = VarDecl(SimpleType("int"), "internal", IntLiteral(1));
        TRExpr expression = VarAccess(target);
        for (int i = 0; i < 1000; i++) expression = BinaryExpr(expression, Plus(), IntLiteral(i));
        var original = StatementList(ReturnStmt(expression), target);
        var copy = original.copyWithRefs();
        TRExpr copiedExpression = ((TRReturnStmt) copy.get(0)).getValue();
        while (copiedExpression instanceof TRBinaryExpr binary) copiedExpression = binary.getLeft();
        assertSame(copy.get(1), ((TRVarAccess) copiedExpression).getVariable());
        assertSame(original, target.getParent());
    }

    @Test
    void copiesRepairLeafReferencesToLaterSiblingsAndKeepExternalAndNullReferences() {
        var internal = VarDecl(SimpleType("int"), "internal", IntLiteral(1));
        var external = VarDecl(SimpleType("int"), "external", IntLiteral(2));
        var body = StatementList(
            ReturnStmt(VarAccess(internal)),
            ReturnStmt(VarAccess(external)),
            ReturnStmt(VarAccess(null)),
            internal);
        var copy = body.copyWithRefs();
        var copiedInternal = (TRVarDecl) copy.get(3);
        assertNotSame(internal, copiedInternal);
        assertSame(copiedInternal, ((TRVarAccess) ((TRReturnStmt) copy.get(0)).getValue()).getVariable());
        assertSame(external, ((TRVarAccess) ((TRReturnStmt) copy.get(1)).getValue()).getVariable());
        assertNull(((TRVarAccess) ((TRReturnStmt) copy.get(2)).getValue()).getVariable());
        assertSame(internal, ((TRVarAccess) ((TRReturnStmt) body.get(0)).getValue()).getVariable());
        assertSame(copy, copiedInternal.getParent());
        assertSame(body, internal.getParent());
        assertNull(external.getParent());
    }

    @Test
    void copiesRepairReferencesToTheRoot() {
        var function = FunctionDef("recursive", ParameterList(), SimpleType("void"), StatementList());
        function.getBody().add(FunctionCall(function, ExprList()));
        var copy = function.copyWithRefs();
        assertSame(copy, ((TRFunctionCall) copy.getBody().get(0)).getFunc());
        assertSame(function, ((TRFunctionCall) function.getBody().get(0)).getFunc());
        assertNull(copy.getParent());
    }

    @Test
    void referenceBearingRootsWithNoChildrenKeepTheirExternalReference() {
        var target = VarDecl(SimpleType("int"), "external", IntLiteral(1));
        var original = VarAccess(target);
        var copy = original.copyWithRefs();
        assertNotSame(original, copy);
        assertSame(target, copy.getVariable());
        assertNull(copy.getParent());
        assertNull(target.getParent());
    }
}
