package test.cache;

public class CacheAttributes {
    public static int calls;
    public static boolean count = true;

    public static int evaluate(CPElement element) {
        if (count) calls++;
        return 1;
    }

    public static int nested(CPCached element) {
        calls++;
        return element.a0() + element.a31() + element.a32() + element.a63() + element.a64();
    }

    public static int cycleA(CPCached element) { return element.cycleB(); }
    public static int cycleB(CPCached element) { return element.cycleA(); }
    public static boolean circularInitial() { return false; }
    public static boolean circularTwice(CPCached element) {
        calls++;
        return element.circularTwice() | element.circularTwice();
    }
    public static Object object(CPCached element) { calls++; return new Object(); }
}
