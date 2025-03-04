package org.jlab.ersap.actor.pcap.source;

/**
 * Represents a data packet in the LMAX Disruptor ring buffer.
 * This is a simple data container for byte arrays.
 */
public class Event {
    private byte[] data;
    private int length;

    /**
     * Default constructor.
     */
    public Event() {
        this.data = null;
        this.length = 0;
    }

    /**
     * Sets the data for this event.
     * 
     * @param data   the data to set
     * @param length the length of the data
     */
    public void set(byte[] data, int length) {
        if (this.data == null || this.data.length < length) {
            this.data = new byte[length];
        }
        System.arraycopy(data, 0, this.data, 0, length);
        this.length = length;
    }

    /**
     * Gets the data for this event.
     * 
     * @return the data
     */
    public byte[] getData() {
        byte[] result = new byte[length];
        System.arraycopy(data, 0, result, 0, length);
        return result;
    }

    /**
     * Gets the length of the data.
     * 
     * @return the length
     */
    public int getLength() {
        return length;
    }

    /**
     * Clears the event data.
     */
    public void clear() {
        this.length = 0;
    }
}