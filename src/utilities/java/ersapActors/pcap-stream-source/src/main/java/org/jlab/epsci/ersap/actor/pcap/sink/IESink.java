package org.jlab.epsci.ersap.actor.pcap.sink;

import java.io.IOException;

/**
 * Interface for PCAP data sinks.
 * Implementations of this interface will handle writing processed PCAP data to various destinations.
 */
public interface IESink {
    
    /**
     * Opens the sink for writing.
     * 
     * @throws IOException if an I/O error occurs
     */
    void open() throws IOException;
    
    /**
     * Closes the sink.
     * 
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;
    
    /**
     * Writes data to the sink.
     * 
     * @param data The data to write
     * @throws IOException if an I/O error occurs
     */
    void write(Object data) throws IOException;
    
    /**
     * Flushes any buffered data to the sink.
     * 
     * @throws IOException if an I/O error occurs
     */
    void flush() throws IOException;
    
    /**
     * Checks if the sink is open.
     * 
     * @return true if the sink is open, false otherwise
     */
    boolean isOpen();
} 