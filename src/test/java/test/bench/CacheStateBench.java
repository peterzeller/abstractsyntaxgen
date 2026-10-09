package test.bench;

import com.sun.management.ThreadMXBean;
import test.cache.*;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Diagnostic for generated cache state storage. Compare independent, interleaved JVM processes. */
public class CacheStateBench {
    private static volatile Object sink;
    private static volatile long sum;

    private static long read(CPNode[] nodes, boolean clear, int repetitions) {
        long result = 0;
        for (int r = 0; r < repetitions; r++) {
            for (CPNode node : nodes) {
                if (clear) node.clearAttributesLocal();
                result += node.a0() + node.a31() + node.a32() + node.a63() + node.a64();
            }
        }
        return result;
    }

    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 65536;
        int repetitions = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        if (count < 1 || repetitions < 1) throw new IllegalArgumentException("Positive arguments required");
        CacheAttributes.count = false;
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocations.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        // Warm the constructor before measuring; array allocation is outside the measured region.
        for (int i = 0; i < 100000; i++) sink = CP.Node(i);
        CPNode[] nodes = new CPNode[count];
        long before = allocations.getThreadAllocatedBytes(thread);
        for (int i = 0; i < count; i++) nodes[i] = CP.Node(i);
        long bytes = allocations.getThreadAllocatedBytes(thread) - before;
        sink = nodes;
        System.out.printf("Node: %.1f allocated bytes/object%n", bytes / (double) count);
        for (boolean clear : new boolean[]{false, true}) {
            for (int i = 0; i < 12; i++) sum = read(nodes, clear, repetitions);
            long[] times = new long[12];
            for (int i = 0; i < times.length; i++) {
                long start = System.nanoTime();
                sum = read(nodes, clear, repetitions);
                times[i] = System.nanoTime() - start;
            }
            Arrays.sort(times);
            System.out.printf("%s: %.3f ns/getter%n", clear ? "clear+recompute" : "cached",
                    (times[5] / 2.0 + times[6] / 2.0) / count / repetitions / 5);
        }
    }
}
