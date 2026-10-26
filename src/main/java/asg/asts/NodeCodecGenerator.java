package asg.asts;

import asg.asts.ast.*;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Generates the schema boundary; clients choose immutable values, symbol keys and ownership policy. */
final class NodeCodecGenerator {
    private final Program program;
    private final Function<String, String> type;
    private final BiPredicate<AstEntityDefinition, FieldDef> hasField;
    private final String prefix;
    private final String element;

    NodeCodecGenerator(Program program, Function<String, String> type, BiPredicate<AstEntityDefinition, FieldDef> hasField) {
        this.program = program;
        this.type = type;
        this.hasField = hasField;
        this.prefix = program.getTypePrefix();
        this.element = prefix + "Element";
    }

    void write(StringBuilder out) {
        out.append("/** Schema codec only: the encoder must detach metadata and encode references as keys.\n")
            .append(" * Does not traverse reference targets, retain nodes, or cache computed attributes.\n")
            .append(" * Data is session/schema-local, not a durable serialization format. */\n")
            .append("@SuppressWarnings(\"unchecked\")\npublic final class ").append(prefix).append("NodeCodec {\n")
            .append("    private ").append(prefix).append("NodeCodec() {}\n")
            .append("    /** V must be immutable and detached from the input graph. Nulls must also be encoded as values. */\n")
            .append("    public record Data<V>(String kind, List<V> fields, List<V> items) {\n")
            .append("        public Data { Objects.requireNonNull(kind); fields = List.copyOf(fields); items = List.copyOf(items); }\n    }\n")
            .append("    public interface Encoder<V> {\n")
            .append("        V child(").append(element).append(" value);\n")
            .append("        V reference(").append(element).append(" value);\n")
            .append("        default V reference(String field, ").append(element).append(" value) { return reference(value); }\n")
            .append("        V attribute(String field, Object value);\n")
            .append("        default V externalReference(String field, Object value) { return attribute(field, value); }\n    }\n")
            .append("    /** Each child must be newly owned; resolve references before exposing the completed graph to a pass. */\n")
            .append("    public interface Decoder<V> {\n")
            .append("        ").append(element).append(" child(V value);\n")
            .append("        ").append(element).append(" reference(V value);\n")
            .append("        default ").append(element).append(" restoreReference(String field, V value) { return reference(value); }\n")
            .append("        Object restoreAttribute(String field, V value);\n")
            .append("        default Object restoreExternalReference(String field, V value) { return restoreAttribute(field, value); }\n    }\n")
            .append("    public static <V> Data<V> encode(").append(element).append(" node, Encoder<V> encoder) {\n")
            .append("        Objects.requireNonNull(encoder);\n        return switch (node) {\n");
        for (ConstructorDef constructor : program.constructorDefs) {
            String name = constructor.getName(prefix);
            out.append("            case ").append(name).append(" n -> encode").append(name).append("(n, encoder);\n");
        }
        for (ListDef list : program.listDefs) {
            String name = list.getName(prefix);
            out.append("            case ").append(name).append(" n -> encode").append(name).append("(n, encoder);\n");
        }
        out.append("            default -> throw new IllegalArgumentException(\"No generated codec for \" + node.getClass());\n        };\n    }\n")
            .append("    public static <V> ").append(element).append(" decode(Data<V> data, Decoder<V> decoder) {\n")
            .append("        Objects.requireNonNull(decoder);\n        return switch (data.kind()) {\n");
        for (ConstructorDef constructor : program.constructorDefs) decodeCase(out, constructor);
        for (ListDef list : program.listDefs) decodeCase(out, list);
        out.append("            default -> throw new IllegalArgumentException(\"Unknown node kind: \" + data.kind());\n        };\n    }\n");
        for (ConstructorDef constructor : program.constructorDefs) constructor(out, constructor);
        for (ListDef list : program.listDefs) list(out, list);
        graphBoundary(out);
        out.append("}\n");
    }

