package org.jlab.epsci.ersap.actor.pcap.source;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.lmax.disruptor.*;

/**
 * Base class for connection handling with LMAX Disruptor integration.
 * Provides thread-safe event retrieval from the ring buffer.
 */
public abstract class AbstractConnectionHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(AbstractConnectionHandler.class.getName());

    protected final RingBuffer<Event> disruptorRingBuffer;
    protected final Sequence sequence;
    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final AtomicBoolean connected = new AtomicBoolean(false);

    // Ring buffer monitoring
    protected final RingBufferMonitor monitor;
    protected long bytesPublishedSinceLastUpdate = 0;
    protected long lastMonitorUpdateTime = System.currentTimeMillis();
    protected static final long MONITOR_UPDATE_INTERVAL_MS = 1000; // Update monitor every second

    /**
     * Constructor.
     * 
     * @param disruptorRingBuffer the ring buffer to publish events to
     */
    public AbstractConnectionHandler(RingBuffer<Event> disruptorRingBuffer) {
        this.disruptorRingBuffer = disruptorRingBuffer;
        this.sequence = new Sequence(RingBuffer.INITIAL_CURSOR_VALUE); // Initialize consumer sequence
        disruptorRingBuffer.addGatingSequences(sequence);
        this.monitor = new RingBufferMonitor(disruptorRingBuffer, sequence);
    }

    /**
     * Gets the next event from the ring buffer.
     * 
     * @return the next event, or null if no more events are available
     * @throws IOException if an I/O error occurs
     */
    public Event getEvent() throws IOException {
        try {
            long nextSequence = sequence.get() + 1;

            // Check if we've caught up to the producer
            if (nextSequence > disruptorRingBuffer.getCursor()) {
                return null; // No more events available
            }

            long availableSequence = disruptorRingBuffer.getCursor();

            if (availableSequence >= nextSequence) {
                Event event = disruptorRingBuffer.get(nextSequence);
                
                // Advance the sequence
                sequence.set(nextSequence);

                return event;
            }

            return null; // No more events available

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting next event from ring buffer", e);
            throw new IOException("Error getting next event from ring buffer", e);
        }
    }

    /**
     * Publishes an event to the ring buffer.
     * 
     * @param data   the data to publish
     * @param length the length of the data
     */
    protected void publishEvent(byte[] data, int length) {
        long sequence = disruptorRingBuffer.next();
        try {
            Event event = disruptorRingBuffer.get(sequence);
            event.set(data, length);
        } finally {
            disruptorRingBuffer.publish(sequence);

            // Update monitoring statistics
            bytesPublishedSinceLastUpdate += length;
            updateMonitorIfNeeded();
        }
    }

    /**
     * Updates the ring buffer monitor if the update interval has elapsed.
     */
    protected void updateMonitorIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastMonitorUpdateTime >= MONITOR_UPDATE_INTERVAL_MS) {
            monitor.update(bytesPublishedSinceLastUpdate);
            bytesPublishedSinceLastUpdate = 0;
            lastMonitorUpdateTime = now;

            // Log the ring buffer status
            LOGGER.info(monitor.toString());
        }
    }

    /**
     * Gets the ring buffer monitor.
     * 
     * @return the ring buffer monitor
     */
    public RingBufferMonitor getMonitor() {
        return monitor;
    }

    /**
     * Starts the connection handler.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread thread = new Thread(this);
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Stops the connection handler.
     */
    public void stop() {
        running.set(false);
    }

    /**
     * Checks if the connection handler is running.
     * 
     * @return true if the connection handler is running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Checks if the connection handler is connected.
     * 
     * @return true if the connection handler is connected, false otherwise
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Opens the connection.
     * 
     * @throws IOException if an I/O error occurs
     */
    public abstract void open() throws IOException;

    /**
     * Closes the connection.
     * 
     * @throws IOException if an I/O error occurs
     */
    public abstract void close() throws IOException;
}