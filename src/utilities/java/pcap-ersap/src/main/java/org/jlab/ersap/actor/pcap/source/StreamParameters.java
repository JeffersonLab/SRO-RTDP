package org.jlab.ersap.actor.pcap.source;

import java.nio.ByteOrder;

import org.jlab.ersap.actor.util.EConstants;

/**
 * Parameters for configuring a stream connection.
 * This class holds configuration parameters for establishing
 * and maintaining a connection to a data source.
 */
public class StreamParameters {

    private String host = EConstants.udf;
    private int port = 9001;
    private ByteOrder byteOrder;
    private int connectionTimeout = 5000; // Connection timeout in milliseconds.
    private int readTimeout = 2000; // Read timeout in milliseconds.
    private int ringBufferSize = 1024;
    private int socketBufferSize = 16384; // Socket buffer size in bytes.

    /**
     * Gets the host name or IP address.
     *
     * @return The host name or IP address
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the host name or IP address.
     *
     * @param host The host name or IP address
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Gets the port number.
     *
     * @return The port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the port number.
     *
     * @param port The port number
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Gets the byte order.
     *
     * @return The byte order
     */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    /**
     * Sets the byte order.
     *
     * @param byteOrder The byte order
     */
    public void setByteOrder(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
    }

    /**
     * Gets the connection timeout in milliseconds.
     *
     * @return The connection timeout
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Sets the connection timeout in milliseconds.
     *
     * @param connectionTimeout The connection timeout
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Gets the read timeout in milliseconds.
     *
     * @return The read timeout
     */
    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Sets the read timeout in milliseconds.
     *
     * @param readTimeout The read timeout
     */
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * Gets the ring buffer size.
     *
     * @return The ring buffer size
     */
    public int getRingBufferSize() {
        return ringBufferSize;
    }

    /**
     * Sets the ring buffer size.
     *
     * @param ringBufferSize The ring buffer size
     */
    public void setRingBufferSize(int ringBufferSize) {
        this.ringBufferSize = ringBufferSize;
    }

    /**
     * Gets the socket buffer size in bytes.
     *
     * @return The socket buffer size
     */
    public int getSocketBufferSize() {
        return socketBufferSize;
    }

    /**
     * Sets the socket buffer size in bytes.
     *
     * @param socketBufferSize The socket buffer size
     */
    public void setSocketBufferSize(int socketBufferSize) {
        this.socketBufferSize = socketBufferSize;
    }
}