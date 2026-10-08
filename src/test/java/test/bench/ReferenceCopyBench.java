package test.bench;

import com.sun.management.ThreadMXBean;
import test.refs.*;

import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.Arrays;

import static test.refs.TR.*;

/**
 * Sparse references and a wide list: measures iterative copy and reference repair separately from AstBench.
 * Run after testClasses, e.g. java -XX:+UseParallelGC -Xmx2g -cp build/classes/java/test
 * test.bench.ReferenceCopyBench [variables=12000] [referenceEvery=4] [rounds=12].
 * Uses separate warmup rounds and retains each copy in a volatile sink. Compare independent JVM processes;
 * these medians and allocation counts are a diagnostic, not a substitute for compiler build measurements.
 */
public class ReferenceCopyBench {
    private static volatile TRElement sink;

    private static TRStatementList tree(int variables, int referenceEvery) {
        var declarations = new TRVarDecl[variables];
        for (int i = 0; i < variables; i++) declarations[i] = VarDecl(SimpleType("int"), "v" + i, IntLiteral(i));
        var body = StatementList();
        for (int i = 0; i < variables; i++) {
            // Reference a later sibling, whose copy will not exist when this reference is first copied.
            if (i % referenceEvery == 0) body.add(ReturnStmt(VarAccess(declarations[(i + 1) % variables])));
            body.add(declarations[i]);
            body.add(ReturnStmt(BinaryExpr(IntLiteral(i), Plus(), IntLiteral(i + 1))));
        }
        return body;
    }

    private static int nodes(TRElement root) {
        var stack = new ArrayDeque<TRElement>();
        stack.push(root);
        int count = 0;
        while (!stack.isEmpty()) {
            var node = stack.pop();
            count++;
            for (int i = 0; i < node.size(); i++) stack.push(node.get(i));
        }
        return count;
    }

    private static void measure(TRStatementList root, boolean withRefs, int rounds, ThreadMXBean allocations) {
        for (int i = 0; i < 12; i++) sink = withRefs ? root.copyWithRefs() : root.copy();
        long[] times = new long[rounds];
        long bytes = 0;
        long threadId = Thread.currentThread().threadId();
        for (int i = 0; i < rounds; i++) {
            long before = allocations == null ? 0 : allocations.getThreadAllocatedBytes(threadId);
            long start = System.nanoTime();
            sink = withRefs ? root.copyWithRefs() : root.copy();
            times[i] = System.nanoTime() - start;
            if (allocations != null) bytes += allocations.getThreadAllocatedBytes(threadId) - before;
        }
        Arrays.sort(times);
        int count = nodes(root);
        double median = (times[(rounds - 1) / 2] / 2.0 + times[rounds / 2] / 2.0);
        System.out.printf("%s: median %.1f ns/node", withRefs ? "copyWithRefs" : "copy", median / count);
        if (allocations != null) System.out.printf(", %.1f allocated bytes/node", bytes / (double) rounds / count);
        System.out.println();
    }

    public static void main(String[] args) {
        int variables = args.length > 0 ? Integer.parseInt(args[0]) : 12000;
        int referenceEvery = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 12;
        if (variables < 1 || referenceEvery < 1 || rounds < 1) throw new IllegalArgumentException("Arguments must be positive");
        var root = tree(variables, referenceEvery);
        var bean = ManagementFactory.getThreadMXBean();
        ThreadMXBean allocations = bean instanceof ThreadMXBean extended && extended.isThreadAllocatedMemorySupported()
                ? extended : null;
        if (allocations != null && !allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        System.out.printf("nodes %d, reference every %d declarations%n", nodes(root), referenceEvery);
        measure(root, false, rounds, allocations);
        measure(root, true, rounds, allocations);
    }
}
