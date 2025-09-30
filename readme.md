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


## Documentation

- [Full Documentation](doc/ast.pdf) - Comprehensive guide with examples
- [API Reference](https://jitpack.io/com/github/peterzeller/abstractsyntaxgen/latest/javadoc/) - Generated Javadocs



## Acknowledgments

- Inspired by the visitor pattern and algebraic data types
- Built for the [WurstScript](https://github.com/wurstscript/WurstScript) compiler

