package org.jlab.ersap.actor.pcap;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Represents a packet event from a PCAP file.
 * This class is used to pass packet data between ERSAP services.
 */
public class PacketEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long packetId;
    private final String sourceIp;
    private final String destinationIp;
    private final String protocol;
    private final int etherType;
    private final byte[] data;
    private final long timestamp;

    /**
     * Creates a new packet event.
     *
     * @param packetId      the packet ID
     * @param sourceIp      the source IP address
     * @param destinationIp the destination IP address
     * @param protocol      the protocol name (e.g., "TCP", "UDP", "ICMP")
     * @param etherType     the EtherType value
     * @param data          the packet data
     * @param timestamp     the packet timestamp
     */
    public PacketEvent(long packetId, String sourceIp, String destinationIp,
            String protocol, int etherType, byte[] data, long timestamp) {
        this.packetId = packetId;
        this.sourceIp = sourceIp;
        this.destinationIp = destinationIp;
        this.protocol = protocol;
        this.etherType = etherType;
        this.data = data != null ? Arrays.copyOf(data, data.length) : null;
        this.timestamp = timestamp;
    }

    /**
     * Gets the packet ID.
     *
     * @return the packet ID
     */
    public long getPacketId() {
        return packetId;
    }

    /**
     * Gets the source IP address.
     *
     * @return the source IP address
     */
    public String getSourceIp() {
        return sourceIp;
    }

    /**
     * Gets the destination IP address.
     *
     * @return the destination IP address
     */
    public String getDestinationIp() {
        return destinationIp;
    }

    /**
     * Gets the protocol name.
     *
     * @return the protocol name
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Gets the EtherType value.
     *
     * @return the EtherType value
     */
    public int getEtherType() {
        return etherType;
    }

    /**
     * Gets the packet data.
     *
     * @return the packet data
     */
    public byte[] getData() {
        return data != null ? Arrays.copyOf(data, data.length) : null;
    }

    /**
     * Gets the packet timestamp.
     *
     * @return the packet timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns a string representation of the packet event.
     *
     * @return a string representation of the packet event
     */
    @Override
    public String toString() {
        return "PacketEvent{" +
                "packetId=" + packetId +
                ", sourceIp='" + sourceIp + '\'' +
                ", destinationIp='" + destinationIp + '\'' +
                ", protocol='" + protocol + '\'' +
                ", etherType=0x" + Integer.toHexString(etherType) +
                ", dataLength=" + (data != null ? data.length : 0) +
                ", timestamp=" + timestamp +
                '}';
    }
}