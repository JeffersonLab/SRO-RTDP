package org.jlab.ersap.actor.rtdp.source;

import com.lmax.disruptor.*;

import java.nio.ByteOrder;
import java.util.logging.Logger;

/**
 * Abstract class representing a connection handler for a resource or source.
 * Subclasses must implement the specific details for establishing connections
 * and handling data. This class uses the LMAX Disruptor library to process events.
 */
public abstract class AbstractConnectionHandler {
    private static final Logger LOGGER = Logger.getLogger(AbstractConnectionHandler.class.getName());

    protected final RingBuffer<Event> disruptorRingBuffer;
    private final Sequence sequence; // Tracks the consumer's progress
    private final SequenceBarrier barrier; // Ensures thread-safe access to the RingBuffer
    protected final int sourceId; // Identifies which source this handler is for

    /**
     * Constructor to initialize the disruptor ring buffer.
     *
     * @param disruptorRingBuffer the RingBuffer instance for managing events.
     * @param sourceId the unique identifier for this source
     */
    protected AbstractConnectionHandler(RingBuffer<Event> disruptorRingBuffer, int sourceId) {
        this.disruptorRingBuffer = disruptorRingBuffer;
        this.barrier = disruptorRingBuffer.newBarrier(); // Create a SequenceBarrier for safe access
        this.sequence = new Sequence(RingBuffer.INITIAL_CURSOR_VALUE); // Initialize consumer sequence
        disruptorRingBuffer.addGatingSequences(sequence); // Register this sequence for gating
        this.sourceId = sourceId;
    }

    /**
     * Retrieves the next event from the RingBuffer in a thread-safe manner.
     *
     * @return the next Event object or null if no event is available.
     */
    public Event getNextEvent() {
        try {
            // Retrieve the next available sequence for this consumer
            long nextSequence = sequence.get() + 1;

            // Wait until the sequence is available
            long availableSequence = barrier.waitFor(nextSequence);

            if (nextSequence <= availableSequence) {
                // Retrieve the event from the RingBuffer
                Event event = disruptorRingBuffer.get(nextSequence);

                // Update the consumer sequence after processing
                sequence.set(nextSequence);

                return event;
            }
        } catch (TimeoutException e) {
            LOGGER.warning("Timeout while waiting for the next event.");
        } catch (AlertException e) {
            LOGGER.warning("Alert received while waiting for the next event.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Interrupted while waiting for the next event.");
        }

        return null; // No event available or an error occurred
    }

    /**
     * Publishes the received data to the RingBuffer.
     *
     * @param data the received data to be published.
     */
    protected void publishEvent(byte[] data) {
        long sequence = disruptorRingBuffer.next(); // Reserve next slot
        try {
            Event event = disruptorRingBuffer.get(sequence); // Get entry for sequence
            event.setData(data); // Set data in event
            event.setSourceId(sourceId); // Set source ID in event
        } finally {
            disruptorRingBuffer.publish(sequence); // Publish event
        }
    }

    /**
     * Abstract methods to be implemented by subclasses.
     */
    public abstract Object establishConnection();

    public abstract void listenAndPublish(Object connection);

    public abstract ByteOrder getByteOrder();

    public abstract void closeConnection(Object connection);

    protected abstract byte[] receiveData(Object connection);
    
    /**
     * Get the source ID for this connection handler
     * 
     * @return the source ID
     */
    public int getSourceId() {
        return sourceId;
    }
} 