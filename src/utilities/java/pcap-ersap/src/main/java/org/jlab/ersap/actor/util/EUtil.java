package org.jlab.ersap.actor.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Utility class for ERSAP actor framework.
 * This class provides utility methods for common operations
 * used throughout the ERSAP actor implementation.
 */
public class EUtil {

    /**
     * Converts an object to a byte array.
     *
     * @param obj The object to convert
     * @return A byte array representation of the object
     * @throws IOException If an I/O error occurs
     */
    public static byte[] toByteArray(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        }
    }

    /**
     * Converts a byte array back to an object.
     *
     * @param bytes The byte array to convert
     * @return The reconstructed object
     * @throws IOException            If an I/O error occurs
     * @throws ClassNotFoundException If the class of the serialized object cannot
     *                                be found
     */
    public static Object toObject(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        }
    }

    /**
     * Creates a copy of a byte array.
     *
     * @param src The source byte array
     * @return A copy of the source byte array
     */
    public static byte[] copyBytes(byte[] src) {
        if (src == null) {
            return null;
        }
        byte[] dest = new byte[src.length];
        System.arraycopy(src, 0, dest, 0, src.length);
        return dest;
    }
}