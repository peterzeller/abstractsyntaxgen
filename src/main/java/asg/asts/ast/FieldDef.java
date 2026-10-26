package asg.asts.ast;

public class FieldDef {
    private final boolean tracked;
    private boolean source;
    public boolean isSource() { return source; }
    public void setSource(boolean source) { this.source = source; }
    private final String fieldType;
    private final String typ;
    private final String fieldName;
    private final String doc;

    public FieldDef(String fieldType, String typ, String fieldName, String doc) {
        this(fieldType, typ, fieldName, doc, false);
    }

    public FieldDef(String fieldType, String typ, String fieldName, String doc, boolean tracked) {
        this.tracked = tracked;
        this.fieldType = fieldType;
        this.typ = typ;
        this.fieldName = fieldName;
        this.doc = doc;
    }

    public boolean isTracked() { return tracked; }

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
