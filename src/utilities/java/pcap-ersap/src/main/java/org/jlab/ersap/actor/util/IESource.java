package org.jlab.ersap.actor.util;

import java.nio.ByteOrder;

/**
 * Interface for source components that provide events to the ERSAP framework.
 * Implementations of this interface are responsible for retrieving events from
 * external sources (e.g., sockets, files) and making them available to the
 * ERSAP processing pipeline.
 */
public interface IESource {

    /**
     * Retrieves the next event from the source data ring (ringBuffer)
     *
     * @return an Object representing the next event. The specifics are
     *         defined by the class implementing this interface.
     */
    public Object nextEvent();

    /**
     * Retrieves the byte order used to read or write data to the source.
     *
     * @return a ByteOrder object indicating the byte order used by the source.
     */
    public ByteOrder getByteOrder();

    /**
     * Closes the connection to the resource or source.
     * It should perform any necessary cleanup and resource deallocation.
     */
    public void close();
}