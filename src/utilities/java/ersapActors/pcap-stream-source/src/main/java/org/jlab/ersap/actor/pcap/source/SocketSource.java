package org.jlab.ersap.actor.pcap.source;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * A source that reads data from a socket connection.
 */
public class SocketSource implements IESource {
    
    private static final Logger LOGGER = Logger.getLogger(SocketSource.class.getName());
    
    private final StreamParameters parameters;
    private final int bufferSize;
    
    private Disruptor<Event> disruptor;
    private RingBuffer<Event> ringBuffer;
    private SocketConnectionHandler connectionHandler;
    private ExecutorService connectionExecutor;
    private boolean isOpen = false;
    
    /**
     * Constructor.
     * 
     * @param parameters the stream parameters
     * @param bufferSize the size of the ring buffer
     */
    public SocketSource(StreamParameters parameters, int bufferSize) {
        this.parameters = parameters;
        this.bufferSize = bufferSize;
    }
    
    @Override
    public void open() throws IOException {
        // Create and start the disruptor
        disruptor = new Disruptor<>(
                Event::new,
                bufferSize,
                DaemonThreadFactory.INSTANCE);
        
        disruptor.start();
        ringBuffer = disruptor.getRingBuffer();
        
        // Create and start the connection handler
        connectionHandler = new SocketConnectionHandler(
                parameters.getHost(),
                parameters.getPort(),
                parameters.getConnectionTimeout(),
                parameters.getReadTimeout(),
                ringBuffer);
        
        connectionExecutor = Executors.newSingleThreadExecutor();
        connectionExecutor.submit(connectionHandler);
        isOpen = true;
    }
    
    @Override
    public void close() throws IOException {
        // Stop the connection handler
        if (connectionHandler != null) {
            connectionHandler.stop();
        }
        
        // Shutdown the executor
        if (connectionExecutor != null) {
            connectionExecutor.shutdown();
            try {
                if (!connectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    connectionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                connectionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Shutdown the disruptor
        if (disruptor != null) {
            disruptor.shutdown();
            disruptor = null;
        }
        
        isOpen = false;
    }
    
    @Override
    public byte[] getNextEvent() throws IOException {
        try {
            Event event = connectionHandler.getEvent();
            return event != null ? event.getData() : null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting event", e);
            return null;
        }
    }
    
    @Override
    public ByteOrder getByteOrder() {
        return parameters.getByteOrder();
    }
    
    @Override
    public boolean isOpen() {
        return isOpen && connectionHandler != null && connectionHandler.isConnected();
    }
    
    /**
     * Checks if the source is connected to the socket.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connectionHandler != null && connectionHandler.isConnected();
    }
    
    /**
     * Gets the ring buffer monitor.
     * 
     * @return the ring buffer monitor, or null if the connection handler is not initialized
     */
    public RingBufferMonitor getRingBufferMonitor() {
        return connectionHandler != null ? connectionHandler.getMonitor() : null;
    }
    
    /**
     * Gets the current ring buffer status as a string.
     * 
     * @return the ring buffer status, or a message indicating that the connection handler is not initialized
     */
    public String getRingBufferStatus() {
        RingBufferMonitor monitor = getRingBufferMonitor();
        return monitor != null ? monitor.toString() : "Connection handler not initialized";
    }
    
    /**
     * Gets the buffer size.
     * 
     * @return the buffer size
     */
    public int getBufferSize() {
        return bufferSize;
    }
    
    /**
     * Gets the stream parameters.
     * 
     * @return the stream parameters
     */
    public StreamParameters getParameters() {
        return parameters;
    }
    
    /**
     * Gets the ring buffer status as a formatted string.
     *
     * @return the ring buffer status as a formatted string
     */
    public String getRingBufferStatusString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SocketSource Status:\n");
        sb.append("  Host: ").append(parameters.getHost()).append("\n");
        sb.append("  Port: ").append(parameters.getPort()).append("\n");
        sb.append("  Connected: ").append(isConnected()).append("\n");
        sb.append("  Buffer Size: ").append(bufferSize).append("\n");
        
        if (connectionHandler != null && connectionHandler.getMonitor() != null) {
            RingBufferMonitor monitor = connectionHandler.getMonitor();
            sb.append("  Used Slots: ").append(monitor.getUsedSlots()).append("\n");
            sb.append("  Available Slots: ").append(monitor.getAvailableSlots()).append("\n");
            sb.append("  Fill Level: ").append(monitor.getFillLevelPercentage()).append("%\n");
            sb.append("  Consumer Lag: ").append(monitor.getConsumerLag()).append("\n");
            sb.append("  Total Events Published: ").append(monitor.getTotalEventsPublished()).append("\n");
            sb.append("  Total Events Consumed: ").append(monitor.getTotalEventsConsumed()).append("\n");
            sb.append("  Publish Throughput: ").append(monitor.getPublishThroughput(TimeUnit.SECONDS)).append(" events/s\n");
        } else {
            sb.append("  Ring Buffer Monitor: Not Available\n");
        }
        
        return sb.toString();
    }
} 