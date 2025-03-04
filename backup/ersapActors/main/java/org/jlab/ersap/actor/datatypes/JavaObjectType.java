package org.jlab.ersap.actor.datatypes;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineDataTypeInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A data type for Java objects.
 */
public class JavaObjectType implements EngineDataType {

    private static final String MIME_TYPE = "binary/data-jobj";
    private static final String DESCRIPTION = "Java Object";

    public static final JavaObjectType JOBJ = new JavaObjectType();

    @Override
    public ByteOrder byteOrder() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public Object createContainer() {
        return new Object();
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public EngineDataTypeInfo getInfo() {
        return new EngineDataTypeInfo(MIME_TYPE, DESCRIPTION);
    }

    @Override
    public Object getObjectData(Object o) {
        return o;
    }

    @Override
    public Object getObjectData(Object o, Object o1) {
        return o;
    }

    @Override
    public Object getObjectData(ByteBuffer byteBuffer) {
        return byteBuffer;
    }

    @Override
    public Object getObjectData(ByteBuffer byteBuffer, Object o) {
        return byteBuffer;
    }

    @Override
    public ByteBuffer getByteBuffer(Object o) {
        if (o instanceof ByteBuffer) {
            return (ByteBuffer) o;
        }
        return null;
    }

    @Override
    public ByteBuffer getByteBuffer(Object o, Object o1) {
        return getByteBuffer(o);
    }

    @Override
    public String mimeType() {
        return MIME_TYPE;
    }
} 