    private void graphBoundary(StringBuilder out) {
        StringBuilder schema=new StringBuilder(program.getPackageName()).append(':').append(prefix);
        for(ConstructorDef c:program.constructorDefs) {
            schema.append(";ctor:").append(c.getName());
            for(Parameter p:c.parameters) schema.append(':').append(p.name).append('/').append(p.getTyp()).append('/').append(p.isRef).append('/').append(p.isTracked()).append('/').append(p.isSource()).append('/').append(p.isIgnoreEquality());
        }
        schema.append(";counted:").append(String.join(",",program.countedTypes));
        for(CaseDef c:program.caseDefs) { schema.append(";case:").append(c.getName());for(Alternative a:c.alternatives) schema.append(':').append(a.name); }
        for(ListDef l:program.listDefs) schema.append(";list:").append(l.getName()).append('/').append(l.itemType).append('/').append(l.ref);
        for(FieldDef f:program.fieldDefs) schema.append(";field:").append(f.getTyp()).append('.').append(f.getFieldName()).append('/').append(f.getFieldType()).append('/').append(f.isTracked()).append('/').append(f.isSource());
        String hash;
        try { hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(schema.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        out.append("    public static final String SCHEMA = \""+hash+"\";\n");
        out.append("    static "+element+" allocate(String kind) { return switch(kind) {\n");
        for(AstBaseTypeDefinition def:definitions()) out.append("        case \""+def.getName(prefix)+"\" -> new "+def.getName(prefix)+"Impl();\n");
        out.append("        default -> throw new IllegalArgumentException(\"Unknown node kind: \"+kind);\n    }; }\n");
        out.append("    static <V> void populate("+element+" node, Data<V> data, Decoder<V> d) {\n        node.zzBeginMetadata();\n        try { switch(data.kind()) {\n");
        for(AstBaseTypeDefinition def:definitions()) {
            String name=def.getName(prefix); List<FieldDef> fields=fields(def);
            int size=(def instanceof ConstructorDef c ? c.parameters.size() : 0)+fields.size();
            out.append("        case \""+name+"\" -> {\n            if(data.fields().size() != "+size+") throw new IllegalArgumentException(\"Invalid fields for "+name+"\");\n            "+name+" result=("+name+")node;\n");
            int i=0;
            if(def instanceof ConstructorDef c) {
                out.append("            if(!data.items().isEmpty()) throw new IllegalArgumentException(\"Unexpected list items\");\n");
                for(Parameter p:c.parameters) out.append("            result.set"+upper(p.name)+"("+decode(name,p.name,p.getTyp(),p.isRef,i++)+");\n");
            } else if(def instanceof ListDef l) {
                out.append("            for(V item:data.items()) result.add(("+type.apply(l.itemType)+")d."+(l.ref ? "restoreReference" : "child")+"("+(l.ref ? "\""+name+".items\", " : "")+"item));\n");
            }
            restoreFields(out,name,fields,i);
            out.append("        }\n");
        }
        out.append("        default -> throw new IllegalArgumentException(\"Unknown node kind: \"+data.kind());\n        } } finally { node.zzEndMetadata(); }\n    }\n");
        out.append("    public static Set<Integer> sourceFields(String kind) { return switch(kind) {\n");
        for(AstBaseTypeDefinition def:definitions()) {
            List<String> indices=new java.util.ArrayList<>();int i=0;
            if(def instanceof ConstructorDef c) for(Parameter p:c.parameters) { if(p.isSource()) indices.add(""+i);i++; }
            for(FieldDef f:fields(def)) { if(f.isSource()) indices.add(""+i);i++; }
            out.append("        case \""+def.getName(prefix)+"\" -> Set.of("+String.join(", ",indices)+");\n");
        }
        out.append("        default -> throw new IllegalArgumentException(\"Unknown node kind: \"+kind);\n    }; }\n");
    }
    private List<AstBaseTypeDefinition> definitions() {
        List<AstBaseTypeDefinition> result=new java.util.ArrayList<>(program.constructorDefs);result.addAll(program.listDefs);return result;
    }

    private void decodeCase(StringBuilder out, AstEntityDefinition definition) {
        String name = definition.getName(prefix);
        out.append("            case \"").append(name).append("\" -> decode").append(name).append("(data, decoder);\n");
    }

    private List<FieldDef> fields(AstEntityDefinition definition) {
        return program.fieldDefs.stream().filter(field -> hasField.test(definition, field)).toList();
    }

    private static String upper(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private String method(String typeName, boolean reference) {
        return program.hasElement(typeName) ? reference ? "reference" : "child" : reference ? "externalReference" : "attribute";
    }

    private String encode(String kind, String field, String typeName, boolean reference) {
        String method = method(typeName, reference);
        return "e." + method + "(" + (!method.equals("child") ? "\"" + kind + "." + field + "\", " : "")
            + "n.get" + upper(field) + "())";
    }

    private String decode(String kind, String field, String typeName, boolean reference, int index) {
        String method = method(typeName, reference);
        return "(" + type.apply(typeName) + ") d." + (method.equals("attribute") ? "restoreAttribute" : method.equals("externalReference") ? "restoreExternalReference" : method.equals("reference") ? "restoreReference" : method) + "("
            + (!method.equals("child") ? "\"" + kind + "." + field + "\", " : "") + "data.fields().get(" + index + "))";
    }

    private void constructor(StringBuilder out, ConstructorDef constructor) {
        String name = constructor.getName(prefix);
        List<FieldDef> fields = fields(constructor);
        out.append("    private static <V> Data<V> encode").append(name).append("(").append(name).append(" n, Encoder<V> e) {\n")
            .append("        return new Data<>(\"").append(name).append("\", List.of(");
        boolean first = true;
        for (Parameter parameter : constructor.parameters) {
            if (!first) out.append(", ");
            first = false;
            out.append(encode(name, parameter.name, parameter.getTyp(), parameter.isRef));
        }
        for (FieldDef field : fields) {
            if (!first) out.append(", ");
            first = false;
            // Attribute fields are not owned children, even when their declared type is another node.
            out.append("e.attribute(\"").append(name).append('.').append(field.getFieldName()).append("\", n.get")
                .append(upper(field.getFieldName())).append("())");
        }
        out.append("), List.of());\n    }\n")
            .append("    private static <V> ").append(name).append(" decode").append(name).append("(Data<V> data, Decoder<V> d) {\n")
            .append("        if (data.fields().size() != ").append(constructor.parameters.size() + fields.size())
            .append(" || !data.items().isEmpty()) throw new IllegalArgumentException(\"Invalid data for ").append(name).append("\");\n")
            .append("        ").append(name).append(" result = ").append(upper(program.getFactoryName())).append('.').append(constructor.getName()).append('(');
        for (int i = 0; i < constructor.parameters.size(); i++) {
            if (i > 0) out.append(", ");
            Parameter parameter = constructor.parameters.get(i);
            out.append(decode(name, parameter.name, parameter.getTyp(), parameter.isRef, i));
        }
        out.append(");\n");
        restoreFields(out, name, fields, constructor.parameters.size());
        out.append("        return result;\n    }\n");
    }

    private void restoreFields(StringBuilder out, String name, List<FieldDef> fields, int start) {
        for (int i = 0; i < fields.size(); i++) {
            FieldDef field = fields.get(i);
            out.append("        result.set").append(upper(field.getFieldName())).append("((").append(type.apply(field.getFieldType()))
                .append(") d.restoreAttribute(\"").append(name).append('.').append(field.getFieldName())
                .append("\", data.fields().get(").append(start + i).append(")));\n");
        }
    }

    private void list(StringBuilder out, ListDef list) {
        String name = list.getName(prefix);
        List<FieldDef> fields = fields(list);
        out.append("    private static <V> Data<V> encode").append(name).append("(").append(name).append(" n, Encoder<V> e) {\n")
            .append("        List<V> items = new ArrayList<>(n.size());\n")
            .append("        for (var item : n) items.add(e.").append(list.ref ? "reference" : "child").append("(").append(list.ref ? "\""+name+".items\", " : "").append("item));\n")
            .append("        return new Data<>(\"").append(name).append("\", List.of(");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) out.append(", ");
            FieldDef field = fields.get(i);
            out.append("e.attribute(\"").append(name).append('.').append(field.getFieldName()).append("\", n.get")
                .append(upper(field.getFieldName())).append("())");
        }
        out.append("), items);\n    }\n")
            .append("    private static <V> ").append(name).append(" decode").append(name).append("(Data<V> data, Decoder<V> d) {\n")
            .append("        if (data.fields().size() != ").append(fields.size()).append(") throw new IllegalArgumentException(\"Invalid data for ")
            .append(name).append("\");\n")
            .append("        ").append(name).append(" result = ").append(upper(program.getFactoryName())).append('.').append(list.getName()).append("();\n")
            .append("        for (V item : data.items()) result.add((").append(type.apply(list.itemType)).append(") d.")
            .append(list.ref ? "restoreReference" : "child").append("(").append(list.ref ? "\""+name+".items\", " : "").append("item));\n");
        restoreFields(out, name, fields, 0);
        out.append("        return result;\n    }\n");
    }
}
