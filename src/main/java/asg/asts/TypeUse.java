package asg.asts;

import java.util.*;
import java.util.function.Function;

/** The Java type grammar accepted by parseq, retained structurally for schema contracts. */
record TypeUse(String name, boolean nullable, List<TypeUse> arguments) {
    static TypeUse parse(String text) {
        int[] offset={0}; TypeUse result=parse(text,offset);
        if(offset[0]!=text.length()) throw new IllegalArgumentException("Invalid schema type: " + text);
        return result;
    }
    private static TypeUse parse(String text,int[] offset) {
        while(offset[0]<text.length() && Character.isWhitespace(text.charAt(offset[0]))) offset[0]++;
        boolean nullable=text.startsWith("@org.eclipse.jdt.annotation.Nullable ",offset[0]);
        if(nullable) offset[0]+="@org.eclipse.jdt.annotation.Nullable ".length();
        int start=offset[0];
        while(offset[0]<text.length() && (Character.isJavaIdentifierPart(text.charAt(offset[0])) || text.charAt(offset[0])=='.')) offset[0]++;
        if(start==offset[0]) throw new IllegalArgumentException("Invalid schema type: " + text);
        String name=text.substring(start,offset[0]); List<TypeUse> args=new ArrayList<>();
        if(offset[0]<text.length() && text.charAt(offset[0])=='<') {
            offset[0]++;
            do {
                args.add(parse(text,offset));
                if(offset[0]>=text.length()) throw new IllegalArgumentException("Unclosed schema type: " + text);
                char separator=text.charAt(offset[0]++);
                if(separator=='>') break;
                if(separator!=',') throw new IllegalArgumentException("Invalid schema type: " + text);
            } while(true);
        }
        return new TypeUse(name,nullable,List.copyOf(args));
    }
    String render(Function<String,String> names) {
        return (nullable ? "@org.eclipse.jdt.annotation.Nullable " : "")+names.apply(name)
            +(arguments.isEmpty() ? "" : "<"+String.join(", ",arguments.stream().map(a->a.render(names)).toList())+">");
    }
    void validateTracked(String field, boolean reference) {
        String raw=name.startsWith("java.util.") ? name.substring(10) : name;
        int arity=raw.equals("Map") ? 2 : Set.of("List","Set","Collection").contains(raw) ? 1 : -1;
        if(reference || arguments.size()!=arity) throw new IllegalArgumentException("Invalid @tracked field " + field + ": expected List<T>, Set<T>, Collection<T> or Map<K,V> without ref; found " + render(Function.identity()));
    }
}
