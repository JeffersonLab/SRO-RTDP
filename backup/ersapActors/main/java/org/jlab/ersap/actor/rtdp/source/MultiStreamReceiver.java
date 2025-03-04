package org.jlab.ersap.actor.rtdp.source;

import org.jlab.ersap.actor.rtdp.util.IESource;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class that manages multiple StreamReceiver instances and aggregates their data.
 * It implements the IESource interface to provide a standardized way to retrieve events.
 */
public class MultiStreamReceiver implements IESource {
    private static final Logger LOGGER = Logger.getLogger(MultiStreamReceiver.class.getName());

    private final ExecutorService executorService;
    private final List<StreamReceiver> streamReceivers;
    private final List<Future<String>> receiverFutures;
    private final int numStreamReceivers;
    private final AggregationStrategy aggregationStrategy;
    
    /**
     * Enum defining different strategies for aggregating data from multiple streams.
     */
    public enum AggregationStrategy {
        /**
         * Return an array of events, one from each stream (may contain nulls).
         */
        ARRAY,
        
        /**
         * Return the first non-null event found when checking all streams.
         */
        FIRST_AVAILABLE,
        
        /**
         * Combine all available events into a single byte array.
         */
        COMBINE
    }

    /**
     * Constructor for MultiStreamReceiver.
     *
     * @param params the parameters for each stream connection
     * @param strategy the strategy to use for aggregating data from multiple streams
     */
    public MultiStreamReceiver(StreamParameters[] params, AggregationStrategy strategy) {
        this.numStreamReceivers = params.length;
        this.aggregationStrategy = strategy;
        
        // Create a thread pool for managing the stream receivers
        this.executorService = Executors.newFixedThreadPool(numStreamReceivers, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("StreamReceiver-Pool-" + t.getId());
            return t;
        });
        
        this.streamReceivers = new ArrayList<>(numStreamReceivers);
        this.receiverFutures = new ArrayList<>(numStreamReceivers);
        
        // Create and start each stream receiver
        for (int i = 0; i < numStreamReceivers; i++) {
            try {
                StreamReceiver receiver = new StreamReceiver(params[i]);
                streamReceivers.add(receiver);
                
                // Submit the receiver to the executor service
                Future<String> future = executorService.submit(receiver);
                receiverFutures.add(future);
                
                LOGGER.info(String.format("Started StreamReceiver %d/%d for %s:%d (Source ID: %d)", 
                        i + 1, numStreamReceivers, params[i].getHost(), params[i].getPort(), params[i].getSourceId()));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, String.format(
                        "Failed to create StreamReceiver %d/%d for %s:%d (Source ID: %d): %s", 
                        i + 1, numStreamReceivers, params[i].getHost(), params[i].getPort(), 
                        params[i].getSourceId(), e.getMessage()), e);
            }
        }
        
        if (streamReceivers.isEmpty()) {
            throw new IllegalStateException("Failed to create any StreamReceivers");
        }
        
        LOGGER.info(String.format("MultiStreamReceiver initialized with %d/%d receivers using %s strategy", 
                streamReceivers.size(), numStreamReceivers, strategy));
    }

    /**
     * Get the next event from all ring buffers, aggregated according to the strategy.
     *
     * @return the aggregated event data, or null if no events are available
     */
    @Override
    public Object nextEvent() {
        switch (aggregationStrategy) {
            case ARRAY:
                return getEventsAsArray();
            case FIRST_AVAILABLE:
                return getFirstAvailableEvent();
            case COMBINE:
                return getCombinedEvents();
            default:
                throw new IllegalStateException("Unknown aggregation strategy: " + aggregationStrategy);
        }
    }

    /**
     * Get events from all receivers as an array.
     *
     * @return an array of events, one from each receiver (may contain nulls)
     */
    private Object[] getEventsAsArray() {
        Object[] events = new Object[streamReceivers.size()];
        for (int i = 0; i < streamReceivers.size(); i++) {
            events[i] = streamReceivers.get(i).nextEvent();
        }
        return events;
    }

    /**
     * Get the first non-null event found when checking all receivers.
     *
     * @return the first available event, or null if no events are available
     */
    private Object getFirstAvailableEvent() {
        for (StreamReceiver receiver : streamReceivers) {
            Object event = receiver.nextEvent();
            if (event != null) {
                return event;
            }
        }
        return null;
    }

    /**
     * Combine all available events into a single byte array.
     *
     * @return a byte array containing all available event data, or null if no events are available
     */
    private byte[] getCombinedEvents() {
        List<byte[]> dataList = new ArrayList<>();
        int totalLength = 0;
        
        // Collect all available events and calculate total length
        for (StreamReceiver receiver : streamReceivers) {
            Event event = (Event) receiver.nextEvent();
            if (event != null) {
                byte[] data = event.getData();
                if (data != null && data.length > 0) {
                    dataList.add(data);
                    totalLength += data.length;
                }
            }
        }
        
        if (dataList.isEmpty()) {
            return null;
        }
        
        // Combine all data into a single byte array
        byte[] combined = new byte[totalLength];
        int offset = 0;
        for (byte[] data : dataList) {
            System.arraycopy(data, 0, combined, offset, data.length);
            offset += data.length;
        }
        
        return combined;
    }

    /**
     * Get the byte order used for these connections.
     *
     * @return the byte order of the first receiver, or BIG_ENDIAN if no receivers are available
     */
    @Override
    public ByteOrder getByteOrder() {
        if (!streamReceivers.isEmpty()) {
            return streamReceivers.get(0).getByteOrder();
        }
        return ByteOrder.BIG_ENDIAN; // Default
    }

    /**
     * Close all connections and clean up resources.
     */
    @Override
    public void close() {
        // Cancel all futures
        for (Future<String> future : receiverFutures) {
            future.cancel(true);
        }
        
        // Close all receivers
        for (StreamReceiver receiver : streamReceivers) {
            try {
                receiver.close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing StreamReceiver: " + e.getMessage(), e);
            }
        }
        
        // Shutdown the executor service
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warning("Executor service did not terminate in the expected time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Interrupted while waiting for executor service to terminate", e);
        }
        
        LOGGER.info("MultiStreamReceiver closed");
    }
} 