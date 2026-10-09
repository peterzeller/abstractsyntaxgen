package test.bench;

import com.sun.management.ThreadMXBean;
import test.stmt.*;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

import static test.stmt.TS.*;

/** Compares ownership transfer with removeAll + addAll, repeatedly moving the same children. */
public class ListTransferBench {
    private static volatile Object sink;

    private static void move(TSStatementList a, TSStatementList b, boolean direct, int repetitions) {
        for (int i = 0; i < repetitions; i++) {
            if (direct) {
                b.addAllMoved(a);
                a.addAllMoved(b);
            } else {
                b.addAll(a.removeAll());
                a.addAll(b.removeAll());
            }
        }
        sink = a;
    }

    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 1024;
        int repetitions = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        if (count < 1 || repetitions < 1) throw new IllegalArgumentException("Positive arguments required");
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocations.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        for (boolean direct : new boolean[]{false, true}) {
            var a = StatementList();
            var b = StatementList();
            for (int i = 0; i < count; i++) a.add(ExprStatement(IntLiteral(i)));
            for (int i = 0; i < 12; i++) move(a, b, direct, repetitions);
            long[] times = new long[12];
            long bytes = 0;
            for (int i = 0; i < times.length; i++) {
                long before = allocations.getThreadAllocatedBytes(thread);
                long start = System.nanoTime();
                move(a, b, direct, repetitions);
                times[i] = System.nanoTime() - start;
                bytes += allocations.getThreadAllocatedBytes(thread) - before;
            }
            Arrays.sort(times);
            System.out.printf("%s: %.3f ns/child, %.1f allocated bytes/transfer%n", direct ? "addAllMoved" : "removeAll+addAll",
                    (times[5] / 2.0 + times[6] / 2.0) / count / repetitions / 2,
                    bytes / (double) times.length / repetitions / 2);
        }
    }
}
