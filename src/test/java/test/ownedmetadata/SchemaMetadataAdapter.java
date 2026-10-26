package test.ownedmetadata;

import java.util.List;

/** Type semantics only: generated code enumerates all fields and containers. */
public class SchemaMetadataAdapter implements OMNodeGraphs.Adapter {
    public OMNodeGraphs.Parts decompose(Object value) {
        return value instanceof GraphContractsTest.Choice choice ? new OMNodeGraphs.Parts(choice.left() ? "left" : "right",List.of(choice.value())) : null;
    }
    public Object compose(String tag,List<Object> values) { return new GraphContractsTest.Choice(tag.equals("left"),values.get(0)); }
}
