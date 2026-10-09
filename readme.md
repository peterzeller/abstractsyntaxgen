# Abstract Syntax Generator

[![Java CI with Gradle](https://github.com/peterzeller/abstractsyntaxgen/actions/workflows/gradle.yml/badge.svg)](https://github.com/peterzeller/abstractsyntaxgen/actions/workflows/gradle.yml)
[![](https://jitpack.io/v/peterzeller/abstractsyntaxgen.svg)](https://jitpack.io/#peterzeller/abstractsyntaxgen)

Generate Java classes for representing mutable abstract syntax trees with powerful mutation capabilities for compiler optimizations.

## Features

- **Order sorted terms** - Hierarchical AST structure
- **Simulated union types** via sealed interfaces
- **Consistency checks** - Each element appears only once in the tree
- **Cached and uncached attributes** - Computed properties with automatic caching
- **Generated visitors** - Type-safe tree traversal
- **Generated matchers** - Exhaustive pattern matching
- **Powerful mutation operations** - Safe AST modifications for optimizations

## Quick Start

### Installation

Requires Java 25.

Include the library via JitPack:

```gradle
dependencies {
    compileOnly 'com.github.peterzeller:abstractsyntaxgen:0.9.0'
}
```

### Basic Usage

1. **Define your AST** in a `.parseq` file:

```parseq
package mycompiler.ast

typeprefix: MC

abstract syntax:

Expr = 
    BinaryExpr(Expr left, Operator op, Expr right)
  | IntLiteral(int value)
  | VarRef(String name)

Operator = Plus() | Minus() | Times()

StatementList * Statement

attributes:

Expr.evaluate()
    returns int
    implemented by mycompiler.Evaluator.evaluate
```

2. **Generate classes** using Gradle:

```gradle
// Define directories and file patterns
def parseqFiles = fileTree(dir: 'src/main/resources', include: '**/*.parseq')
def genDir = file("$buildDir/generated/sources/ast/java")
def pkgPattern = ~/package\s+(\S+)\s*;?/

// Add generated sources to source sets
sourceSets {
    main {
        java {
            srcDirs += genDir
        }
    }
}

// AST generation task
tasks.register('genAst') {
    description = 'Generate AST classes from .parseq files'
    group = 'build'
    
    dependsOn 'compileJava'
    
    inputs.files(parseqFiles)
    outputs.dir(genDir)
    
    doFirst {
        delete genDir
        genDir.mkdirs()
    }
    
    doLast {
        ExecOperations execOps = project.services.get(ExecOperations)
        parseqFiles.files.each { File f ->
            String contents = f.getText('UTF-8')
            def m = pkgPattern.matcher(contents)
            String pkg = m.find() ? m.group(1) : ""
            
            execOps.javaexec {
                classpath = sourceSets.main.runtimeClasspath
                mainClass.set('asg.Main')
                args(f.absolutePath, genDir.absolutePath)
            }
        }
    }
}

// Make compilation depend on AST generation
compileJava {
    dependsOn genAst
}

// Clean generated files
clean {
    delete genDir
}
```

3. **Use the generated AST**:

```java
import static mycompiler.ast.MC.*;

// Create AST: (5 + 3) * 2
var expr = BinaryExpr(
    BinaryExpr(IntLiteral(5), Plus(), IntLiteral(3)),
    Times(),
    IntLiteral(2)
);

// Evaluate
int result = expr.evaluate(); // 16
```

## AST Mutation Guide

This library provides capabilities for AST mutations, essential for compiler optimizations like constant folding, dead code elimination, and function inlining.

### Core Mutation Principles

1. **Tree Invariant**: Each node can have at most one parent
2. **Null Safety**: No null references in tree structure (except for `ref` fields)
3. **Parent Tracking**: Parent relationships are automatically maintained

### Basic Mutations

#### 1. Modifying Node Properties

```java
var expr = BinaryExpr(IntLiteral(5), Plus(), IntLiteral(3));

// Change operator
expr.setOp(Times()); // Now: 5 * 3

// Replace operand  
expr.setRight(IntLiteral(10)); // Now: 5 * 10
```

#### 2. Replacing Nodes

```java
var list = StatementList(stmt1, stmt2, stmt3);
var oldStmt = list.get(1);
var newStmt = Assignment("x", IntLiteral(42));

// Replace using the node
oldStmt.replaceBy(newStmt);

// Or replace using parent
list.set(1, newStmt);
```

**Replacing by several nodes (splicing)**

A node in a list can be replaced by any number of nodes, which take its place in order; no node removes it.
The new nodes must not be in a tree, and nothing changes if one of them is.

```java
oldStmt.replaceByAll(List.of(stmtA, stmtB));   // one node
oldStmt.replaceByAll(List.of());                // removes it
```

To replace many nodes of one list, give `replaceEach` a map from the node to its replacements. It makes one pass
over the list however many nodes are replaced, where one `replaceByAll` after another is a pass each. The keys are
looked up as the map compares them, so use an `IdentityHashMap` to replace by identity.

```java
Map<Statement, List<Statement>> replacements = new IdentityHashMap<>();
replacements.put(dead, List.of());                       // remove
replacements.put(call, List.of(setup, call.copy()));     // replace by two
int replaced = list.replaceEach(replacements);           // the number of nodes replaced
```

#### 3. Moving Nodes Between Trees

**Problem**: Direct movement violates tree invariant
```java
var stmt = Assignment("x", IntLiteral(5));
var list1 = StatementList(stmt);
var list2 = StatementList();

// ❌ This will throw an error:
list2.add(stmt); // Error: Cannot change parent
```

**Solution 1**: Copy the node
```java
list2.add(stmt.copy()); // ✅ Creates a new tree
```

**Solution 2**: Use `removeAll()` for collections
```java
var statements = list1.removeAll(); // Removes all, clears parents
list2.addAll(statements); // ✅ Now they can be added elsewhere
```

**Solution 3**: Manual parent clearing
```java
stmt.setParent(null); // Clear parent first
list2.add(stmt); // ✅ Now it can be moved
```

### Knowing What Changed: Modification Counts

An analysis which is kept between passes, or a pass which only has to look at what changed, needs to know whether a
part of the tree is the part it saw. A spec can name the constructors which count the modifications of themselves
and of everything below them:

```
package my.ast
typeprefix: My
modification counts: Function, Program

abstract syntax:
...
```

`Function` and `Program` have an `int modificationCount()` then. It goes up by one for each change of the element or
of anything below it, and each element above which counts changes too (a function and the program it is in):

```java
int seen = function.modificationCount();
// ... passes ...
if (function.modificationCount() == seen) {
    // nothing in it changed: what you computed from it is still right
}
```

* A change is a setter (of a child or of any other field), anything done to a list (add, remove, set, clear, sort,
  `replaceEach`, an iterator, a `subList`), and a replacement (`replaceBy`, `replaceByAll`). Reading counts nothing.
* A node which is moved out of a tree counts for the tree it was in, and for the tree it is put into.
* A copy starts at zero, and building a tree (the factory methods) counts nothing. What is changed in a tree which
  is not a part of a counting element counts for no one.
* It wraps around after 2^32 modifications; compare for equality.
* A spec without `modification counts:` gets no counting code at all.

### Advanced Mutations

#### 1. Constant Folding

```java
public Expr foldConstants(Expr expr) {
    return expr.match(new Expr.Matcher<Expr>() {
        @Override
        public Expr case_BinaryExpr(BinaryExpr binary) {
            if (binary.getLeft() instanceof IntLiteral &&
                binary.getRight() instanceof IntLiteral &&
                binary.getOp() instanceof Plus) {
                
                int left = ((IntLiteral) binary.getLeft()).getValue();
                int right = ((IntLiteral) binary.getRight()).getValue();
                return IntLiteral(left + right);
            }
            return binary;
        }
        
        // ... other cases
    });
}
```

#### 2. Dead Code Elimination

```java
public Program eliminateDeadCode(Program program) {
    var newStatements = StatementList();
    
    for (Statement stmt : program.getStatements()) {
        if (stmt instanceof IfStatement) {
            var ifStmt = (IfStatement) stmt;
            if (ifStmt.getCondition() instanceof BoolLiteral) {
                var condition = (BoolLiteral) ifStmt.getCondition();
                if (condition.getValue()) {
                    // Always true - keep then branch
                    var thenStmts = ifStmt.getThenBranch().removeAll();
                    newStatements.addAll(thenStmts);
                } else {
                    // Always false - keep else branch
                    var elseStmts = ifStmt.getElseBranch().removeAll();
                    newStatements.addAll(elseStmts);
                }
                continue;
            }
        }
        newStatements.add(stmt.copy());
    }
    
    return Program(newStatements);
}
```

#### 3. Function Inlining with Side Effects

```java
public Program inlineFunction(Program program, String funcName) {
    // Find calls to the function
    program.accept(new Element.DefaultVisitor() {
        @Override
        public void visit(FunctionCall call) {
            if (call.getFuncName().equals(funcName)) {
                // Save variables that might be modified
                var savedVars = saveVariables(call);
                
                // Inline function body
                var inlinedStmts = inlineFunctionBody(call);
                
                // Replace call with inlined statements
                replaceCallWithStatements(call, savedVars, inlinedStmts);
            }
        }
    });
    
    return program;
}
```

### Working with References

Use `ref` fields for cross-references that don't follow tree structure:

```parseq
Statement = 
    VarDecl(String name, Expr initializer)
  | Assignment(ref VarDecl target, Expr value)
  | VarAccess(ref VarDecl variable)
```

```java
// References can be null initially
var varAccess = VarAccess(null);

// Set reference after name resolution
var varDecl = findVariable("x");
varAccess.setVariable(varDecl);

// Copy with references
var copy = program.copyWithRefs(); // Maintains reference integrity
```

### Best Practices

1. **Use `copy()` when building new trees** to avoid parent conflicts
2. **Use `removeAll()` when moving collections** to transfer ownership
3. **Use visitors for tree traversal** - they're type-safe and exhaustive
4. **Use matchers for transformations** - they ensure all cases are handled
5. **Test mutations thoroughly** - verify parent relationships and semantic correctness
6. **Use `copyWithRefs()` for reference-heavy trees** - it maintains reference integrity
7. **Use `Element.IterativeVisitor#traverse` for deeply nested or untrusted trees** - it avoids recursion limits

## Performance

What the generated code costs, and why it is built the way it is. `src/test/java/test/bench/AstBench.java` measures
all of it (run its `main` on the test classes; it prints the heap per node and the time per node of a walk, a copy and
a comparison):

- **A list is an object with its own array.** It does not wrap an `ArrayList`, which was a second object per list and
  a second load for each element. A list which is filled in one go gets an array of exactly its size; one which grows
  one element at a time starts with room for three. A node of a tree costs about 28 bytes on Java 27 (compact object
  headers), of which a list node is 32.
- **`copy()` and `structuralEquals()` do not ask a node which type it is.** Each generated element copies and compares
  itself (`zzCopy`, `zzStructuralEquals`), recursing for the first 256 levels; a tree which is deeper continues in an
  iterative loop, so the depth of a tree is still not limited by the stack. This is about twice as fast as a search
  for the type of each node, and avoids traversal scratch allocations for a small tree. The iterative comparison
  delegates a custom node's entire subtree to its `structuralEquals` implementation, just as the recursive path does.
- **`copyWithRefs()` maps every copied node, but repairs only copies with reference fields.** Generated virtual methods
  remap those fields after all targets have been copied, including later siblings and leaves; external and null
  references are preserved. Iterative copying reserves each list's capacity once and does not schedule leaves on its
  work stacks. `src/test/java/test/bench/ReferenceCopyBench.java` measures this path separately, with warmup rounds,
  median times and allocated bytes per node. On a synthetic 102,001-node tree with one reference per four declarations,
  three interleaved comparisons on OpenJDK 25 (Parallel GC, `-Xmx2g`, 24 measured rounds per JVM) reduced reference-copy
  time from 60.6–66.5 to 46.6–50.0 ns/node and allocation from 89.4 to 75.2 bytes/node. These are microbenchmark results,
  not a measurement of an additional WurstScript build speedup.
- **List spliterators bind on traversal and check structural modifications.** Streams see changes made before their
  terminal operation starts; split traversals preserve order and size and detect subsequent structural changes.
- **A `DefaultVisitor` which does not override a list's `visit` method visits the elements of the lists directly**,
  from the element above them. It visits the same elements in the same order; a visitor which overrides the visit of
  a list is told of every list, as before.
- A walk over a tree is bound by memory, not by the dispatch: about 20 ns for each node of a tree which does not fit in the
  cache, whether the walk is a visitor or a loop over `size()` and `get(i)`. The way to make it faster is a smaller tree
  or fewer walks.


## Documentation

- [Full Documentation](doc/ast.pdf) - Comprehensive guide with examples
- [API Reference](https://jitpack.io/com/github/peterzeller/abstractsyntaxgen/latest/javadoc/) - Generated Javadocs



## Acknowledgments

- Inspired by the visitor pattern and algebraic data types
- Built for the [WurstScript](https://github.com/wurstscript/WurstScript) compiler

