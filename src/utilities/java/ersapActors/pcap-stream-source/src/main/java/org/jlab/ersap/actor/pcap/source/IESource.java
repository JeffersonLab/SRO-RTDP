package org.jlab.ersap.actor.pcap.source;

import java.io.IOException;

/**
 * Interface for data sources that can be used by ERSAP source engines.
 */
public interface IESource {

    /**
     * Opens the data source.
     * 
     * @throws IOException if an I/O error occurs
     */
    void open() throws IOException;

    /**
     * Closes the data source.
     * 
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;

    /**
     * Gets the next event from the data source.
     * 
     * @return the next event, or null if no more events are available
     * @throws IOException if an I/O error occurs
     */
    byte[] getNextEvent() throws IOException;

    /**
     * Gets the byte order of the data source.
     * 
     * @return the byte order
     */
    java.nio.ByteOrder getByteOrder();

    /**
     * Checks if the data source is open.
     * 
     * @return true if the data source is open, false otherwise
     */
    boolean isOpen();
}