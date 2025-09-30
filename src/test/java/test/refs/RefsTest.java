package test.refs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static test.refs.TR.*;

public class RefsTest {

    @Test
    public void testBasicModuleConstruction() {
        var module = Module(
            FunctionList(
                FunctionDef("add", 
                    ParameterList(
                        Parameter(SimpleType("int"), "a"),
                        Parameter(SimpleType("int"), "b")
                    ),
                    SimpleType("int"),
                    StatementList(
                        ReturnStmt(BinaryExpr(VarAccess(null), Plus(), VarAccess(null)))
                    )
                )
            ),
            ClassDefList(
                ClassDef("Point",
                    FieldList(
                        Field(SimpleType("int"), "x"),
                        Field(SimpleType("int"), "y")
                    ),
                    MethodList()
                )
            )
        );
        
        assertNotNull(module);
        assertEquals(1, module.getFunctions().size());
        assertEquals(1, module.getClasses().size());
    }

    @Test
    public void testReferenceRelationships() {
        // Create a variable declaration
        var varDecl = VarDecl(SimpleType("int"), "x", IntLiteral(5));
        
        // Create a reference to that variable
        var varAccess = VarAccess(varDecl);
        
        // Create an assignment using the reference
        var assignment = Assignment(varDecl, IntLiteral(10));
        
        // Verify references are set correctly
        assertEquals(varDecl, varAccess.getVariable());
        assertEquals(varDecl, assignment.getTarget());
        
        // Note: ref fields don't change parent relationships
        assertNull(varDecl.getParent()); // varDecl is not a child of varAccess or assignment
    }

    @Test
    public void testFunctionCallReferences() {
        var funcDef = FunctionDef("test", 
            ParameterList(), 
            SimpleType("void"), 
            StatementList()
        );
        
        var funcCall = FunctionCall(funcDef, ExprList());
        
        assertEquals(funcDef, funcCall.getFunc());
        assertNull(funcDef.getParent()); // ref fields don't establish parent relationships
    }

    @Test
    public void testComplexReferenceScenario() {
        // Create a variable declaration
        var varDecl = VarDecl(SimpleType("int"), "counter", IntLiteral(0));
        
        // Create a function that uses this variable
        var funcDef = FunctionDef("increment",
            ParameterList(),
            SimpleType("void"),
            StatementList(
                Assignment(varDecl, BinaryExpr(VarAccess(varDecl), Plus(), IntLiteral(1)))
            )
        );
        
        // Create a statement that calls this function
        var funcCall = FunctionCall(funcDef, ExprList());
        
        var module = Module(
            FunctionList(funcDef),
            ClassDefList()
        );
        
        // Verify all references are correct
        var assignment = (TRAssignment) funcDef.getBody().get(0);
        assertEquals(varDecl, assignment.getTarget());
        
        var binaryExpr = (TRBinaryExpr) assignment.getValue();
        var leftAccess = (TRVarAccess) binaryExpr.getLeft();
        assertEquals(varDecl, leftAccess.getVariable());
        
        assertEquals(funcDef, funcCall.getFunc());
    }

    @Test
    public void testCopyWithReferences() {
        var varDecl = VarDecl(SimpleType("int"), "x", IntLiteral(5));
        var varAccess = VarAccess(varDecl);
        var assignment = Assignment(varDecl, IntLiteral(10));
        
        var statements = StatementList(varDecl, assignment);
        
        var copy = statements.copyWithRefs();
        
        // References should be updated to point to copied elements
        var copiedVarDecl = (TRVarDecl) copy.get(0);
        var copiedAssignment = (TRAssignment) copy.get(1);
        
        assertEquals(copiedVarDecl, copiedAssignment.getTarget());
        assertNotSame(varDecl, copiedVarDecl);
        assertNotSame(assignment, copiedAssignment);
    }

    @Test
    public void testReferenceIntegrityAfterModification() {
        var varDecl = VarDecl(SimpleType("int"), "x", IntLiteral(5));
        var varAccess = VarAccess(varDecl);
        
        // Create a new variable declaration
        var newVarDecl = VarDecl(SimpleType("int"), "y", IntLiteral(10));
        
        // Change the reference
        varAccess.setVariable(newVarDecl);
        
        assertEquals(newVarDecl, varAccess.getVariable());
        assertNotEquals(varDecl, varAccess.getVariable());
    }

