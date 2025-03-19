package org.jlab.epsci.ersap.actor.pcap.source;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A source that manages multiple socket connections and aggregates their data.
 * Each socket connection is handled by a separate SocketSource instance.
 */
public class MultiSocketSource implements IESource {
    
    private static final Logger LOGGER = Logger.getLogger(MultiSocketSource.class.getName());
    
    private List<SocketSource> socketSources;
    private int numSources;
    private int currentSourceIndex = 0; // For round-robin event retrieval
    private boolean isOpen = false;
    
    /**
     * Default constructor with no connections.
     */
    public MultiSocketSource() {
        this.socketSources = new ArrayList<>();
        this.numSources = 0;
    }
    
    /**
     * Constructor.
     * 
     * @param parameters the array of stream parameters for each connection
     * @param bufferSizes the array of buffer sizes for each connection
     */
    public MultiSocketSource(StreamParameters[] parameters, int[] bufferSizes) {
        this.numSources = parameters.length;
        this.socketSources = new ArrayList<>(numSources);
        
        for (int i = 0; i < numSources; i++) {
            SocketSource socketSource = new SocketSource(parameters[i], bufferSizes[i]);
            socketSources.add(socketSource);
        }
    }
    
    /**
     * Opens connections to all configured socket sources.
     * 
     * @param parametersList the list of stream parameters for each connection
     * @throws IOException if all connections fail
     */
    public void open(List<StreamParameters> parametersList) throws IOException {
        // Clear existing sources if any
        if (!socketSources.isEmpty()) {
            try {
                close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing existing connections", e);
            }
            socketSources.clear();
        }
        
        // Create new socket sources with default buffer size
        int defaultBufferSize = 1024;
        this.numSources = parametersList.size();
        
        for (StreamParameters params : parametersList) {
            SocketSource socketSource = new SocketSource(params, defaultBufferSize);
            socketSources.add(socketSource);
        }
        
        // Open all connections
        open();
    }
    
    @Override
    public void open() throws IOException {
        List<Exception> exceptions = new ArrayList<>();
        
        for (int i = 0; i < numSources; i++) {
            try {
                socketSources.get(i).open();
                LOGGER.info("Opened connection " + (i + 1) + " of " + numSources);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to open connection " + (i + 1) + " of " + numSources, e);
                exceptions.add(e);
            }
        }
        
        // If all connections failed, throw an exception
        if (exceptions.size() == numSources && numSources > 0) {
            IOException exception = new IOException("Failed to open any connections");
            for (Exception e : exceptions) {
                exception.addSuppressed(e);
            }
            throw exception;
        }
        
        isOpen = true;
    }
    
    @Override
    public void close() throws IOException {
        for (int i = 0; i < numSources; i++) {
            try {
                socketSources.get(i).close();
                LOGGER.info("Closed connection " + (i + 1) + " of " + numSources);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing connection " + (i + 1) + " of " + numSources, e);
            }
        }
        
        isOpen = false;
    }
    
    @Override
    public byte[] getNextEvent() throws IOException {
        if (numSources == 0) {
            return null;
        }
        
        // Try to get an event using round-robin strategy
        for (int i = 0; i < numSources; i++) {
            // Calculate the index using round-robin
            int index = (currentSourceIndex + i) % numSources;
            SocketSource source = socketSources.get(index);
            
            if (source.isConnected()) {
                byte[] event = source.getNextEvent();
                if (event != null) {
                    // Update the index for the next call
                    currentSourceIndex = (index + 1) % numSources;
                    return event;
                }
            }
        }
        
        // No events available from any source
        return null;
    }
    
    @Override
    public ByteOrder getByteOrder() {
        // Return the byte order of the first source, or default to BIG_ENDIAN if no sources
        if (numSources > 0) {
            return socketSources.get(0).getByteOrder();
        }
        return ByteOrder.BIG_ENDIAN;
    }
    
    @Override
    public boolean isOpen() {
        return isOpen && isConnected();
    }
    
