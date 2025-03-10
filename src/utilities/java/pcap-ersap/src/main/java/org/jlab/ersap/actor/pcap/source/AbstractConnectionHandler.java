package org.jlab.ersap.actor.pcap.source;

import java.nio.ByteOrder;
import java.util.logging.Logger;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.TimeoutException;

/**
 * Abstract base class for connection handlers.
 * This class provides the basic structure for handling connections
 * to data sources and publishing events to a ring buffer.
 */
public abstract class AbstractConnectionHandler {

    private static final Logger LOGGER = Logger.getLogger(AbstractConnectionHandler.class.getName());

    protected final RingBuffer<Event> disruptorRingBuffer;
    private final Sequence sequence; // Tracks the consumer's progress
    private final SequenceBarrier barrier; // Ensures thread-safe access to the RingBuffer

    /**
     * Constructor to initialize the disruptor ring buffer.
     *
     * @param disruptorRingBuffer The ring buffer to use
     */
    protected AbstractConnectionHandler(RingBuffer<Event> disruptorRingBuffer) {
        this.disruptorRingBuffer = disruptorRingBuffer;
        this.barrier = disruptorRingBuffer.newBarrier(); // Create a SequenceBarrier for safe access
        this.sequence = new Sequence(RingBuffer.INITIAL_CURSOR_VALUE); // Initialize consumer sequence
        disruptorRingBuffer.addGatingSequences(sequence); // Register this sequence for gating
    }

    /**
     * Retrieves the next event from the RingBuffer in a thread-safe manner.
     *
     * @return The next event, or null if no event is available
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
     * Establishes a connection to the data source.
     *
     * @return The connection object
     */
    public abstract Object establishConnection();

    /**
     * Listens for data on the connection and publishes events to the ring buffer.
     *
     * @param connection The connection object
     */
    public abstract void listenAndPublish(Object connection);

    /**
     * Gets the byte order used for reading data.
     *
     * @return The byte order
     */
    public abstract ByteOrder getByteOrder();

    /**
     * Closes the connection to the data source.
     *
     * @param connection The connection object
     */
    public abstract void closeConnection(Object connection);

    /**
     * Receives data from the connection.
     *
     * @param connection The connection object
     * @return The received data as a byte array, or null if no data is available
     */
    protected abstract byte[] receiveData(Object connection);
}