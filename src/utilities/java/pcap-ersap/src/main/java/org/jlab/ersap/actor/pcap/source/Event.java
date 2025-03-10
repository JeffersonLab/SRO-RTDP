package org.jlab.ersap.actor.pcap.source;

/**
 * Event class used by the LMAX Disruptor ring buffer.
 * This class represents an event in the ring buffer and
 * holds the data payload for the event.
 */
public class Event {
    
    /**
     * The data payload for this event.
     */
    private byte[] data;

    /**
     * Gets the data payload for this event.
     *
     * @return The data payload
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Sets the data payload for this event.
     *
     * @param data The data payload
     */
    public void setData(byte[] data) {
        this.data = data;
    }
} 