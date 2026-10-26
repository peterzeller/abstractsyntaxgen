package test.codec;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NodeCodecTest {
    @Test
    void aSchemaWithoutATypePrefixUsesItsGeneratedFactory() {
        var data = NodeCodec.encode(Codec.Value(7), new NodeCodec.Encoder<Integer>() {
            public Integer child(Element node) { throw new AssertionError("no children"); }
            public Integer reference(Element node) { throw new AssertionError("no references"); }
            public Integer attribute(String field, Object value) { return (Integer) value; }
        });
        var result = (Value) NodeCodec.decode(data, new NodeCodec.Decoder<Integer>() {
            public Element child(Integer key) { throw new AssertionError("no children"); }
            public Element reference(Integer key) { throw new AssertionError("no references"); }
            public Object restoreAttribute(String field, Integer value) { return value; }
        });
        assertEquals(7, result.getNumber());
    }
}
