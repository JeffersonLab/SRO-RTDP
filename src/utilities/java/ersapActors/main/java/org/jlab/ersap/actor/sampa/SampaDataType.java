package org.jlab.ersap.actor.sampa;

import org.jlab.ersap.engine.EngineDataType;
import org.jlab.ersap.engine.EngineDataTypeSupplier;
import org.jlab.ersap.engine.ErsapSerializer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Data types for SAMPA data processing.
 */
public class SampaDataType {

    private SampaDataType() { }

    /**
     * Raw SAMPA data stream from the network.
     */
    public static final EngineDataType SAMPA_STREAM =
            new SampaStreamType();

    /**
     * Decoded SAMPA data.
     */
    public static final EngineDataType SAMPA_DECODED =
            new SampaDecodedType();

    /**
     * Processed SAMPA data.
     */
    public static final EngineDataType SAMPA_PROCESSED =
            new SampaProcessedType();

    /**
     * Serializer for raw SAMPA stream data.
     */
    private static class SampaStreamSerializer implements ErsapSerializer {
        @Override
        public ByteBuffer write(Object data) {
            byte[] bytes = (byte[]) data;
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        }

        @Override
        public Object read(ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }

    /**
     * Serializer for decoded SAMPA data.
     */
    private static class SampaDecodedSerializer implements ErsapSerializer {
        @Override
        public ByteBuffer write(Object data) {
            SampaDecodedData decodedData = (SampaDecodedData) data;
            ByteBuffer buffer = ByteBuffer.allocate(decodedData.getSerializedSize());
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            decodedData.serialize(buffer);
            buffer.flip();
            return buffer;
        }

        @Override
        public Object read(ByteBuffer buffer) {
            return SampaDecodedData.deserialize(buffer);
        }
    }

    /**
     * Serializer for processed SAMPA data.
     */
    private static class SampaProcessedSerializer implements ErsapSerializer {
        @Override
        public ByteBuffer write(Object data) {
            SampaProcessedData processedData = (SampaProcessedData) data;
            ByteBuffer buffer = ByteBuffer.allocate(processedData.getSerializedSize());
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            processedData.serialize(buffer);
            buffer.flip();
            return buffer;
        }

        @Override
        public Object read(ByteBuffer buffer) {
            return SampaProcessedData.deserialize(buffer);
        }
    }

    /**
     * Data type for raw SAMPA stream.
     */
    private static class SampaStreamType implements EngineDataTypeSupplier {
        private static final String MIME_TYPE = "binary/data-sampa-stream";

        @Override
        public EngineDataType createEngineDataType() {
            return new EngineDataType(MIME_TYPE, new SampaStreamSerializer());
        }
    }

    /**
     * Data type for decoded SAMPA data.
     */
    private static class SampaDecodedType implements EngineDataTypeSupplier {
        private static final String MIME_TYPE = "binary/data-sampa-decoded";

        @Override
        public EngineDataType createEngineDataType() {
            return new EngineDataType(MIME_TYPE, new SampaDecodedSerializer());
        }
    }

    /**
     * Data type for processed SAMPA data.
     */
    private static class SampaProcessedType implements EngineDataTypeSupplier {
        private static final String MIME_TYPE = "binary/data-sampa-processed";

        @Override
        public EngineDataType createEngineDataType() {
            return new EngineDataType(MIME_TYPE, new SampaProcessedSerializer());
        }
    }
} 