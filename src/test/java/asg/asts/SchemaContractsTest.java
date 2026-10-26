package asg.asts;

import asg.asts.ast.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SchemaContractsTest {
    @TempDir Path output;
    @Test void invalidTrackedSchemasFailBeforeWritingAnyGeneratedFile() throws Exception {
        for(String type:List.of("java.util.List", "String", "java.util.Map<String>","java.util.ArrayList<String>")) {
            Program program=new Program("test.invalid");var c=new ConstructorDef("Node");c.addParam(false,false,true,type,"payload");program.addConstructorDef(c);
            var failure=assertThrows(IllegalArgumentException.class,()->new Generator(new FileGenerator(output.toFile()),program,output.toString()).generate());
            assertTrue(failure.getMessage().contains("Node.payload"));
            try(var files=Files.list(output)) { assertEquals(0,files.count()); }
        }
    }
    private Map<String,String> generate(String name,boolean source) throws Exception {
        Program program=new Program("test.schema");program.addCountedType("Node");
        var c=new ConstructorDef("Node");c.addParam(false,false,"String","text");c.parameters.get(0).setSource(source);program.addConstructorDef(c);
        Path destination=output.resolve(name);new Generator(new FileGenerator(destination.toFile()),program,destination.toString()).generate();
        Map<String,String> files=new TreeMap<>();try(var paths=Files.list(destination)) { for(var file:paths.toList()) files.put(file.getFileName().toString(),Files.readString(file)); }return files;
    }
    @Test void generationIsByteIdenticalAndSchemaIdentityIncludesFieldRoles() throws Exception {
        var first=generate("first",false);assertEquals(first,generate("second",false));
        assertNotEquals(first.get("NodeCodec.java"),generate("source",true).get("NodeCodec.java"));
    }
    @Test void trackedReferenceRoleIsRejectedAndNestedGeneratedTypesAreRendered() {
        assertEquals("java.util.Map<XNode, java.util.List<XNode>>",TypeUse.parse("java.util.Map<Node, java.util.List<Node>>").render(n->n.equals("Node") ? "XNode" : n));
        assertThrows(IllegalArgumentException.class,()->TypeUse.parse("java.util.List<String>").validateTracked("Node.payload",true));
    }
}