    /**
     * Checks if at least one source is connected.
     * 
     * @return true if at least one source is connected, false otherwise
     */
    public boolean isConnected() {
        // Return true if at least one source is connected
        for (SocketSource source : socketSources) {
            if (source.isConnected()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets the number of sources.
     * 
     * @return the number of sources
     */
    public int getNumSources() {
        return numSources;
    }
    
    /**
     * Gets the list of socket sources.
     * 
     * @return the list of socket sources
     */
    public List<SocketSource> getSocketSources() {
        return socketSources;
    }
    
    /**
     * Gets the ring buffer monitors for all sources.
     * 
     * @return the list of ring buffer monitors
     */
    public List<RingBufferMonitor> getRingBufferMonitors() {
        List<RingBufferMonitor> monitors = new ArrayList<>();
        for (SocketSource source : socketSources) {
            monitors.add(source.getRingBufferMonitor());
        }
        return monitors;
    }
    
    /**
     * Gets the ring buffer status for all sources as a JSON array.
     * 
     * @return the ring buffer status as a JSON array
     */
    public String getRingBufferStatusJson() {
        try {
            JSONObject status = new JSONObject();
            JSONObject[] sourceStatuses = new JSONObject[numSources];
            
            for (int i = 0; i < numSources; i++) {
                SocketSource source = socketSources.get(i);
                RingBufferMonitor monitor = source.getRingBufferMonitor();
                
                if (monitor != null) {
                    JSONObject sourceStatus = new JSONObject();
                    sourceStatus.put("sourceIndex", i);
                    sourceStatus.put("host", source.getParameters().getHost());
                    sourceStatus.put("port", source.getParameters().getPort());
                    sourceStatus.put("connected", source.isConnected());
                    sourceStatus.put("bufferSize", source.getBufferSize());
                    sourceStatus.put("usedSlots", monitor.getUsedSlots());
                    sourceStatus.put("availableSlots", monitor.getAvailableSlots());
                    sourceStatus.put("fillLevelPercentage", monitor.getFillLevelPercentage());
                    sourceStatus.put("consumerLag", monitor.getConsumerLag());
                    sourceStatus.put("totalEventsPublished", monitor.getTotalEventsPublished());
                    sourceStatus.put("totalEventsConsumed", monitor.getTotalEventsConsumed());
                    sourceStatus.put("totalBytesPublished", monitor.getTotalBytesPublished());
                    sourceStatus.put("publishThroughputEventsPerSecond", monitor.getPublishThroughput(TimeUnit.SECONDS));
                    sourceStatus.put("publishThroughputMBPerSecond", 
                            monitor.getBytesThroughput(TimeUnit.SECONDS) / (1024 * 1024));
                    
                    sourceStatuses[i] = sourceStatus;
                }
            }
            
            status.put("numSources", numSources);
            status.put("sources", sourceStatuses);
            
            return status.toString(2); // Pretty print with 2-space indentation
        } catch (JSONException e) {
            return "Error creating JSON status: " + e.getMessage();
        }
    }
    
    /**
     * Gets the ring buffer status for all sources as a formatted string.
     * 
     * @return the ring buffer status as a formatted string
     */
    public String getRingBufferStatus() {
        StringBuilder status = new StringBuilder();
        status.append("MultiSocketSource Status (").append(numSources).append(" sources):\n");
        
        for (int i = 0; i < numSources; i++) {
            SocketSource source = socketSources.get(i);
            status.append("Source ").append(i + 1).append(" (")
                  .append(source.getParameters().getHost()).append(":")
                  .append(source.getParameters().getPort()).append("): ")
                  .append(source.isConnected() ? "CONNECTED" : "DISCONNECTED")
                  .append("\n");
            
            if (source.isConnected()) {
                status.append("  ").append(source.getRingBufferStatus().replace("\n", "\n  ")).append("\n");
            }
        }
        
        return status.toString();
    }
    
    /**
     * Gets the ring buffer status as a formatted string.
     *
     * @return the ring buffer status as a formatted string
     */
    public String getRingBufferStatusString() {
        if (numSources == 0) {
            return "No sources configured";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("MultiSocketSource Status:\n");
        sb.append("Total Sources: ").append(numSources).append("\n");
        sb.append("Connected Sources: ").append(getConnectedSourceCount()).append("\n\n");
        
        for (int i = 0; i < numSources; i++) {
            SocketSource source = socketSources.get(i);
            sb.append("Source ").append(i + 1).append(":\n");
            sb.append("  Host: ").append(source.getParameters().getHost()).append("\n");
            sb.append("  Port: ").append(source.getParameters().getPort()).append("\n");
            sb.append("  Connected: ").append(source.isConnected()).append("\n");
            sb.append("  Status: ").append(source.getRingBufferStatus()).append("\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gets the number of connected sources.
     *
     * @return the number of connected sources
     */
    public int getConnectedSourceCount() {
        int count = 0;
        for (SocketSource source : socketSources) {
            if (source.isConnected()) {
                count++;
            }
        }
        return count;
    }
} 