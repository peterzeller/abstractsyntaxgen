# Abstract Syntax Generator

Generate Java classes for representing mutable abstract syntax trees.

- Order sorted terms
- Simulated union types via interfaces
- Consistency checks: Enforces that each element only appears once in the tree.
- Cached and uncached attributes
- Generated visitors
- Generated matchers for exhaustive pattern matching
- Null safety

## Documentation

Include the library via Jitpack:

[![](https://jitpack.io/v/peterzeller/abstractsyntaxgen.svg)](https://jitpack.io/#peterzeller/abstractsyntaxgen)

For example using these blocks in your Gradle configuration:

```
dependencies {

    compileOnly 'com.github.peterzeller:abstractsyntaxgen:0.3.2' // adapt to current version

}

task genAst {
    description = 'Compile ast specifications'
    fileTree(dir: 'parserspec', include: '**/*.ast').each { file ->
        Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+(\\S+)\\s*;")
        String fileContents = file.text

        Matcher matcher = PACKAGE_PATTERN.matcher(fileContents)
        String packageName = ""
        if (matcher.find()) {
            packageName = matcher.group(1)
        }

        String targetDir = "$genDir/" + packageName.replace(".", "/")

        inputs.file(file)
        outputs.dir(targetDir)

        doLast {
            javaexec {
                classpath configurations.compileClasspath
                main = "asg.Main"
                args = [file, targetDir]
            }
        }
    }
}

compileJava.dependsOn genAst
```

Further documentation is available under [doc/ast.pdf](doc/ast.pdf).