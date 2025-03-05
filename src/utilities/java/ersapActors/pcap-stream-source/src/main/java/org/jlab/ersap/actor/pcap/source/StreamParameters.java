package org.jlab.ersap.actor.pcap.source;

import java.nio.ByteOrder;

/**
 * Configuration parameters for the socket connection.
 */
public class StreamParameters {
    private String host = "localhost";
    private int port = 5000;
    private int ringBufferSize = 1024;
    private int connectionTimeout = 5000;
    private int readTimeout = 10000;
    private ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;

    /**
     * Default constructor.
     */
    public StreamParameters() {
        // Default values are set in field declarations
    }
    
    /**
     * Constructor with host and port.
     * 
     * @param host the host
     * @param port the port
     */
    public StreamParameters(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    /**
     * Constructor with all parameters.
     * 
     * @param host the host
     * @param port the port
     * @param connectionTimeout the connection timeout
     * @param readTimeout the read timeout
     */
    public StreamParameters(String host, int port, int connectionTimeout, int readTimeout) {
        this.host = host;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
    }

    /**
     * Gets the host.
     * 
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the host.
     * 
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Gets the port.
     * 
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the port.
     * 
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Gets the ring buffer size.
     * 
     * @return the ring buffer size
     */
    public int getRingBufferSize() {
        return ringBufferSize;
    }

    /**
     * Sets the ring buffer size.
     * 
     * @param ringBufferSize the ring buffer size to set
     */
    public void setRingBufferSize(int ringBufferSize) {
        this.ringBufferSize = ringBufferSize;
    }

    /**
     * Gets the connection timeout.
     * 
     * @return the connection timeout
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Sets the connection timeout.
     * 
     * @param connectionTimeout the connection timeout to set
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Gets the read timeout.
     * 
     * @return the read timeout
     */
    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Sets the read timeout.
     * 
     * @param readTimeout the read timeout to set
     */
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * Gets the byte order.
     * 
     * @return the byte order
     */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    /**
     * Sets the byte order.
     * 
     * @param byteOrder the byte order to set
     */
    public void setByteOrder(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
    }

    /**
     * Sets the byte order from a string.
     * 
     * @param byteOrderStr the byte order string ("BIG_ENDIAN" or "LITTLE_ENDIAN")
     */
    public void setByteOrder(String byteOrderStr) {
        if ("BIG_ENDIAN".equalsIgnoreCase(byteOrderStr)) {
            this.byteOrder = ByteOrder.BIG_ENDIAN;
        } else if ("LITTLE_ENDIAN".equalsIgnoreCase(byteOrderStr)) {
            this.byteOrder = ByteOrder.LITTLE_ENDIAN;
        } else {
            throw new IllegalArgumentException("Invalid byte order: " + byteOrderStr);
        }
    }
}