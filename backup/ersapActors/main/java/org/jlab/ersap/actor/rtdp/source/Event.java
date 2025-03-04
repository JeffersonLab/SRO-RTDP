package org.jlab.ersap.actor.rtdp.source;

/**
 * Event class for use with LMAX Disruptor RingBuffer.
 * This class stores the payload data and source information.
 */
public class Event {
    private byte[] data;
    private int sourceId; // Identifies which source/server this event came from

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
    
    public int getSourceId() {
        return sourceId;
    }
    
    public void setSourceId(int sourceId) {
        this.sourceId = sourceId;
    }
} 