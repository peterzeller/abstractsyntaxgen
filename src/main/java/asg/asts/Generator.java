package asg.asts;

import asg.asts.ast.*;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.Map.Entry;

@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
public class Generator {


    private Multimap<CaseDef, AstBaseTypeDefinition> baseTypes = HashMultimap.create();
    private Multimap<AstEntityDefinition, AstEntityDefinition> directChildTypes = HashMultimap.create();

    private Multimap<AstEntityDefinition, AstEntityDefinition> directParentType = HashMultimap.create();
    private Multimap<AstEntityDefinition, AstEntityDefinition> directSubTypes = HashMultimap.create();
    private Multimap<AstEntityDefinition, AstEntityDefinition> directSuperTypes = HashMultimap.create();
    private Multimap<AstBaseTypeDefinition, CaseDef> interfaceTypes = HashMultimap.create();
    private String mainName;
    private String packageName;
    private Program prog;
    private Multimap<AstEntityDefinition, AstEntityDefinition> transientChildTypes = HashMultimap.create();
    private Multimap<AstEntityDefinition, AstEntityDefinition> transientSubTypes;
    private Multimap<AstEntityDefinition, AstEntityDefinition> transientSuperTypes;

    private Map<String, Parameter> parameters = Maps.newLinkedHashMap();
    private final FileGenerator fileGenerator;
    private String typePrefix;
    private CaseDef commonSuperType;


    public Generator(FileGenerator fileGenerator, Program prog, String p_outputFolder) {
        this.fileGenerator = fileGenerator;

        this.prog = prog;
        this.typePrefix = prog.getTypePrefix();
        this.packageName = prog.getPackageName();
        this.mainName = prog.getFactoryName();
    }


    private void caclulateCaseDefBaseTypes(CaseDef caseDef) {
        if (baseTypes.containsKey(caseDef)) {
            return; // already calculated
        }
        for (AstEntityDefinition sub : directSubTypes.get(caseDef)) {
            if (sub instanceof AstBaseTypeDefinition) {
                baseTypes.put(caseDef, (AstBaseTypeDefinition) sub);
            } else if (sub instanceof CaseDef) {
                CaseDef caseDef2 = (CaseDef) sub;
                caclulateCaseDefBaseTypes(caseDef2);
                for (AstBaseTypeDefinition sub2 : baseTypes.get(caseDef2)) {
                    baseTypes.put(caseDef, sub2);
                }
            }
        }
    }

    private Set<Parameter> calculateAttributes(CaseDef c) {
        Set<Parameter> commonAttributes = null;
        for (AstBaseTypeDefinition base : baseTypes.get(c)) {
            if (base instanceof ConstructorDef) {
                ConstructorDef baseClass = (ConstructorDef) base;
                Set<Parameter> attributes = Sets.newLinkedHashSet();
                attributes.addAll(baseClass.parameters);
                if (commonAttributes == null) {
                    commonAttributes = attributes;
                } else {
                    commonAttributes = Sets.intersection(commonAttributes, attributes);
                }
            } else if (base instanceof ListDef) {
                return Sets.newLinkedHashSet();
            } else {
                throw new Error("Case not possible.");
            }
        }
        if (commonAttributes == null) {
            commonAttributes = Sets.newLinkedHashSet();
        }
        return commonAttributes;
    }

    private void calculateContainments() {
        for (ConstructorDef c : prog.constructorDefs) {
            for (Parameter a : c.parameters) {
                if (!a.isRef) {
                    addContainmentInfo(c, prog.getElement(a.getTyp()));
                }
            }
        }
        for (ListDef l : prog.listDefs) {
            if (!l.ref) {
                addContainmentInfo(l, prog.getElement(l.itemType));
            }
        }

        calculateTransientChildTypes();
    }

    private void calculateTransientChildTypes() {
        transientChildTypes = HashMultimap.create();
        transientChildTypes.putAll(directChildTypes);
        boolean changed;
        do {
            HashMultimap<AstEntityDefinition, AstEntityDefinition> newTransitions = HashMultimap.create();
            for (Entry<AstEntityDefinition, AstEntityDefinition> e : transientChildTypes.entries()) {
                AstEntityDefinition parent = e.getKey();
                AstEntityDefinition child = e.getValue();

                // reflexive:
                newTransitions.put(parent, parent);
                newTransitions.put(child, child);

                // add transitive childs
                for (AstEntityDefinition trChild : transientChildTypes.get(child)) {
                    newTransitions.put(parent, trChild);
                }

                // add subtypes of child:
                for (AstEntityDefinition sub : transientSubTypes.get(child)) {
                    newTransitions.put(parent, sub);
                }

                // add supertypes of parent:
                for (AstEntityDefinition sup : transientSuperTypes.get(parent)) {
                    newTransitions.put(sup, child);
                }
            }
            changed = transientChildTypes.putAll(newTransitions);
        } while (changed);
        // must terminate because there are only finitely many possible transitions
        // and every iteration adds at least one
        // runtime might be quite bad however
    }

    private void addContainmentInfo(AstEntityDefinition parent, AstEntityDefinition child) {
        if (parent == null || child == null) {
            return;
        }
        directParentType.put(child, parent);
        directChildTypes.put(parent, child);
    }

    private void calculateSubTypes() {
        List<String> undefined = new ArrayList<>();

        for (CaseDef caseDef : prog.caseDefs) {
            for (Alternative alt : caseDef.alternatives) {
                AstEntityDefinition subType = prog.getElement(alt.name);
                if (subType == null) {
                    undefined.add("case '" + caseDef.getName() + "' -> alternative '" + alt.name + "'");
                    continue; // skip invalid entry
                }
                directSubTypes.put(caseDef, subType);
                directSuperTypes.put(subType, caseDef);
            }
        }

        if (!undefined.isEmpty()) {
            String msg = "Undefined AST types referenced in case alternatives:\n  - "
                    + String.join("\n  - ", undefined)
                    + "\nPlease declare these types (constructor/list/case) before using them.";
            throw new IllegalStateException(msg);
        }

        // calculate base types of interfaces:
        for (CaseDef caseDef : prog.caseDefs) {
            caclulateCaseDefBaseTypes(caseDef);
        }

        // calculate interfaces for base types:
        for (CaseDef caseDef : prog.caseDefs) {
            for (AstBaseTypeDefinition base : baseTypes.get(caseDef)) {
                interfaceTypes.put(base, caseDef);
            }
        }

        transientSubTypes = transientClosure(directSubTypes);
        transientSuperTypes = transientClosure(directSuperTypes);
    }

    private void createMatchMethods(AstBaseTypeDefinition c, StringBuilder sb) {
        // create match methods
        for (CaseDef superType : interfaceTypes.get(c)) {
            sb.append("    @Override public <T> T match(" + superType.getName(typePrefix) + ".Matcher<T> matcher) {\n");
            sb.append("        return matcher.case_" + c.getName() + "(this);\n");
            sb.append("    }\n");

            sb.append("    @Override public void match(" + superType.getName(typePrefix) + ".MatcherVoid matcher) {\n");
            sb.append("        matcher.case_" + c.getName() + "(this);\n");
            sb.append("    }\n\n");
        }
    }

    public void generate() {
        createFakeSuperclass();

        calculateProperties();
        calculateSubTypes();
        calculateContainments();

//        generatePackageInfo(); // TODO add flag
        generateStandardClasses();
        generateStandardList();
        generateCyclicDependencyError();


        generateInterfaceTypes();

        generateBaseClasses();

        generateLists();


        generateFactoryClass();

    }

    private void createFakeSuperclass() {
        commonSuperType = new CaseDef("Element");
        for (CaseDef d : prog.caseDefs) {
            commonSuperType.addAlternative(d.getName());
        }
        for (ConstructorDef d : prog.constructorDefs) {
            commonSuperType.addAlternative(d.getName());
        }
        for (ListDef d : prog.listDefs) {
            commonSuperType.addAlternative(d.getName());
        }
        prog.caseDefs.add(commonSuperType);
    }


    private void generatePackageInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(FileGenerator.PARSEQ_COMMENT + "\n");
        sb.append("@org.eclipse.jdt.annotation.NonNullByDefault\n");
        sb.append("package " + packageName + ";\n\n");