    @Test
    public void testClassAndMethodStructure() {
        var classDef = ClassDef("Calculator",
            FieldList(
                Field(SimpleType("int"), "result")
            ),
            MethodList(
                Method("add",
                    ParameterList(
                        Parameter(SimpleType("int"), "value")
                    ),
                    SimpleType("void"),
                    StatementList()
                )
            )
        );
        
        assertEquals("Calculator", classDef.getName());
        assertEquals(1, classDef.getFields().size());
        assertEquals(1, classDef.getMethods().size());
        
        var field = classDef.getFields().get(0);
        assertEquals("result", field.getName());
        assertEquals("int", field.getFieldType().getTypeName());
        
        var method = classDef.getMethods().get(0);
        assertEquals("add", method.getName());
        assertEquals(1, method.getParams().size());
    }

    @Test
    public void testReferenceVisitor() {
        var varDecl1 = VarDecl(SimpleType("int"), "x", IntLiteral(1));
        var varDecl2 = VarDecl(SimpleType("int"), "y", IntLiteral(2));
        
        var module = Module(
            FunctionList(
                FunctionDef("test",
                    ParameterList(),
                    SimpleType("void"),
                    StatementList(
                        varDecl1,
                        varDecl2,
                        Assignment(varDecl1, VarAccess(varDecl2))
                    )
                )
            ),
            ClassDefList()
        );
        
        // Count variable declarations
        var declCounter = new TRElement.DefaultVisitor() {
            int count = 0;
            
            @Override
            public void visit(TRVarDecl varDecl) {
                count++;
            }
        };
        
        module.accept(declCounter);
        assertEquals(2, declCounter.count);
    }

    @Test
    public void testReferenceMatcher() {
        var varDecl = VarDecl(SimpleType("int"), "x", IntLiteral(5));
        var varAccess = VarAccess(varDecl);
        
        String result = varAccess.match(new TRExpr.Matcher<String>() {
            @Override
            public String case_VarAccess(TRVarAccess varAccess) {
                return "accessing " + varAccess.getVariable().getName();
            }
            
            @Override
            public String case_IntLiteral(TRIntLiteral intLiteral) {
                return "int literal";
            }
            
            @Override
            public String case_BoolLiteral(TRBoolLiteral boolLiteral) {
                return "bool literal";
            }
            
            @Override
            public String case_BinaryExpr(TRBinaryExpr binaryExpr) {
                return "binary expression";
            }
        });
        
        assertEquals("accessing x", result);
    }

    @Test
    public void testReferenceNullSafety() {
        // Test that ref fields can be null initially
        var varAccess = VarAccess(null);
        assertNull(varAccess.getVariable());
        
        // Test setting a reference
        var varDecl = VarDecl(SimpleType("int"), "x", IntLiteral(5));
        varAccess.setVariable(varDecl);
        assertEquals(varDecl, varAccess.getVariable());
    }

    @Test
    public void testComplexModuleStructure() {
        var module = Module(
            FunctionList(
                FunctionDef("main",
                    ParameterList(),
                    SimpleType("void"),
                    StatementList(
                        VarDecl(SimpleType("int"), "x", IntLiteral(10)),
                        VarDecl(SimpleType("int"), "y", IntLiteral(20))
                    )
                ),
                FunctionDef("helper",
                    ParameterList(
                        Parameter(SimpleType("int"), "param")
                    ),
                    SimpleType("int"),
                    StatementList(
                        ReturnStmt(IntLiteral(42))
                    )
                )
            ),
            ClassDefList(
                ClassDef("MyClass",
                    FieldList(
                        Field(SimpleType("string"), "name"),
                        Field(SimpleType("int"), "id")
                    ),
                    MethodList(
                        Method("getName",
                            ParameterList(),
                            SimpleType("string"),
                            StatementList()
                        )
                    )
                )
            )
        );
        
        assertEquals(2, module.getFunctions().size());
        assertEquals(1, module.getClasses().size());
        
        var mainFunc = module.getFunctions().get(0);
        assertEquals("main", mainFunc.getName());
        assertEquals(2, mainFunc.getBody().size());
        
        var myClass = module.getClasses().get(0);
        assertEquals("MyClass", myClass.getName());
        assertEquals(2, myClass.getFields().size());
        assertEquals(1, myClass.getMethods().size());
    }
}