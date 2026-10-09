package test.bench;

import test.cache.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** A standalone expected-failure probe, not a passing test of concurrent cache support. */
public class ConcurrentAttributesProbe {
    private static final CountDownLatch STARTED = new CountDownLatch(1);
    private static final CountDownLatch RELEASE = new CountDownLatch(1);
    private static volatile boolean gate;

    public static String evaluate(CPProbe node) {
        if (gate && node.getNumber() == 0) {
            STARTED.countDown();
            try {
                if (!RELEASE.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out releasing evaluator");
            } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new RuntimeException(failure); }
        }
        return "v" + node.getNumber();
    }

    private static int compare(CPSharedRoot tree, List<String> expected) {
        int failures = 0;
        for (int i = 0; i < tree.getNodes().size(); i++) {
            try { if (!expected.get(i).equals(tree.getNodes().get(i).value())) failures++; }
            catch (CyclicDependencyError failure) { failures++; }
        }
        return failures;
    }

    public static void main(String[] args) throws Exception {
        int workers = args.length > 0 ? Integer.parseInt(args[0]) : 8;
        if (workers < 2) throw new IllegalArgumentException("At least two workers required");
        var nodes = CP.ProbeList();
        for (int i = 0; i < 4096; i++) nodes.add(CP.Probe(i));
        var tree = CP.SharedRoot(nodes);
        var expected = nodes.stream().map(CPProbe::value).toList();
        nodes.forEach(CPProbe::clearAttributesLocal);
        int cold = 0, warm = 0;
        try (var executor = Executors.newFixedThreadPool(workers)) {
            gate = true;
            // Hold one evaluator while the other workers enter the same attribute getter. This is a
            // legitimate overlap, and latch publication avoids relying on probabilistic scheduling.
            var first = executor.submit(() -> compare(tree, expected));
            if (!STARTED.await(10, TimeUnit.SECONDS)) throw new AssertionError("Evaluator did not start");
            var readersFinished = new CountDownLatch(workers - 1);
            List<Future<Integer>> readers = new ArrayList<>();
            try {
                for (int i = 1; i < workers; i++) readers.add(executor.submit(() -> {
                    try { return compare(tree, expected); } finally { readersFinished.countDown(); }
                }));
                readersFinished.await(1, TimeUnit.SECONDS);
            } finally { RELEASE.countDown(); }
            cold += first.get(10, TimeUnit.SECONDS);
            for (var reader : readers) cold += reader.get(10, TimeUnit.SECONDS);
            gate = false;
            List<Future<Integer>> warmed = new ArrayList<>();
            for (int i = 0; i < workers; i++) warmed.add(executor.submit(() -> compare(tree, expected)));
            for (var reader : warmed) warm += reader.get(10, TimeUnit.SECONDS);
        }
        System.out.printf("workers %d, nodes %d: cold failures %d, safely published warm failures %d%n", workers, nodes.size(), cold, warm);
        if (cold != 0 || warm != 0) System.exit(1);
    }
}
