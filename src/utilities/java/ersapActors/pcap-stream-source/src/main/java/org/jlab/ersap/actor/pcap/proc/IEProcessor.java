package org.jlab.ersap.actor.pcap.proc;

/**
 * Interface for PCAP data processors.
 * Implementations of this interface will process PCAP data and return processed results.
 */
public interface IEProcessor {
    
    /**
     * Process the input data.
     * 
     * @param data The input data to process
     * @return The processed data
     */
    Object process(Object data);
    
    /**
     * Reset the processor to its initial state.
     */
    void reset();
    
    /**
     * Clean up resources used by the processor.
     */
    void destruct();
} 