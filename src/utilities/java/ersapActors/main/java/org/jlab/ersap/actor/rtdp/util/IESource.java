package org.jlab.ersap.actor.rtdp.util;

import java.nio.ByteOrder;

/**
 * Interface for ERSAP source components.
 * Defines the contract for classes that act as data sources in the ERSAP framework.
 */
public interface IESource {

    /**
     * Retrieves the next event from the source data ring (ringBuffer)
     *
     * @return an Object representing the next event. The specifics are
     * defined by the class implementing this interface.
     */
    Object nextEvent();

    /**
     * Retrieves the byte order used to read or write data to the source.
     *
     * @return a ByteOrder object indicating the byte order used by the source.
     */
    ByteOrder getByteOrder();

    /**
     * Closes the connection to the resource or source.
     * It should perform any necessary cleanup and resource deallocation.
     */
    void close();
} 