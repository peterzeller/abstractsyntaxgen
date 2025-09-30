package test.expr;

import org.junit.jupiter.api.Test;
import test.expr.*;

import static org.junit.jupiter.api.Assertions.*;
import static test.expr.TE.*; // Static import for the factory

public class ExprTest {

    @Test
    public void testBasicConstruction() {
        // Create: 5 + 3
        var expr = BinaryExpr(
                IntLiteral(5),
                Plus(),
                IntLiteral(3)
        );

        assertNotNull(expr);

        // Need to cast to access specific properties
        assertTrue(expr.getLeft() instanceof TEIntLiteral);
        assertTrue(expr.getRight() instanceof TEIntLiteral);

        var leftLiteral = (TEIntLiteral) expr.getLeft();
        var rightLiteral = (TEIntLiteral) expr.getRight();

        assertEquals(5, leftLiteral.getIvalue());
        assertEquals(3, rightLiteral.getIvalue());
    }

    @Test
    public void testParentRelationships() {
        var left = IntLiteral(5);
        var right = IntLiteral(3);
        var expr = BinaryExpr(left, Plus(), right);
        
        assertEquals(expr, left.getParent());
        assertEquals(expr, right.getParent());
    }

    @Test
    public void testCopy() {
        var original = BinaryExpr(
            VarRef("x"),
            Plus(),
            IntLiteral(5)
        );
        
        var copy = original.copy();
        
        assertTrue(original.structuralEquals(copy));
        assertNotSame(original, copy);
    }

    @Test
    public void testListOperations() {
        var list = ExprList(
            IntLiteral(1),
            IntLiteral(2),
            IntLiteral(3)
        );
        
        assertEquals(3, list.size());
        assertEquals(1, ((TEIntLiteralImpl)list.get(0)).getIvalue());
        
        // Test modification
        list.add(IntLiteral(4));
        assertEquals(4, list.size());
    }

    @Test
    public void testMatcher() {
        var op = Plus();
        
        String result = op.match(new TEOperator.Matcher<String>() {
            @Override
            public String case_Plus(TEPlus plus) { return "addition"; }
            
            @Override
            public String case_Minus(TEMinus minus) { return "subtraction"; }
            
            @Override
            public String case_Times(TETimes times) { return "multiplication"; }
            
            @Override
            public String case_Div(TEDiv div) { return "division"; }
            
            @Override
            public String case_Equals(TEEquals equals) { return "equality"; }
            
            @Override
            public String case_Less(TELess less) { return "less than"; }
        });
        
        assertEquals("addition", result);
    }
}