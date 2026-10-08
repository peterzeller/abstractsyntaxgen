package test.bench;

import test.stmt.*;

import java.util.Random;

import static test.stmt.TS.*;

/**
 * Micro benchmark of what the generated trees do all the time: build, walk with several visitors, copy, compare.
 * Not a test (no JUnit annotations): run its main, e.g.
 * <pre>java -XX:+UseParallelGC -Xmx2g -cp build/classes/java/test:build/classes/java/main test.bench.AstBench [rounds]</pre>
 * Prints, per round, the best time of the operations and the heap per node.
 */
public class AstBench {

    static TSExpr expr(Random r, int depth) {
        int k = r.nextInt(6);
        if (depth <= 0 || k < 3) {
            return k == 0 ? VarRef("v" + r.nextInt(40)) : IntLiteral(r.nextInt(100));
        }
        return BinaryExpr(expr(r, depth - 1), k == 3 ? Plus() : k == 4 ? Less() : Equals(), expr(r, depth - 1));
    }

    static TSStatementList list(Random r, int depth, int max) {
        var list = StatementList();
        int n = r.nextInt(max + 1);
        for (int i = 0; i < n; i++) {
            list.add(statement(r, depth));
        }
        return list;
    }

    static TSStatement statement(Random r, int depth) {
        int k = r.nextInt(10);
        if (depth <= 0 || k < 5) {
            return k < 3 ? ExprStatement(expr(r, 2)) : Assignment("v" + r.nextInt(40), expr(r, 2));
        }
        if (k < 7) {
            return IfStatement(expr(r, 1), list(r, depth - 1, 3), list(r, depth - 1, 2));
        }
        if (k < 9) {
            return WhileLoop(expr(r, 1), list(r, depth - 1, 3));
        }
        return Block(list(r, depth - 1, 4));
    }

    static TSProgram program(long seed, int functions) {
        var r = new Random(seed);
        var statements = StatementList();
        for (int i = 0; i < functions; i++) {
            statements.add(Block(list(r, 6, 5)));
        }
        return Program(statements);
    }

    // several visitor classes, so the visit calls are megamorphic like in a compiler with many passes
    static abstract class Counting extends TSElement.DefaultVisitor {
        long count;
    }

    static final Counting[] VISITORS = {
        new Counting() { @Override public void visit(TSVarRef e) { count++; } },
        new Counting() { @Override public void visit(TSIntLiteral e) { count++; } },
        new Counting() { @Override public void visit(TSBinaryExpr e) { count++; super.visit(e); } },
        new Counting() { @Override public void visit(TSAssignment e) { count++; super.visit(e); } },
        new Counting() { @Override public void visit(TSExprStatement e) { count++; super.visit(e); } },
        new Counting() { @Override public void visit(TSIfStatement e) { count++; super.visit(e); } },
    };

