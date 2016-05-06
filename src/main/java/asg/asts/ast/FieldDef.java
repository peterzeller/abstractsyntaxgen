package asg.asts.ast;

public class FieldDef {
    private final String fieldType;
    private final String typ;
    private final String fieldName;
    private final String doc;

    public FieldDef(String fieldType, String typ, String fieldName, String doc) {
        this.fieldType = fieldType;
        this.typ = typ;
        this.fieldName = fieldName;
        this.doc = doc;
    }

    public String getFieldType() {
        return fieldType;
    }

    public String getTyp() {
        return typ;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getDoc() {
        return doc;
    }
}
