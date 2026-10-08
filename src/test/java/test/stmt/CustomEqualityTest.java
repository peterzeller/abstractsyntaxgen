package test.stmt;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;
import static test.stmt.TS.*;

class CustomEqualityTest {
    private static <T extends TSElement> T custom(Class<T> type, T delegate, boolean ignoreStructure) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("structuralEquals") && ignoreStructure) return true;
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }));
    }

    private static TSExpr chain(TSExpr leaf, int depth) {
        TSExpr expression = leaf;
        for (int i = 0; i < depth; i++) expression = BinaryExpr(expression, Plus(), IntLiteral(i));
        return expression;
    }

    private static TSExpr deepest(TSExpr expression) {
        while (expression instanceof TSBinaryExpr binary) expression = binary.getLeft();
        return expression;
    }

    @Test
    void customLeafEqualityDoesNotDependOnTheRecursionCutoff() {
        for (int depth : new int[]{10, 255, 256, 257, 300, 1000}) {
            var original = chain(custom(TSIntLiteral.class, IntLiteral(0), false), depth);
            var copy = original.copy();
            assertTrue(original.structuralEquals(copy), "depth " + depth);
            assertTrue(copy.structuralEquals(original), "depth " + depth);
            ((TSIntLiteral) deepest(copy)).setIntValue(-1);
            assertFalse(original.structuralEquals(copy), "depth " + depth);
            assertFalse(copy.structuralEquals(original), "depth " + depth);
        }
    }

    @Test
    void aCustomComparisonOwnsItsEntireSubtreeInTheIterativePath() {
        TSBinaryExpr custom = custom(TSBinaryExpr.class, BinaryExpr(IntLiteral(1), Plus(), IntLiteral(2)), true);
        var original = chain(custom, 300);
        var copy = original.copy();
        ((TSIntLiteral) deepest(copy)).setIntValue(-1);
        assertTrue(original.structuralEquals(copy));
    }
}