    static long usedMemory() {
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    static int nodes(TSElement e) {
        int[] n = {0};
        new TSElement.IterativeVisitor() {
            @Override public void visit(TSProgram x) { n[0]++; }
            @Override public void visit(TSAssignment x) { n[0]++; }
            @Override public void visit(TSIfStatement x) { n[0]++; }
            @Override public void visit(TSWhileLoop x) { n[0]++; }
            @Override public void visit(TSBlock x) { n[0]++; }
            @Override public void visit(TSExprStatement x) { n[0]++; }
            @Override public void visit(TSBinaryExpr x) { n[0]++; }
            @Override public void visit(TSVarRef x) { n[0]++; }
            @Override public void visit(TSIntLiteral x) { n[0]++; }
            @Override public void visit(TSBoolLiteral x) { n[0]++; }
            @Override public void visit(TSPlus x) { n[0]++; }
            @Override public void visit(TSEquals x) { n[0]++; }
            @Override public void visit(TSLess x) { n[0]++; }
            @Override public void visit(TSStatementList x) { n[0]++; }
        }.traverse(e);
        return n[0];
    }

    public static void main(String[] args) {
        int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 12;
        int functions = args.length > 1 ? Integer.parseInt(args[1]) : 4000;

        long before = usedMemory();
        TSProgram program = program(42, functions);
        long after = usedMemory();
        int nodeCount = nodes(program);
        System.out.printf("nodes %d, heap %.1f MB, %.1f bytes per node%n", nodeCount, (after - before) / 1e6, (double) (after - before) / nodeCount);

        // the small trees which a compiler copies all the time (an inlined body, a replaced expression)
        var small = new java.util.ArrayList<TSStatement>();
        var r = new Random(7);
        for (int i = 0; i < 20_000; i++) {
            small.add(statement(r, 1));
        }
        int smallNodes = 0;
        for (TSStatement s : small) {
            smallNodes += nodes(s);
        }

        long bestBuild = Long.MAX_VALUE, bestWalk = Long.MAX_VALUE, bestCopy = Long.MAX_VALUE, bestSmallCopy = Long.MAX_VALUE,
            bestEquals = Long.MAX_VALUE, bestLists = Long.MAX_VALUE, bestGeneric = Long.MAX_VALUE;
        long sink = 0;
        for (int round = 0; round < rounds; round++) {
            long t0 = System.nanoTime();
            var built = program(100 + round, functions / 4);
            long t1 = System.nanoTime();
            bestBuild = Math.min(bestBuild, t1 - t0);
            sink += built.size();

            t0 = System.nanoTime();
            for (int rep = 0; rep < 4; rep++) {
                for (Counting v : VISITORS) {
                    v.count = 0;
                    program.accept(v);
                    sink += v.count;
                }
            }
            t1 = System.nanoTime();
            bestWalk = Math.min(bestWalk, t1 - t0);

            t0 = System.nanoTime();
            for (int rep = 0; rep < 24; rep++) {
                sink += walkGeneric(program);
            }
            t1 = System.nanoTime();
            bestGeneric = Math.min(bestGeneric, t1 - t0);

            t0 = System.nanoTime();
            TSProgram copy = program.copy();
            t1 = System.nanoTime();
            bestCopy = Math.min(bestCopy, t1 - t0);
            sink += copy.size();

            t0 = System.nanoTime();
            for (TSStatement s : small) {
                sink += s.copy().size();
            }
            t1 = System.nanoTime();
            bestSmallCopy = Math.min(bestSmallCopy, t1 - t0);

            t0 = System.nanoTime();
            if (!program.structuralEquals(copy)) throw new AssertionError();
            t1 = System.nanoTime();
            bestEquals = Math.min(bestEquals, t1 - t0);

            // list access: the loops of the visitors without the dispatch
            t0 = System.nanoTime();
            long sum = 0;
            for (int rep = 0; rep < 20; rep++) {
                sum += sumLists(program);
            }
            t1 = System.nanoTime();
            bestLists = Math.min(bestLists, t1 - t0);
            sink += sum;
        }
        System.out.printf("build %6.1f ms (%d nodes/4)%n", bestBuild / 1e6, nodeCount / 4);
        System.out.printf("walk  %6.1f ms for %d visits of the tree (%.1f ns per node and visit)%n", bestWalk / 1e6, 24, bestWalk / (24.0 * nodeCount));
        System.out.printf("walk by size and get %6.1f ms for 24 walks (%.1f ns per node)%n", bestGeneric / 1e6, bestGeneric / (24.0 * nodeCount));
        System.out.printf("copy  %6.1f ms (%.1f ns per node)%n", bestCopy / 1e6, bestCopy / (double) nodeCount);
        System.out.printf("copy of %d small trees %6.1f ms (%.1f ns per node)%n", small.size(), bestSmallCopy / 1e6, bestSmallCopy / (double) smallNodes);
        System.out.printf("equals %6.1f ms (%.1f ns per node)%n", bestEquals / 1e6, bestEquals / (double) nodeCount);
        System.out.printf("lists %6.1f ms for 20 passes over the lists%n", bestLists / 1e6);
        if (sink == 42) System.out.println();
    }

    /** A walk without the visitor: two virtual calls per node (size and get), no double dispatch. */
    static long walkGeneric(TSElement e) {
        long n = 1;
        for (int i = 0, c = e.size(); i < c; i++) {
            n += walkGeneric(e.get(i));
        }
        return n;
    }

    static long sumLists(TSElement e) {
        long sum = 0;
        if (e instanceof TSStatementList list) {
            for (int i = 0, n = list.size(); i < n; i++) {
                sum += list.get(i).size();
            }
        }
        for (int i = 0, n = e.size(); i < n; i++) {
            sum += sumLists(e.get(i));
        }
        return sum;
    }
}
