package com.example.schema;

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.apache.avro.SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE;
import static org.apache.avro.SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The check a schema registry runs before it accepts a new schema version, done with the Avro library alone.
 * Backward: consumers on the NEW schema can read data written with the OLD one. Forward: the other way round.
 */
class AvroCompatibilityTest {

    private static final Schema V1 = load("order-placed-v1.avsc");

    private static Schema load(String file) {
        try (InputStream in = AvroCompatibilityTest.class.getResourceAsStream("/avro/" + file)) {
            return new Schema.Parser().parse(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SchemaCompatibilityType backward(Schema next) {
        return SchemaCompatibility.checkReaderWriterCompatibility(next, V1).getType();   // reader = new, writer = old
    }

    private static SchemaCompatibilityType forward(Schema next) {
        return SchemaCompatibility.checkReaderWriterCompatibility(V1, next).getType();   // reader = old, writer = new
    }

    @Test
    void addingAFieldWithADefault_isBackwardAndForwardCompatible() {
        Schema next = load("v2-add-field-with-default.avsc");

        assertThat(backward(next)).isEqualTo(COMPATIBLE);
        assertThat(forward(next)).isEqualTo(COMPATIBLE);
    }

    @Test
    void addingAFieldWithoutADefault_newConsumersCannotReadOldData() {
        Schema next = load("v2-add-field-without-default.avsc");

        assertThat(backward(next)).isEqualTo(INCOMPATIBLE);   // old records have no currency, and there's no default
        assertThat(forward(next)).isEqualTo(COMPATIBLE);      // old consumers skip the extra field
    }

    @Test
    void renamingAField_breaksBothDirections() {
        Schema next = load("v2-rename-field.avsc");

        assertThat(backward(next)).isEqualTo(INCOMPATIBLE);
        assertThat(forward(next)).isEqualTo(INCOMPATIBLE);
    }

    @Test
    void changingAFieldsType_breaksBothDirections() {
        Schema next = load("v2-change-type.avsc");

        assertThat(backward(next)).isEqualTo(INCOMPATIBLE);
        assertThat(forward(next)).isEqualTo(INCOMPATIBLE);
    }

    @Test
    void oldBytes_readWithTheNewSchema_getTheDefault() throws IOException {
        GenericRecord written = new GenericData.Record(V1);
        written.put("orderId", "o-1");
        written.put("amountCents", 1999L);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(bytes, null);
        new GenericDatumWriter<GenericRecord>(V1).write(written, encoder);
        encoder.flush();

        Schema next = load("v2-add-field-with-default.avsc");
        GenericRecord read = new GenericDatumReader<GenericRecord>(V1, next)   // writer's schema, reader's schema
                .read(null, DecoderFactory.get().binaryDecoder(bytes.toByteArray(), null));

        assertThat(read.get("amountCents")).isEqualTo(1999L);
        assertThat(read.get("currency").toString()).isEqualTo("EUR");
        // Avro binary has no field names at all: the reader needs the writer's exact schema to decode it.
        // That's why a registry stores every version and each message carries a schema id.
        assertThat(bytes.size()).isLessThan(12);
    }
}