        fileGenerator.createFile("package-info.java", sb);
    }


    /**
     * calculate which properties exist
     */
    private void calculateProperties() {
        for (ConstructorDef c : prog.constructorDefs) {
            for (Parameter p : c.parameters) {
                Parameter oldP = parameters.put(p.name, p);
                if (oldP != null) {
                    if (!oldP.getTyp().equals(p.getTyp())) {
                        throw new Error("The property <" + p.name + "> has not the same type for each element: " + oldP.getTyp() + " and " + p.getTyp());
                    }
                }
            }
        }
    }

    private void generateBaseClass_Impl(ConstructorDef c) {
        StringBuilder sb = new StringBuilder();
        printProlog(sb);
        addSuppressWarningAnnotations(sb);
        sb.append("final class ").append(c.getName(typePrefix)).append("Impl implements ")
        .append(c.getName(typePrefix)).append("{\n");


        // create constructor
        createConstructor(c, sb);
        createShallowCopyMethod(c, sb);

        // get/set parent method:
        createGetSetParentMethods(sb);

        // replaceBy method:
        createReplaceByMethod(sb);

        // create getters and setters for parameters:
        createGetterAndSetterMethods(c, sb);

        //get method
        int childCount = createGetMethod(c, sb);
        // set method
        createSetMethod(c, sb);
        // forEachElement method
        createForEachMethod(c, sb);

        //size method
        createSizeMethod(sb, childCount);

        //copy method
        createCopyMethod(c, sb);

        // copyWithRefs method
        createCopyWithRefsMethod(c, sb);

        // clear attributes method
        createClearMethod(c, sb);


        // accept method for visitor
        createAcceptMethods(c, sb);

        // match methods for switch
        createMatchMethods(c, sb);

        // toString method
        createToString(c, sb);

        createStructuralEquals(c, sb);

        createAttributeImpl(c, sb);
        createFieldsImpl(c, sb);

        sb.append("}\n");
        fileGenerator.createFile(c.getName(typePrefix) + "Impl.java", sb);
    }

    private void createStructuralEquals(ConstructorDef c, StringBuilder sb) {
        sb.append("    public boolean structuralEquals(" + getCommonSupertypeType() + " e) {\n");
        sb.append("        return ").append(getTreeUtilType()).append(".structuralEquals(this, e);\n");
        sb.append("    }\n");
    }

    private void createFieldsImpl(AstBaseTypeDefinition c, StringBuilder sb) {
        for (FieldDef field : prog.fieldDefs) {
            if (!hasField(c, field)) {
                continue;
            }
            sb.append("    private " + field.getFieldType() + " " + field.getFieldName() + ";\n");
            sb.append("    /** " + field.getDoc() + "*/\n");
            sb.append("    public " + field.getFieldType() + " get" + toFirstUpper(field.getFieldName()) + "() {\n");
            sb.append("        return " + field.getFieldName() + ";\n");
            sb.append("    }\n");
            sb.append("    /** " + field.getDoc() + "*/\n");
            sb.append("    public void set" + toFirstUpper(field.getFieldName())
                    + "(" + field.getFieldType() + " " + field.getFieldName() + ") {\n");
            sb.append("        this." + field.getFieldName() + " = " + field.getFieldName() + ";\n");
            sb.append("    }\n");
        }
    }


    private void createAttributeImpl(AstBaseTypeDefinition c, StringBuilder sb) {
        for (AttributeDef attr : prog.attrDefs) {

            if (hasAttribute(c, attr)) {
                if (attr.parameters == null) {
                    sb.append("// circular = ").append(attr.circular).append("\n");
                    if (attr.circular == null) {
                        // ---------- NON-CIRCULAR CACHED ATTRIBUTE ----------
                        // State: 0 = uncached, 1 = computing (cycle), 2 = cached
                        sb.append("    private byte zzattr_").append(attr.attr).append("_state = 0;\n");
                        sb.append("    private ").append(attr.returns).append(" zzattr_").append(attr.attr).append("_cache;\n");
                        sb.append("    /** ").append(attr.comment).append("*/\n");
                        sb.append("    public ").append(attr.returns).append(" ").append(attr.attr).append("() {\n");
                        sb.append("        byte s = zzattr_").append(attr.attr).append("_state;\n");
                        sb.append("        if (s == 2) return zzattr_").append(attr.attr).append("_cache;\n");
                        sb.append("        if (s == 1) throw new CyclicDependencyError(this, \"").append(attr.attr).append("\");\n");
                        sb.append("        zzattr_").append(attr.attr).append("_state = 1;\n");
                        sb.append("        zzattr_").append(attr.attr).append("_cache = ")
                                .append(attr.implementedBy).append("((")
                                .append(c.getName(typePrefix)).append(")this);\n");
                        sb.append("        zzattr_").append(attr.attr).append("_state = 2;\n");
                        sb.append("        return zzattr_").append(attr.attr).append("_cache;\n");
                        sb.append("    }\n");
                    } else {
                        // ---------- CIRCULAR (FIXPOINT) CACHED ATTRIBUTE ----------
                        // States: 0 = uninitialized, 1 = iterating, 2 = fixed, 3 = touched-during-iteration
                        sb.append("    private byte zzattr_").append(attr.attr).append("_state = 0;\n");
                        sb.append("    private ").append(attr.returns).append(" zzattr_").append(attr.attr).append("_cache;\n");
                        sb.append("    /** ").append(attr.comment).append("*/\n");
                        sb.append("    public ").append(attr.returns).append(" ").append(attr.attr).append("() {\n");
                        sb.append("        if (zzattr_").append(attr.attr).append("_state == 2) {\n");
                        sb.append("            return zzattr_").append(attr.attr).append("_cache;\n");
                        sb.append("        }\n");
                        sb.append("        if (zzattr_").append(attr.attr).append("_state == 1")
                                .append(" || zzattr_").append(attr.attr).append("_state == 3) {\n");
                        sb.append("            // Mark that we were queried during iteration\n");
                        sb.append("            zzattr_").append(attr.attr).append("_state = 3;\n");
                        sb.append("            return zzattr_").append(attr.attr).append("_cache;\n");
                        sb.append("        }\n");
                        sb.append("        // Initialize and iterate to a fixpoint\n");
                        sb.append("        zzattr_").append(attr.attr).append("_state = 1;\n");
                        sb.append("        zzattr_").append(attr.attr).append("_cache = ").append(attr.circular).append("();\n");
                        sb.append("        while (true) {\n");
                        sb.append("            ").append(attr.returns).append(" r = ")
                                .append(attr.implementedBy).append("((")
                                .append(c.getName(typePrefix)).append(")this);\n");
                        sb.append("            if (zzattr_").append(attr.attr).append("_state == 3) {\n");
                        sb.append("                // Another access happened during iteration -> keep iterating until stable\n");
                        sb.append("                if (!(").append(equalityExpression(
                                "zzattr_" + attr.attr + "_cache", "r", attr.returns)).append(")) {\n");
                        sb.append("                    zzattr_").append(attr.attr).append("_cache = r;\n");
                        sb.append("                    // continue loop\n");
                        sb.append("                } else {\n");
                        sb.append("                    // Stabilized after re-entrancy\n");
                        sb.append("                    break;\n");
                        sb.append("                }\n");
                        sb.append("            } else {\n");
                        sb.append("                // Normal iteration step\n");
                        sb.append("                if (!(").append(equalityExpression(
                                "zzattr_" + attr.attr + "_cache", "r", attr.returns)).append(")) {\n");
                        sb.append("                    zzattr_").append(attr.attr).append("_cache = r;\n");
                        sb.append("                    // continue loop\n");
                        sb.append("                } else {\n");
                        sb.append("                    break;\n");
                        sb.append("                }\n");
                        sb.append("            }\n");
                        sb.append("            // Reset to 'iterating' for the next step (clears the 3-marker if it was set)\n");
                        sb.append("            zzattr_").append(attr.attr).append("_state = 1;\n");
                        sb.append("        }\n");
                        sb.append("        zzattr_").append(attr.attr).append("_state = 2;\n");
                        sb.append("        return zzattr_").append(attr.attr).append("_cache;\n");
                        sb.append("    }\n");
                    }
                } else {
                    // ---------- PARAMETERIZED (NON-CACHED) ATTRIBUTE ----------
                    sb.append("    /** ").append(attr.comment).append("*/\n");
                    sb.append("    public ").append(attr.returns).append(" ").append(attr.attr)
                            .append("(").append(printParams(attr.parameters)).append(") {\n");
                    if (attr.returns.equals("void")) {
                        sb.append("        ").append(attr.implementedBy).append("((")
                                .append(c.getName(typePrefix)).append(")this")
                                .append(printArgs(attr.parameters)).append(");\n");
                    } else {
                        sb.append("        return ").append(attr.implementedBy).append("((")
                                .append(c.getName(typePrefix)).append(")this")
                                .append(printArgs(attr.parameters)).append(");\n");
                    }
                    sb.append("    }\n");
                }
            }
        }
    }


    private String printArgs(List<Parameter> parameters2) {
        StringBuilder result = new StringBuilder();
        for (Parameter p : parameters2) {
            result.append(", ").append(p.name);
        }
        return result.toString();
    }

    private String printParams(List<Parameter> parameters2) {
        if (parameters2 == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Parameter p : parameters2) {
            if (!first) {
                result.append(", ");
            }
            result.append(printType(p.getTyp())).append(" ").append(p.name);
            first = false;
        }
        return result.toString();
    }

    private void createConstructor(ConstructorDef c, StringBuilder sb) {
        if (!c.parameters.isEmpty()) {
            sb.append("    " + c.getName(typePrefix) + "Impl() {}\n\n");
        }
        sb.append("    " + c.getName(typePrefix) + "Impl(");
        boolean first = true;
        for (Parameter p : c.parameters) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(printType(p.getTyp()) + " " + p.name);
            first = false;
        }
        sb.append(") {\n");
        for (Parameter p : c.parameters) {
            if (!JavaTypes.primitiveTypes.contains(p.getTyp()) && !p.isRef) {
                // add null checks for non primitive types:
                sb.append("        if (" + p.name + " == null)\n");
                sb.append("            throw new IllegalArgumentException(\"Element " + p.name + " must not be null.\");\n");
            }
        }
        for (Parameter p : c.parameters) {
            sb.append("        this." + p.name + " = " + p.name + ";\n");
        }
        for (Parameter p : c.parameters) {
            if (!JavaTypes.primitiveTypes.contains(p.getTyp())) {
                if (isGeneratedTyp(p.getTyp()) && !p.isRef) {
                    // we have a generated type.
                    // the new element has a new parent:
                    sb.append("        " + p.name + ".setParent(this);\n");
                }
            }
        }
        sb.append("    }\n\n");
    }

    private void createShallowCopyMethod(ConstructorDef c, StringBuilder sb) {
        String implType = c.getName(typePrefix) + "Impl";
        sb.append("    ").append(implType).append(" zzShallowCopy() {\n");
        sb.append("        ").append(implType).append(" result = new ").append(implType).append("();\n");
        for (Parameter p : c.parameters) {
            if (p.isRef || !prog.hasElement(p.getTyp())) {
                sb.append("        result.").append(p.name).append(" = this.").append(p.name).append(";\n");
            }
        }
        for (FieldDef field : prog.fieldDefs) {
            if (hasField(c, field)) {
                sb.append("        result.set").append(toFirstUpper(field.getFieldName()))
                        .append("(get").append(toFirstUpper(field.getFieldName())).append("());\n");
            }
        }
        sb.append("        return result;\n");
        sb.append("    }\n\n");
    }

    private String printType(String typ) {
        if (prog.hasElement(typ)) {
            return prog.getElement(typ).getName(typePrefix);
        }
        return typ;
    }

    private String equalityExpression(String left, String right, String type) {
        return switch (type) {
            case "float" -> "java.lang.Float.compare(" + left + ", " + right + ") == 0";
            case "double" -> "java.lang.Double.compare(" + left + ", " + right + ") == 0";
            case "byte", "short", "int", "long", "char", "boolean" -> left + " == " + right;
            default -> "java.util.Objects.equals(" + left + ", " + right + ")";
        };
    }


    private boolean isGeneratedTyp(String typ) {
        for (CaseDef c : prog.caseDefs) {
            if (c.getName().equals(typ)) {
                return true;
            }
        }
        for (ConstructorDef c : prog.constructorDefs) {
            if (c.getName().equals(typ)) {
                return true;
            }
        }
        for (ListDef c : prog.listDefs) {
            if (c.getName().equals(typ)) {
                return true;
            }
        }
        return false;
    }

    private void createGetSetParentMethods(StringBuilder sb) {
        sb.append("    private " + getCommonSupertypeType() + " parent;\n");
        sb.append("    public " + getNullableAnnotation() + getCommonSupertypeType() + " getParent() { return parent; }\n");
        sb.append("    public void setParent(" + getNullableAnnotation() + getCommonSupertypeType() + " parent) {\n" +
                "        if (parent != null && this.parent != null) {\n" +
                "            throw new Error(\"Cannot change parent of element \" + this.getClass().getSimpleName() + \", as it is already used in another tree. \"\n" +
                "                + \"Use the copy method to create a new tree or remove the tree from its old parent or set the parent to null before moving the tree. \");\n" +
                "        }\n" +
                "        this.parent = parent;\n" +
                "    }\n\n");
    }


    private void createReplaceByMethod(StringBuilder sb) {
        String T = getCommonSupertypeType();
        sb.append("    public void replaceBy(").append(T).append(" other) {\n");
        sb.append("        if (parent == null)\n");
        sb.append("            throw new RuntimeException(\"Node not attached to tree.\");\n");
        sb.append("        if (parent instanceof AsgList) {\n");
        sb.append("            if (!((AsgList<").append(T).append(">) parent).replaceExact(this, other)) {\n");
        sb.append("                throw new RuntimeException(\"Node not found in parent list.\");\n");
        sb.append("            }\n");
        sb.append("            return;\n");
        sb.append("        }\n");
        sb.append("        // Fallback (should not happen): linear scan\n");
        sb.append("        for (int i=0; i<parent.size(); i++) {\n");
        sb.append("            if (parent.get(i) == this) {\n");
        sb.append("                parent.set(i, other);\n");
        sb.append("                return;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
    }


    private String getNullableAnnotation() {
//        return "@org.eclipse.jdt.annotation.Nullable"; // TODO add flag
        return "";
    }


    private void createGetterAndSetterMethods(ConstructorDef c, StringBuilder sb) {
        for (Parameter p : c.parameters) {
            sb.append("    private " + printType(p.getTyp()) + " " + p.name + ";\n");
            // setter:
            sb.append("    public void set" + toFirstUpper(p.name) + "(" + printType(p.getTyp()) + " " + p.name + ") {\n");
            if (!JavaTypes.primitiveTypes.contains(p.getTyp()) && !p.isRef) {
                // add null checks for non primitive types:
                sb.append("        if (" + p.name + " == null) throw new IllegalArgumentException();\n");
                if (isGeneratedTyp(p.getTyp()) && !p.isRef) {
                    // we have a generated type.
                    // the removed type looses its parent:
                    sb.append("        if (this." + p.name + " != null) this." + p.name + ".setParent(null);\n");
                    // the new element has a new parent:
                    sb.append("        " + p.name + ".setParent(this);\n");
                }
            }
            sb.append("        this." + p.name + " = " + p.name + ";\n" + "    } \n");
            // getter
            sb.append("    public " + printType(p.getTyp()) + " get" + toFirstUpper(p.name) + "() { return " + p.name + "; }\n\n");
        }
    }

    private int createGetMethod(ConstructorDef c, StringBuilder sb) {
        sb.append("    public " + getCommonSupertypeType() + " get(int i) {\n");
        sb.append("        switch (i) {\n");
        int childCount = 0;
        for (Parameter p : c.parameters) {
            if (prog.hasElement(p.getTyp()) && !p.isRef) {
                sb.append("            case " + childCount + ": return " + p.name + ";\n");
                childCount++;
            }
        }
        sb.append("            default: throw new IllegalArgumentException(\"Index out of range: \" + i);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        return childCount;
    }

    private void createSetMethod(ConstructorDef c, StringBuilder sb) {
        sb.append("    public " + getCommonSupertypeType() + " set(int i, " + getCommonSupertypeType() + " newElem) {\n");
        sb.append("        " + getCommonSupertypeType() + " oldElem;\n");
        sb.append("        switch (i) {\n");
        int childCount = 0;
        for (Parameter p : c.parameters) {
            if (prog.hasElement(p.getTyp()) && !p.isRef) {
                sb.append("            case " + childCount + ": oldElem = " + p.name + "; set" + toFirstUpper(p.name) + "((" + printType(p.getTyp()) + ") newElem); return oldElem;\n");
                childCount++;
            }
        }
        sb.append("            default: throw new IllegalArgumentException(\"Index out of range: \" + i);\n");
        sb.append("        }\n");
        sb.append("    }\n");
    }

    private void createForEachMethod(ConstructorDef c, StringBuilder sb) {
        sb.append("\n");
        sb.append("    @Override\n");
        sb.append("    public void forEachElement(java.util.function.Consumer<? super " + getCommonSupertypeType() + "> action) {\n");
        for (Parameter p : c.parameters) {
            if (prog.hasElement(p.getTyp()) && !p.isRef) {
                sb.append("        action.accept(this." + p.name + ");\n");
            }
        }
        sb.append("    }\n");
    }

    private void createSizeMethod(StringBuilder sb, int childCount) {
        sb.append("    public int size() {\n");
        sb.append("        return " + childCount + ";\n");
        sb.append("    }\n");
    }

    private void createCopyMethod(ConstructorDef c, StringBuilder sb) {
        sb.append("    @Override public " + c.getName(typePrefix) + " copy() {\n");
        sb.append("        return (").append(c.getName(typePrefix)).append(") ")
                .append(getTreeUtilType()).append(".copy(this, false);\n");
        sb.append("    }\n\n");
    }

    private void createCopyWithRefsMethod(AstBaseTypeDefinition c, StringBuilder sb) {
        sb.append("    @Override public " + c.getName(typePrefix) + " copyWithRefs() {\n");
        sb.append("        return (").append(c.getName(typePrefix)).append(") ")
                .append(getTreeUtilType()).append(".copy(this, true);\n");
        sb.append("    }\n\n");
    }

    private void createClearMethod(ConstructorDef c, StringBuilder sb) {
        // local clear attributes:
        sb.append("    @Override public void clearAttributesLocal() {\n");
        for (AttributeDef attr : prog.attrDefs) {
            if (hasAttribute(c, attr)) {
                if (attr.parameters == null) {
                    sb.append("        zzattr_" + attr.attr + "_state = 0;\n");
                    if (!JavaTypes.primitiveTypes.contains(attr.returns)) {
                        sb.append("        zzattr_" + attr.attr + "_cache = null;\n");
                    }
                }
            }
        }

        sb.append("    }\n");
    }

    private void createClearMethod(ListDef c, StringBuilder sb) {
        // local clear
        sb.append("    @Override public void clearAttributesLocal() {\n");
        for (AttributeDef attr : prog.attrDefs) {
            if (hasAttribute(c, attr)) {
                if (attr.parameters == null) {
                    sb.append("        zzattr_" + attr.attr + "_state = 0;\n");
                    if (!JavaTypes.primitiveTypes.contains(attr.returns)) {
                        sb.append("        zzattr_" + attr.attr + "_cache = null;\n");
                    }
                }
            }
        }
        sb.append("    }\n");
    }

    private boolean hasAttribute(AstEntityDefinition c, AttributeDef attr) {
        boolean hasAttribute = attr.typ.equals(c.getName());
        for (AstEntityDefinition sup : transientSuperTypes.get(c)) {
            hasAttribute |= attr.typ.equals(sup.getName());
        }
        hasAttribute |= attr.typ.equals(getCommonSupertypeType());
        hasAttribute |= attr.typ.equals("Element");
        return hasAttribute;
    }

    private boolean hasField(AstEntityDefinition c, FieldDef attr) {
        boolean hasAttribute = attr.getTyp().equals(c.getName());
        for (AstEntityDefinition sup : transientSuperTypes.get(c)) {
            hasAttribute |= attr.getTyp().equals(sup.getName());
        }
        hasAttribute |= attr.getTyp().equals(getCommonSupertypeType());
        hasAttribute |= attr.getTyp().equals("Element");
        return hasAttribute;
    }

    private boolean hasCustomToString(AstEntityDefinition c) {
        for (AttributeDef attr : prog.attrDefs) {
            if (attr.attr.equals("toString") && hasAttribute(c, attr)) return true;
        }
        return false;
    }

    private void createAcceptMethods(ConstructorDef c, StringBuilder sb) {
        sb.append("    @Override public void accept(Visitor v) {\n");
        sb.append("        v.visit(this);\n");
        sb.append("    }\n");
    }

    private boolean hasVisitor(AstEntityDefinition e) {
        // a type has a visitor, if it has some subtypes
        return !directSubTypes.get(e).isEmpty();
    }


    private void createToString(ConstructorDef c, StringBuilder sb) {
        for (AttributeDef attr : prog.attrDefs) {
            if (attr.attr.equals("toString") && hasAttribute(c, attr)) {
                // already has toString method
                return;
            }
        }

        sb.append("    @Override public String toString() {\n");
        sb.append("        return ").append(getTreeUtilType()).append(".render(this);\n");
        sb.append("    }\n");
    }

    private void generateBaseClass_Interface(ConstructorDef c) {
        StringBuilder sb = new StringBuilder();
        printProlog(sb);
        sb.append("public non-sealed interface ").append(c.getName(typePrefix)).append(" extends ");
        boolean first = true;
        for (AstEntityDefinition supertype : directSuperTypes.get(c)) {
            if (!first) sb.append(", ");
            sb.append(supertype.getName(typePrefix));
            first = false;
        }
        sb.append(" {\n");

        for (Parameter p : c.parameters) {
            sb.append("    void set").append(toFirstUpper(p.name)).append("(").append(printType(p.getTyp())).append(" ").append(p.name).append(");\n");
            sb.append("    ").append(printType(p.getTyp())).append(" get").append(toFirstUpper(p.name)).append("();\n");
        }

        sb.append("    ").append(getNullableAnnotation()).append(getCommonSupertypeType()).append(" getParent();\n");
        sb.append("    ").append(c.getName(typePrefix)).append(" copy();\n");
        sb.append("    ").append(c.getName(typePrefix)).append(" copyWithRefs();\n");
        sb.append("    void clearAttributesLocal();\n");

        createAttributeStubs(c, sb);
        createFieldStubs(c, sb);

        sb.append("}\n");
        fileGenerator.createFile(c.getName(typePrefix) + ".java", sb);
    }


    private void generateVisitorInterface(AstEntityDefinition d, StringBuilder sb) {
        sb.append("    public abstract void accept(Visitor v);\n");


        // Visitor interface
        sb.append("    public interface Visitor");
        sb.append(" {\n");
        sb.append("");
        List<AstEntityDefinition> defs = new ArrayList<>(prog.constructorDefs);
        defs.addAll(prog.listDefs);

        for (AstEntityDefinition contained : defs) {
            if (contained instanceof AstBaseTypeDefinition) {
                AstBaseTypeDefinition c = (AstBaseTypeDefinition) contained;
                sb.append("        void visit(" + c.getName(typePrefix) + " " + toFirstLower(c.getName()) + ");\n");
            }
        }
        sb.append("    }\n");

        // Default Visitor
        sb.append("    public static abstract class DefaultVisitor implements Visitor {\n");
        for (AstEntityDefinition contained : defs) {
            if (contained instanceof AstBaseTypeDefinition) {
                AstBaseTypeDefinition c = (AstBaseTypeDefinition) contained;
                sb.append("        @Override public void visit(" + c.getName(typePrefix) + " " + toFirstLower(c.getName()) + ") {\n");

                if (contained instanceof ConstructorDef) {
                    ConstructorDef cconst = (ConstructorDef) contained;
                    for (Parameter p : cconst.parameters) {
                        if (prog.hasElement(p.getTyp()) && !p.isRef) {
                            sb.append("          " + toFirstLower(c.getName()) + ".get" + toFirstUpper(p.name) + "().accept(this);\n");
                        }
                    }
                } else {
                    ListDef l = ((ListDef) contained);
                    String listName = toFirstLower(c.getName());
                    sb.append("          for (int i = 0, n = ").append(listName).append(".size(); i < n; i++) {\n");
                    sb.append("              ").append(listName).append(".get(i).accept(this);\n");
                    sb.append("          }\n");
                }

                sb.append("     }\n");
            }
        }
        sb.append("    }\n");

        // Stack-safe visitor for very deep trees. DefaultVisitor remains recursive for compatibility.
        sb.append("    public static abstract class IterativeVisitor implements Visitor {\n");
        sb.append("        public final void traverse(").append(getCommonSupertypeType()).append(" root) {\n");
        sb.append("            java.util.ArrayDeque<").append(getCommonSupertypeType())
                .append("> stack = new java.util.ArrayDeque<>();\n");
        sb.append("            stack.push(root);\n");
        sb.append("            while (!stack.isEmpty()) {\n");
        sb.append("                ").append(getCommonSupertypeType()).append(" elem = stack.pop();\n");
        sb.append("                elem.accept(this);\n");
        sb.append("                for (int i = elem.size() - 1; i >= 0; i--) stack.push(elem.get(i));\n");
        sb.append("            }\n");
        sb.append("        }\n");
        for (AstEntityDefinition contained : defs) {
            if (contained instanceof AstBaseTypeDefinition) {
                AstBaseTypeDefinition baseType = (AstBaseTypeDefinition) contained;
                sb.append("        @Override public void visit(").append(baseType.getName(typePrefix)).append(" ")
                        .append(toFirstLower(baseType.getName())).append(") {}\n");
            }
        }
        sb.append("    }\n");
    }

    private void generateBaseClasses() {
        for (ConstructorDef c : prog.constructorDefs) {
            generateBaseClass_Interface(c);
            generateBaseClass_Impl(c);
        }
    }

    private void generateFactoryClass() {
        StringBuilder sb = new StringBuilder();
        printProlog(sb);

        addSuppressWarningAnnotations(sb);
        sb.append("public class " + toFirstUpper(prog.getFactoryName()) + " {\n");

        for (ConstructorDef c : prog.constructorDefs) {
            sb.append("    public static " + c.getName(typePrefix) + " " + c.getName() + "(");
            boolean first = true;
            for (Parameter a : c.parameters) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(printType(a.getTyp()) + " " + a.name);
                first = false;
            }
            sb.append(") {\n");
            sb.append("        return new " + c.getName(typePrefix) + "Impl(");
            first = true;
            for (Parameter a : c.parameters) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(a.name);
                first = false;
            }
            sb.append(");\n");
            sb.append("    }\n");

        }


        for (ListDef l : prog.listDefs) {
            sb.append("    public static " + l.getName(typePrefix) + " " + l.getName() + "(" + printType(l.itemType) + " ... elements ) {\n");
            sb.append("        " + l.getName(typePrefix) + " l = new " + l.getName(typePrefix) + "Impl();\n");
            sb.append("        l.ensureCapacity(elements.length);\n");
            sb.append("        for (int i = 0; i < elements.length; i++) l.add(elements[i]);\n");
            sb.append("        return l;\n");
            sb.append("    }\n");


            sb.append("    public static " + l.getName(typePrefix) + " " + l.getName() + "(Iterable<" + printType(l.itemType) + "> elements ) {\n");
            sb.append("        " + l.getName(typePrefix) + " l = new " + l.getName(typePrefix) + "Impl();\n");
            sb.append("        if (elements instanceof Collection) l.addAll((Collection<? extends " + printType(l.itemType) + ">) elements);\n");
            sb.append("        else for (" + printType(l.itemType) + " elem : elements) l.add(elem);\n");
            sb.append("        return l;\n");
            sb.append("    }\n");
        }


        sb.append("}");
        fileGenerator.createFile(toFirstUpper(prog.getFactoryName()) + ".java", sb);
    }

    private void generateInterfaceType(CaseDef c) {
        if (c == commonSuperType) {
            // generated in generateStandardClasses()
            return;
        }
        StringBuilder sb = new StringBuilder();
        printProlog(sb);

        // direct subtypes of this case (interfaces and/or list classes)
        Collection<AstEntityDefinition> subs = directSubTypes.get(c);
        boolean hasSubs = !subs.isEmpty();

        // header: sealed if it has alternatives, otherwise non-sealed (since it’s in a sealed hierarchy)
        sb.append("public ").append(hasSubs ? "sealed " : "non-sealed ").append("interface ")
                .append(c.getName(typePrefix));

        // extends (super cases)
        boolean first = true;
        sb.append(" extends ");
        for (AstEntityDefinition supertype : directSuperTypes.get(c)) {
            if (!first) sb.append(", ");
            sb.append(supertype.getName(typePrefix));
            first = false;
        }
        if (first) {
            // no explicit super-cases -> extend the common super element type for convenience
            sb.append(getCommonSupertypeType());
        }

        // permits (ONLY in header)
        if (hasSubs) {
            sb.append(" permits ");
            first = true;
            for (AstEntityDefinition sub : subs) {
                if (!first) sb.append(", ");
                // note: this is the TYPE name (interface/class), NOT the Impl class
                sb.append(sub.getName(typePrefix));
                first = false;
            }
        }
        sb.append(" {\n");

        // common attributes/fields stubs
        Set<Parameter> attributes = calculateAttributes(c);
        for (Parameter p : attributes) {
            sb.append("    void set").append(toFirstUpper(p.name)).append("(").append(printType(p.getTyp())).append(" ").append(p.name).append(");\n");
            sb.append("    ").append(printType(p.getTyp())).append(" get").append(toFirstUpper(p.name)).append("();\n");
        }
        sb.append("    ").append(getNullableAnnotation()).append(getCommonSupertypeType()).append(" getParent();\n");

        generateMatcher(c, sb);
        sb.append("    ").append(printType(c.getName())).append(" copy();\n");
        sb.append("    ").append(printType(c.getName())).append(" copyWithRefs();\n");

        createAttributeStubs(c, sb);
        createFieldStubs(c, sb);

        sb.append("}\n");
        fileGenerator.createFile(c.getName(typePrefix) + ".java", sb);
    }


    private void generateMatcher(CaseDef c, StringBuilder sb) {
        // create match methods:
        sb.append("    <T> T match(Matcher<T> s);\n");
        sb.append("    void match(MatcherVoid s);\n");

        // create Matcher interface:
        sb.append("    public interface Matcher<T> {\n");
        for (AstBaseTypeDefinition baseType : baseTypes.get(c)) {
            sb.append("        T case_" + baseType.getName() + "(" + baseType.getName(typePrefix) + " " + toFirstLower(baseType.getName()) + ");\n");
        }
        sb.append("    }\n\n");

        // create MatchVoid interface:
        sb.append("    public interface MatcherVoid {\n");
        for (AstBaseTypeDefinition baseType : baseTypes.get(c)) {
            sb.append("        void case_" + baseType.getName() + "(" + baseType.getName(typePrefix) + " " + toFirstLower(baseType.getName()) + ");\n");
        }
        sb.append("    }\n\n");
    }


    private void createAttributeStubs(AstEntityDefinition c, StringBuilder sb) {
        for (AttributeDef attr : prog.attrDefs) {
            if (hasAttribute(c, attr)) {
                sb.append("    /** " + attr.comment + "*/\n");
                sb.append("    public abstract " + attr.returns + " " + attr.attr + "(" + printParams(attr.parameters) + ");\n");
            }
        }
    }

    private void createFieldStubs(AstEntityDefinition c, StringBuilder sb) {
        for (FieldDef attr : prog.fieldDefs) {
            if (hasField(c, attr)) {
                sb.append("    /** " + attr.getDoc() + "*/\n");
                sb.append("    public abstract " + attr.getFieldType() + " get" + toFirstUpper(attr.getFieldName()) + "();\n");
                sb.append("    /** " + attr.getDoc() + "*/\n");
                sb.append("    public abstract void set" + toFirstUpper(attr.getFieldName())
                        + "(" + attr.getFieldType() + " " + attr.getFieldName() + ");\n");
            }
        }
    }

    public static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String s : list) {
            if (!first) {
                sb.append(sep);
            }
            sb.append(s);
            first = false;
        }
        return sb.toString();
    }

    private void generateInterfaceTypes() {
        for (CaseDef caseDef : prog.caseDefs) {
            generateInterfaceType(caseDef);
        }
    }

    private void generateList(ListDef l) {
        generateList_interface(l);
        generateList_impl(l);
    }

    private void generateList_impl(ListDef l) {
        StringBuilder sb;
        sb = new StringBuilder();
        printProlog(sb);

        addSuppressWarningAnnotations(sb);
        sb.append("final class ").append(l.getName(typePrefix)).append("Impl extends ")
                .append(l.getName(typePrefix)).append(" {\n ");

        createShallowCopyMethod(l, sb);

        createGetSetParentMethods(sb);

        createReplaceByMethod(sb);

        sb.append("    protected void other_setParentToThis(" + printType(l.itemType) + " t) {\n");
        if (isGeneratedTyp(l.itemType) && !l.ref) {
            sb.append("        t.setParent(this);\n");
        }
        sb.append("    }\n\n");

        sb.append("    protected void other_clearParent(" + printType(l.itemType) + " t) {\n");
        if (isGeneratedTyp(l.itemType) && !l.ref) {
            sb.append("        t.setParent(null);\n");
        }
        sb.append("    }\n\n");

        // set method:
        sb.append("    @Override\n");
        sb.append("    public " + getCommonSupertypeType() + " set(int i, " + getCommonSupertypeType() + " newElement) {\n");
        sb.append("        return ((AsgList<" + printType(l.itemType) + ">) this).set(i, (" + printType(l.itemType) + ") newElement);\n");
        sb.append("    }\n\n");
        sb.append("    @Override public boolean structuralEquals(").append(getCommonSupertypeType()).append(" e) {\n");
        sb.append("        return ").append(getTreeUtilType()).append(".structuralEquals(this, e);\n");
        sb.append("    }\n\n");

        // match methods for switch
        createMatchMethods(l, sb);


        // accept methods for visitors
        sb.append("    @Override public void accept(Visitor v) {\n");
        sb.append("        v.visit(this);\n");
        sb.append("    }\n");
        createClearMethod(l, sb);
        createAttributeImpl(l, sb);
        createFieldsImpl(l, sb);

        // toString method
        createToString(l, sb);

        sb.append("}\n");
        fileGenerator.createFile(l.getName(typePrefix) + "Impl.java", sb);
    }

    private void createToString(ListDef l, StringBuilder sb) {
        for (AttributeDef attr : prog.attrDefs) {
            if (attr.attr.equals("toString") && hasAttribute(l, attr)) {
                // already has toString method
                return;
            }
        }

        sb.append("    @Override public String toString() {\n");
        sb.append("        return ").append(getTreeUtilType()).append(".render(this);\n");
        sb.append("    }\n");
    }

    private void generateList_interface(ListDef l) {
        StringBuilder sb = new StringBuilder();
        printProlog(sb);
        addSuppressWarningAnnotations(sb);

        sb.append("public non-sealed abstract class ")
                .append(l.getName(typePrefix))
                .append(" extends AsgList<").append(printType(l.itemType)).append("> implements ");

        boolean first = true;
        for (AstEntityDefinition supertype : directSuperTypes.get(l)) {
            if (!first) sb.append(", ");
            sb.append(supertype.getName(typePrefix));
            first = false;
        }
        sb.append(" {\n");

        sb.append("    public ").append(l.getName(typePrefix)).append(" copy() {\n");
        sb.append("        return (").append(l.getName(typePrefix)).append(") ")
                .append(getTreeUtilType()).append(".copy(this, false);\n");
        sb.append("    }\n\n");

        createCopyWithRefsMethod(l, sb);
        createAttributeStubs(l, sb);
        createFieldStubs(l, sb);

        sb.append("}\n");
        fileGenerator.createFile(l.getName(typePrefix) + ".java", sb);
    }

    private void createShallowCopyMethod(ListDef l, StringBuilder sb) {
        String implType = l.getName(typePrefix) + "Impl";
        sb.append("    ").append(implType).append(" zzShallowCopy() {\n");
        sb.append("        ").append(implType).append(" result = new ").append(implType).append("();\n");
        for (FieldDef field : prog.fieldDefs) {
            if (hasField(l, field)) {
                sb.append("        result.set").append(toFirstUpper(field.getFieldName()))
                        .append("(get").append(toFirstUpper(field.getFieldName())).append("());\n");
            }
        }
        sb.append("        return result;\n");
        sb.append("    }\n\n");
    }


    private void addSuppressWarningAnnotations(StringBuilder sb) {
        sb.append("@SuppressWarnings({\"cast\", \"unused\", \"rawtypes\"})\n");
    }

    private void generateLists() {
        for (ListDef l : prog.listDefs) {
            generateList(l);
        }
    }

    private void generateStandardClasses() {
        StringBuilder sb = new StringBuilder();
        printProlog(sb);

        // Seal the common super element and permit all direct children
        Collection<AstEntityDefinition> subs = directSubTypes.get(commonSuperType);

        sb.append("public sealed interface ").append(getCommonSupertypeType());

        // no extends list for the root
        if (!subs.isEmpty()) {
            sb.append(" permits ");
            boolean first = true;
            for (AstEntityDefinition sub : subs) {
                if (!first) sb.append(", ");
                sb.append(sub.getName(typePrefix));
                first = false;
            }
        }
        sb.append(" {\n");

        sb.append("    ").append(getNullableAnnotation()).append(getCommonSupertypeType()).append(" getParent();\n")
                .append("    ").append(getCommonSupertypeType()).append(" copy();\n")
                .append("    ").append(getCommonSupertypeType()).append(" copyWithRefs();\n")
                .append("    int size();\n")
                .append("    default void clearAttributes() {\n")
                .append("        java.util.ArrayDeque<").append(getCommonSupertypeType()).append("> stack = new java.util.ArrayDeque<>();\n")
                .append("        stack.push(this);\n")
                .append("        while (!stack.isEmpty()) {\n")
                .append("            ").append(getCommonSupertypeType()).append(" elem = stack.pop();\n")
                .append("            elem.clearAttributesLocal();\n")
                .append("            for (int i = elem.size() - 1; i >= 0; i--) stack.push(elem.get(i));\n")
                .append("        }\n")
                .append("    }\n")
                .append("    void clearAttributesLocal();\n")
                .append("    ").append(getCommonSupertypeType()).append(" get(int i);\n")
                .append("    ").append(getCommonSupertypeType()).append(" set(int i, ").append(getCommonSupertypeType()).append(" newElement);\n")
                .append("    void forEachElement(java.util.function.Consumer<? super ").append(getCommonSupertypeType()).append("> action);\n")
                .append("    default void trimToSize() { for (int i = 0, n = size(); i < n; i++) get(i).trimToSize(); }\n")
                .append("    void setParent(").append(getNullableAnnotation()).append(getCommonSupertypeType()).append(" parent);\n")
                .append("    void replaceBy(").append(getCommonSupertypeType()).append(" other);\n")
                .append("    boolean structuralEquals(").append(getCommonSupertypeType()).append(" elem);\n")
                .append("    default java.util.List<Integer> pathTo(").append(getCommonSupertypeType()).append("  elem) {\n")
                .append("        java.util.List<Integer> path = new java.util.ArrayList<>();\n")
                .append("        while (elem != this) {\n")
                .append("            if (elem == null) { throw new RuntimeException(\"Element \" + elem + \" is not a parent of \" + this); }\n")
                .append("            ").append(getCommonSupertypeType()).append(" parent = elem.getParent();\n")
                .append("            for (int i = 0; i < parent.size(); i++) {\n")
                .append("                if (parent.get(i) == elem) { path.add(i); break; }\n")
                .append("            }\n")
                .append("            elem = parent;\n")
                .append("        }\n")
                .append("        java.util.Collections.reverse(path);\n")
                .append("        return path;\n")
                .append("    }\n")
                .append("    default ").append(getCommonSupertypeType()).append(" followPath(Iterable<Integer> path) {\n")
                .append("        ").append(getCommonSupertypeType()).append(" elem = this;\n")
                .append("        for (Integer i : path) { elem = elem.get(i); }\n")
                .append("        return elem;\n")
                .append("    }\n");

        // keep your existing matcher + visitor + attribute/field stubs
        generateMatcher(commonSuperType, sb);
        generateVisitorInterface(commonSuperType, sb);
        createAttributeStubs(commonSuperType, sb);
        createFieldStubs(commonSuperType, sb);

        // --- Pattern-matching switch matcher (Java 21+) ---
        // Functional interface group
        sb.append("    public interface ").append(getCommonSupertypeType()).append("Switch<T> {\n");
        for (ConstructorDef d : prog.constructorDefs) {
            String t = d.getName(typePrefix);
            sb.append("        T case_").append(t).append("(").append(t).append("Impl n);\n");
        }
        for (ListDef d : prog.listDefs) {
            String t = d.getName(typePrefix);
            sb.append("        T case_").append(t).append("(").append(t).append("Impl n);\n");
        }
        sb.append("    }\n");

        // Static method doing the pattern switch
        sb.append("    static <T> T matchSwitch(").append(getCommonSupertypeType()).append(" e, ")
                .append(getCommonSupertypeType()).append("Switch<T> fn) {\n")
                .append("        return switch (e) {\n");
        for (ConstructorDef d : prog.constructorDefs) {
            String t = d.getName(typePrefix);
            sb.append("            case ").append(t).append("Impl n -> fn.case_").append(t).append("(n);\n");
        }
        for (ListDef d : prog.listDefs) {
            String t = d.getName(typePrefix);
            sb.append("            case ").append(t).append("Impl n -> fn.case_").append(t).append("(n);\n");
        }
        sb.append("            default -> throw new IllegalStateException(\"Unknown node: \" + e.getClass());\n")
                .append("        };\n")
                .append("    }\n");

        sb.append("}\n\n");
        generateTreeUtilities(sb);

        fileGenerator.createFile(getCommonSupertypeType() + ".java", sb);
    }

    private void generateTreeUtilities(StringBuilder sb) {
        String elementType = getCommonSupertypeType();
        sb.append("@SuppressWarnings({\"rawtypes\", \"unchecked\"})\n");
        sb.append("final class ").append(getTreeUtilType()).append(" {\n");
        sb.append("    private ").append(getTreeUtilType()).append("() {}\n\n");
        sb.append("    static ").append(elementType).append(" copy(").append(elementType)
                .append(" root, boolean withRefs) {\n");
        sb.append("        java.util.IdentityHashMap<").append(elementType).append(", ")
                .append(elementType).append("> copies = withRefs ? new java.util.IdentityHashMap<>() : null;\n");
        sb.append("        java.util.ArrayList<").append(elementType)
                .append("> originals = withRefs ? new java.util.ArrayList<>() : null;\n");
        sb.append("        java.util.ArrayDeque<").append(elementType)
                .append("> originalStack = new java.util.ArrayDeque<>();\n");
        sb.append("        java.util.ArrayDeque<").append(elementType)
                .append("> copyStack = new java.util.ArrayDeque<>();\n");
        sb.append("        ").append(elementType).append(" rootCopy = shallowCopy(root);\n");
        sb.append("        originalStack.push(root);\n");
        sb.append("        copyStack.push(rootCopy);\n");
        sb.append("        if (withRefs) { copies.put(root, rootCopy); originals.add(root); }\n");
        sb.append("        while (!originalStack.isEmpty()) {\n");
        sb.append("            ").append(elementType).append(" original = originalStack.pop();\n");
        sb.append("            ").append(elementType).append(" copy = copyStack.pop();\n");
        sb.append("            for (int i = 0, n = original.size(); i < n; i++) {\n");
        sb.append("                ").append(elementType).append(" child = original.get(i);\n");
        sb.append("                boolean generatedChild = isGenerated(child);\n");
        sb.append("                ").append(elementType)
                .append(" childCopy = generatedChild ? shallowCopy(child) : ")
                .append("(withRefs ? child.copyWithRefs() : child.copy());\n");
        sb.append("                if (copy instanceof AsgList list) list.add(childCopy);\n");
        sb.append("                else copy.set(i, childCopy);\n");
        sb.append("                if (generatedChild) {\n");
        sb.append("                    originalStack.push(child);\n");
        sb.append("                    copyStack.push(childCopy);\n");
        sb.append("                }\n");
        sb.append("                if (withRefs) {\n");
        sb.append("                    copies.put(child, childCopy);\n");
        sb.append("                    if (generatedChild) originals.add(child);\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        if (withRefs) {\n");
        sb.append("            for (int i = 0, n = originals.size(); i < n; i++) {\n");
        sb.append("                ").append(elementType).append(" original = originals.get(i);\n");
        sb.append("                repairReferences(original, copies.get(original), copies);\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return rootCopy;\n");
        sb.append("    }\n\n");

        sb.append("    static boolean structuralEquals(").append(elementType).append(" left, ")
                .append(elementType).append(" right) {\n");
        sb.append("        if (left == right) return true;\n");
        sb.append("        if (right == null) return false;\n");
        sb.append("        java.util.ArrayDeque<").append(elementType)
                .append("> leftStack = new java.util.ArrayDeque<>();\n");
        sb.append("        java.util.ArrayDeque<").append(elementType)
                .append("> rightStack = new java.util.ArrayDeque<>();\n");
        sb.append("        leftStack.push(left);\n");
        sb.append("        rightStack.push(right);\n");
        sb.append("        while (!leftStack.isEmpty()) {\n");
        sb.append("            ").append(elementType).append(" a = leftStack.pop();\n");
        sb.append("            ").append(elementType).append(" b = rightStack.pop();\n");
        sb.append("            if (!isGenerated(a)) {\n");
        sb.append("                if (!a.structuralEquals(b)) return false;\n");
        sb.append("                continue;\n");
        sb.append("            }\n");
        sb.append("            if (!localEquals(a, b)) return false;\n");
        sb.append("            int n = a.size();\n");
        sb.append("            if (b.size() != n) return false;\n");
        sb.append("            for (int i = 0; i < n; i++) {\n");
        sb.append("                leftStack.push(a.get(i));\n");
        sb.append("                rightStack.push(b.get(i));\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return true;\n");
        sb.append("    }\n\n");

        sb.append("    static String render(").append(elementType).append(" root) {\n");
        sb.append("        StringBuilder result = new StringBuilder();\n");
        sb.append("        java.util.ArrayDeque<Object> tasks = new java.util.ArrayDeque<>();\n");
        sb.append("        tasks.push(root);\n");
        sb.append("        while (!tasks.isEmpty()) {\n");
        sb.append("            Object task = tasks.pop();\n");
        sb.append("            if (task instanceof String text) result.append(text);\n");
        sb.append("            else if (task instanceof RenderListState state) {\n");
        sb.append("                if (state.index >= state.list.size()) result.append(\")\");\n");
        sb.append("                else {\n");
        sb.append("                    int index = state.index++;\n");
        sb.append("                    tasks.push(state);\n");
        sb.append("                    tasks.push(state.list.get(index));\n");
        sb.append("                    if (index > 0) tasks.push(\", \");\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("            else appendRenderTasks(result, tasks, (").append(elementType).append(") task);\n");
        sb.append("            if (result.length() > 1000) { result.append(\"...\"); break; }\n");
        sb.append("        }\n");
        sb.append("        return result.toString();\n");
        sb.append("    }\n\n");

        sb.append("    private static void appendRenderTasks(StringBuilder result, java.util.ArrayDeque<Object> tasks, ")
                .append(elementType).append(" elem) {\n");
        for (ConstructorDef c : prog.constructorDefs) {
            String implType = c.getName(typePrefix) + "Impl";
            sb.append("        if (elem instanceof ").append(implType).append(" value) {\n");
            if (hasCustomToString(c)) {
                sb.append("            result.append(value.toString());\n");
            } else {
                sb.append("            result.append(\"").append(c.getName()).append("\");\n");
                if (!c.parameters.isEmpty()) {
                    sb.append("            result.append(\"(\");\n");
                    sb.append("            tasks.push(\")\");\n");
                    for (int i = c.parameters.size() - 1; i >= 0; i--) {
                        Parameter p = c.parameters.get(i);
                        String getter = "value.get" + toFirstUpper(p.name) + "()";
                        if (prog.hasElement(p.getTyp()) && !p.isRef) {
                            sb.append("            tasks.push(").append(getter).append(");\n");
                        } else if (prog.hasElement(p.getTyp())) {
                            sb.append("            tasks.push(referenceString(").append(getter).append("));\n");
                        } else {
                            sb.append("            tasks.push(String.valueOf(").append(getter).append("));\n");
                        }
                        if (i > 0) sb.append("            tasks.push(\", \");\n");
                    }
                }
            }
            sb.append("            return;\n");
            sb.append("        }\n");
        }
        for (ListDef l : prog.listDefs) {
            String implType = l.getName(typePrefix) + "Impl";
            sb.append("        if (elem instanceof ").append(implType).append(" value) {\n");
            if (hasCustomToString(l)) {
                sb.append("            result.append(value.toString());\n");
            } else {
                sb.append("            result.append(\"").append(l.getName()).append("(\");\n");
                sb.append("            tasks.push(new RenderListState(value));\n");
            }
            sb.append("            return;\n");
            sb.append("        }\n");
        }
        sb.append("        result.append(elem.getClass().getSimpleName());\n");
        sb.append("    }\n\n");
        sb.append("    private static String referenceString(Object ref) {\n");
        sb.append("        if (ref == null) return \"null\";\n");
        sb.append("        return ref.getClass().getSimpleName() + \"@\" + Integer.toHexString(System.identityHashCode(ref));\n");
        sb.append("    }\n\n");
        sb.append("    private static final class RenderListState {\n");
        sb.append("        final ").append(elementType).append(" list;\n");
        sb.append("        int index;\n");
        sb.append("        RenderListState(").append(elementType).append(" list) { this.list = list; }\n");
        sb.append("    }\n\n");

        sb.append("    private static boolean localEquals(").append(elementType).append(" left, ")
                .append(elementType).append(" right) {\n");
        for (ConstructorDef c : prog.constructorDefs) {
            String implType = c.getName(typePrefix) + "Impl";
            String interfaceType = c.getName(typePrefix);
            sb.append("        if (left instanceof ").append(implType).append(" source) {\n");
            sb.append("            if (!(right instanceof ").append(interfaceType).append(" target)) return false;\n");
            List<String> comparisons = new ArrayList<>();
            for (Parameter p : c.parameters) {
                if (p.isIgnoreEquality() || (prog.hasElement(p.getTyp()) && !p.isRef)) continue;
                String leftExpr = "source.get" + toFirstUpper(p.name) + "()";
                String rightExpr = "target.get" + toFirstUpper(p.name) + "()";
                if (p.isRef && prog.hasElement(p.getTyp())) {
                    comparisons.add(leftExpr + " == " + rightExpr);
                } else {
                    comparisons.add(equalityExpression(leftExpr, rightExpr, p.getTyp()));
                }
            }
            sb.append("            return ").append(comparisons.isEmpty()
                    ? "true" : String.join(" && ", comparisons)).append(";\n");
            sb.append("        }\n");
        }
        for (ListDef l : prog.listDefs) {
            String implType = l.getName(typePrefix) + "Impl";
            String interfaceType = l.getName(typePrefix);
            sb.append("        if (left instanceof ").append(implType)
                    .append(") return right instanceof ").append(interfaceType).append(";\n");
        }
        sb.append("        return false;\n");
        sb.append("    }\n\n");

        sb.append("    private static ").append(elementType).append(" shallowCopy(")
                .append(elementType).append(" elem) {\n");
        for (ConstructorDef c : prog.constructorDefs) {
            String implType = c.getName(typePrefix) + "Impl";
            sb.append("        if (elem instanceof ").append(implType).append(" value) return value.zzShallowCopy();\n");
        }
        for (ListDef l : prog.listDefs) {
            String implType = l.getName(typePrefix) + "Impl";
            sb.append("        if (elem instanceof ").append(implType).append(" value) return value.zzShallowCopy();\n");
        }
        sb.append("        throw new IllegalArgumentException(\"Unsupported AST implementation: \" + elem.getClass());\n");
        sb.append("    }\n\n");

        sb.append("    private static boolean isGenerated(").append(elementType).append(" elem) {\n");
        for (ConstructorDef c : prog.constructorDefs) {
            sb.append("        if (elem instanceof ").append(c.getName(typePrefix)).append("Impl) return true;\n");
        }
        for (ListDef l : prog.listDefs) {
            sb.append("        if (elem instanceof ").append(l.getName(typePrefix)).append("Impl) return true;\n");
        }
        sb.append("        return false;\n");
        sb.append("    }\n\n");

        sb.append("    private static void repairReferences(").append(elementType).append(" original, ")
                .append(elementType).append(" copy, java.util.IdentityHashMap<")
                .append(elementType).append(", ").append(elementType).append("> copies) {\n");
        for (ConstructorDef c : prog.constructorDefs) {
            List<Parameter> refs = new ArrayList<>();
            for (Parameter p : c.parameters) {
                if (p.isRef && prog.hasElement(p.getTyp())) refs.add(p);
            }
            if (refs.isEmpty()) continue;
            String implType = c.getName(typePrefix) + "Impl";
            sb.append("        if (original instanceof ").append(implType).append(" source) {\n");
            sb.append("            ").append(implType).append(" target = (").append(implType).append(") copy;\n");
            for (Parameter p : refs) {
                sb.append("            ").append(elementType).append(" mapped_").append(p.name)
                        .append(" = copies.get(source.get").append(toFirstUpper(p.name)).append("());\n");
                sb.append("            if (mapped_").append(p.name).append(" != null) target.set")
                        .append(toFirstUpper(p.name)).append("((").append(printType(p.getTyp()))
                        .append(") mapped_").append(p.name).append(");\n");
            }
            sb.append("            return;\n");
            sb.append("        }\n");
        }
        sb.append("    }\n");
        sb.append("}\n");
    }


    private void generateStandardList() {
        StringBuilder sb = new StringBuilder();
        printProlog(sb);
        TemplateAsgList.writeTo(sb, getCommonSupertypeType());
        fileGenerator.createFile("AsgList.java", sb);
    }

    private void generateCyclicDependencyError() {
        StringBuilder sb = new StringBuilder();
        printProlog(sb);
        TemplateCyclicDependencyError.writeTo(sb, getCommonSupertypeType());
        fileGenerator.createFile("CyclicDependencyError.java", sb);
    }

    private String getCommonSupertypeType() {
        return typePrefix + "Element";
    }

    private String getTreeUtilType() {
        return typePrefix + "TreeUtil";
    }

    private void printProlog(StringBuilder sb) {
        sb.append(FileGenerator.PARSEQ_COMMENT + "\n");
        sb.append("package " + packageName + ";\n");
        sb.append("import java.util.*;\n\n");
    }


    private String toFirstLower(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private String toFirstUpper(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * calculates the transient closure of a multimap
     */
    private <T> Multimap<T, T> transientClosure(Multimap<T, T> start) {
        Multimap<T, T> result = HashMultimap.create();
        for (Entry<T, T> e : start.entries()) {
            if (e.getKey() != null && e.getValue() != null) {
                result.put(e.getKey(), e.getValue());
            }
        }

        boolean changed;
        do {
            Multimap<T, T> changes = HashMultimap.create();
            for (Entry<T, T> e1 : result.entries()) {
                T v = e1.getValue();
                if (v == null) continue;
                for (T t : result.get(v)) {
                    if (e1.getKey() != null && t != null) {
                        changes.put(e1.getKey(), t);
                    }
                }
            }
            changed = result.putAll(changes);
        } while (changed);

        return result;
    }

}
