package asg.asts.ast;

import java.util.*;


public class Program {

	public final List<ListDef> listDefs = new LinkedList<>();
	public final List<CaseDef> caseDefs = new LinkedList<>();
	public final List<ConstructorDef> constructorDefs = new LinkedList<>();
	public final List<AttributeDef> attrDefs = new LinkedList<>();
	public final List<FieldDef> fieldDefs = new ArrayList<>();
	public final Map<String, AstEntityDefinition> definitions = new HashMap<>();
	private String packageName;
	private String typePrefix = "";


	public Program(String packageName) {
		this.packageName = packageName;
	}

//	public void addListDef(String name, String itemType) {
//		if (definitions.containsKey(name)) {
//			throw new Error("Name "+ name + " redefined.");
//		}
//		ListDef def = new ListDef(name, itemType);
//		listDefs.add(def);
//		definitions.put(name, def);
//	}

//	public void addCaseDef(String name, List<Alternative> alternatives) {
//		if (definitions.containsKey(name)) {
//			throw new Error("Name "+ name + " redefined.");
//		}
//		CaseDef def = new CaseDef(name, alternatives);
//		caseDefs.add(def);
//		definitions.put(name, def);
//	}

	
//	public void addConstructor(String name, List<Parameter> parameters) {
//		if (definitions.containsKey(name)) {
//			throw new Error("Name "+ name + " redefined.");
//		}
//		ConstructorDef def = new ConstructorDef(name, parameters);
//		constructorDefs.add(def);
//		definitions.put(name, def);
//	}

	private void addDefinition(String name, AstEntityDefinition def) {
		if (definitions.containsKey(name)) {
			throw new Error("Name "+ name + " redefined.");
		}
		definitions.put(name, def);
	}
	
	public void addConstructorDef(ConstructorDef c) {
		addDefinition(c.getName(), c);
		constructorDefs.add(c);
	}
	
	
	public void addListDef(ListDef listDef) {
		addDefinition(listDef.getName(), listDef);
		listDefs.add(listDef);
	}

	public void addCaseDef(CaseDef caseDef) {
		addDefinition(caseDef.getName(), caseDef);
		caseDefs.add(caseDef);
	}

	public String getPackageName() {
		return packageName;
	}
	
	public void addAttribute(List<Parameter> parameters, String typ, String attr, String returnType, String implementedBy, String doc, String circular) {
		String docStr = doc != null ? doc : "";
		attrDefs.add(new AttributeDef(parameters, typ, attr, docStr, returnType, implementedBy, circular));
	}

	public void addAttributeField(String fieldType, String typ, String fieldName, String doc) {
		String docStr = doc != null ? doc : "";
		fieldDefs.add(new FieldDef(fieldType, typ, fieldName, doc));
		// ($t.name, $elem.text, $attrName.text, $doc.text)
	}
	
	
	
	

	public AstEntityDefinition getElement(String sub) {
		return definitions.get(sub);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("\nCase Types: \n");
		for (CaseDef x : caseDefs) {
			sb.append(x).append("\n");
		}
		sb.append("\nConstructors: \n");
		for (ConstructorDef x : constructorDefs) {
			sb.append(x).append("\n");
		}
		sb.append("\nLists: \n");
		for (ListDef x : listDefs) {
			sb.append(x).append("\n");
		}
		
		return sb.toString();
	}

	public String getFactoryName() {
		if (!typePrefix.isEmpty()) {
			return typePrefix;
		}
		int pos = packageName.lastIndexOf('.');
		if (pos >= 0) {
			return packageName.substring(pos+1);
		} else {
			return packageName;
		}
	}

	public boolean hasElement(String e) {
		return definitions.containsKey(e);
	}


	

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getTypePrefix() {
		return typePrefix;
	}
	
	public void setTypePrefix(String typePrefix) {
		this.typePrefix = typePrefix;
	}



}
