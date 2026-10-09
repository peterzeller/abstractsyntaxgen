package test.bench;

import test.mutation.counted.*;
import test.mutation.plain.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Same schema with/without counts; retains mutated graphs. Run AstBench mutations [ancestorDepth=16]. */
public class MutationBench {
    private static volatile Object sink;
    private static volatile long checksum;
    private static final int SIZE = 4096, REPETITIONS = 20;

    private static MCFunctionList counted(int depth) {
        var functions = MC.FunctionList();
        for (int i = 0; i < SIZE; i++) functions.add(MC.Function(0, MC.StmtList(MC.Assign(MC.Num(i)))));
        MCAncestor ancestor = MC.Top(MC.Root(functions));
        for (int i = 0; i < depth; i++) ancestor = MC.Wrap(ancestor);
        sink = ancestor;
        return functions;
    }

    private static MPFunctionList plain(int depth) {
        var functions = MP.FunctionList();
        for (int i = 0; i < SIZE; i++) functions.add(MP.Function(0, MP.StmtList(MP.Assign(MP.Num(i)))));
        MPAncestor ancestor = MP.Top(MP.Root(functions));
        for (int i = 0; i < depth; i++) ancestor = MP.Wrap(ancestor);
        sink = ancestor;
        return functions;
    }

    private static void mutate(MCFunctionList functions, int operation) {
        for (int r = 0; r < REPETITIONS; r++) {
            for (int i = 0; i < SIZE; i++) {
                var body = functions.get(i).getBody();
                if (operation == 0) ((MCAssign) body.get(0)).getValue().setNumber(r + i);
                else if (operation == 1) { body.add(MC.Assign(MC.Num(r + i))); body.remove(1); }
                else {
                    var stmt = body.get(0);
                    body.replaceEach(Map.of(stmt, List.of(MC.Assign(MC.Num(((MCAssign) stmt).getValue().getNumber() + 1)))));
                }
            }
        }
        sink = functions;
        long result = 0;
        for (var function : functions) result += ((MCAssign) function.getBody().get(0)).getValue().getNumber() + function.modificationCount();
        checksum = result;
    }

    private static void mutate(MPFunctionList functions, int operation) {
        for (int r = 0; r < REPETITIONS; r++) {
            for (int i = 0; i < SIZE; i++) {
                var body = functions.get(i).getBody();
                if (operation == 0) ((MPAssign) body.get(0)).getValue().setNumber(r + i);
                else if (operation == 1) { body.add(MP.Assign(MP.Num(r + i))); body.remove(1); }
                else {
                    var stmt = body.get(0);
                    body.replaceEach(Map.of(stmt, List.of(MP.Assign(MP.Num(((MPAssign) stmt).getValue().getNumber() + 1)))));
                }
            }
        }
        sink = functions;
        long result = 0;
        for (var function : functions) result += ((MPAssign) function.getBody().get(0)).getValue().getNumber();
        checksum = result;
    }

    public static void main(String[] args) {
        int depth = args.length > 0 ? Integer.parseInt(args[0]) : 16;
        if (depth < 0) throw new IllegalArgumentException("Nonnegative depth required");
        for (int operation = 0; operation < 3; operation++) {
            final int op = operation;
            for (boolean counts : new boolean[]{false, true}) {
                Runnable run;
                if (counts) { var functions = counted(depth); run = () -> mutate(functions, op); }
                else { var functions = plain(depth); run = () -> mutate(functions, op); }
                for (int i = 0; i < 12; i++) run.run();
                long[] times = new long[12];
                for (int i = 0; i < times.length; i++) {
                    long start = System.nanoTime();
                    run.run();
                    times[i] = System.nanoTime() - start;
                }
                Arrays.sort(times);
                System.out.printf("%s, %s, ancestor depth %d: %.2f ns/operation%n",
                        new String[]{"setter", "add/remove pair", "replaceEach (one child)"}[operation],
                        counts ? "counts" : "no counts", depth,
                        (times[5] / 2.0 + times[6] / 2.0) / SIZE / REPETITIONS);
            }
        }
    }
}
