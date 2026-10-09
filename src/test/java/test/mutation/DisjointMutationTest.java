package test.mutation;

import org.junit.jupiter.api.Test;
import test.mutation.counted.*;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static test.mutation.counted.MC.*;

class DisjointMutationTest {
    @Test
    void workersMutateOnlyTheirOwnFunctionAndRetainExactCountsAndOrder() throws Exception {
        int workers = 8, repetitions = 2000;
        for (int round = 0; round < 6; round++) {
            var functions = FunctionList();
            for (int i = 0; i < workers; i++) functions.add(Function(i, StmtList(Assign(Num(0)))));
            var root = Root(functions);
            var sharedAncestor = Wrap(Wrap(Top(root)));
            int[] initialCounts = new int[workers];
            for (int i = 0; i < workers; i++) initialCounts[i] = functions.get(i).modificationCount();
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(workers)) {
                List<Future<?>> tasks = new ArrayList<>();
                for (int i = 0; i < workers; i++) {
                    var function = functions.get(i); // Publish inputs before workers start.
                    tasks.add(executor.submit(() -> {
                        start.await();
                        var body = function.getBody();
                        var number = ((MCAssign) body.get(0)).getValue();
                        for (int r = 0; r < repetitions; r++) {
                            number.setNumber(r); // one count
                            body.add(Assign(Num(r))); // one count
                            body.get(1).replaceBy(Assign(Num(r + 1))); // one count
                            body.remove(1); // one count
                        }
                        for (int r = 0; r < 32; r++) body.add(Assign(Num(r)));
                        var replacements = new LinkedHashMap<MCStmt, List<MCStmt>>();
                        for (var stmt : body) {
                            var value = ((MCAssign) stmt).getValue().getNumber();
                            replacements.put(stmt, List.of(Assign(Num(value + 10)), Assign(Num(value + 20))));
                        }
                        body.replaceEach(replacements); // one bulk modification
                        return null;
                    }));
                }
                start.countDown();
                for (var task : tasks) task.get(30, TimeUnit.SECONDS);
            }
            assertSame(sharedAncestor.getChild(), root.getParent().getParent());
            assertSame(root, functions.getParent());
            assertEquals(workers, functions.size());
            for (int i = 0; i < workers; i++) {
                var function = functions.get(i);
                assertSame(functions, function.getParent());
                assertEquals(i, function.getVersion());
                assertEquals(initialCounts[i] + 4 * repetitions + 33, function.modificationCount());
                var body = function.getBody();
                assertSame(function, body.getParent());
                assertEquals(66, body.size());
                for (int j = 0; j < body.size(); j++) {
                    var stmt = (MCAssign) body.get(j);
                    assertSame(body, stmt.getParent());
                    assertSame(stmt, stmt.getValue().getParent());
                    int original = j < 2 ? repetitions - 1 : j / 2 - 1;
                    assertEquals(original + (j % 2 == 0 ? 10 : 20), stmt.getValue().getNumber());
                }
            }
        }
    }
}
