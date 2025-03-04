package org.jlab.ersap.actor.rtdp.source;

/**
 * Configuration parameters for stream connections.
 */
public class StreamParameters {
    private String host = "localhost";
    private int port = 5000;
    private int ringBufferSize = 1024;
    private int connectionTimeout = 5000; // milliseconds
    private int readTimeout = 1000; // milliseconds
    private int sourceId = 0; // Unique identifier for this stream source

    public StreamParameters() {
    }

    public StreamParameters(String host, int port, int sourceId) {
        this.host = host;
        this.port = port;
        this.sourceId = sourceId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public void setRingBufferSize(int ringBufferSize) {
        this.ringBufferSize = ringBufferSize;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
    
    public int getSourceId() {
        return sourceId;
    }
    
    public void setSourceId(int sourceId) {
        this.sourceId = sourceId;
    }
} 