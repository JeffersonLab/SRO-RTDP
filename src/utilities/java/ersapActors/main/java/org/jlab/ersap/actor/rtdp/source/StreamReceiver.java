package org.jlab.ersap.actor.rtdp.source;

import com.lmax.disruptor.dsl.Disruptor;
import org.jlab.ersap.actor.rtdp.util.IESource;

import java.net.Socket;
import java.nio.ByteOrder;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class that manages a socket connection to a data stream source.
 * It implements the IESource interface to provide a standardized way to retrieve events.
 */
public class StreamReceiver implements IESource, Callable<String> {
    private static final Logger LOGGER = Logger.getLogger(StreamReceiver.class.getName());

    private final SocketConnectionHandler handler;
    private Socket connection;
    private final StreamParameters params;
    private final Disruptor<Event> disruptor;

    /**
     * Constructor for StreamReceiver.
     *
     * @param params the parameters for the stream connection
     */
    public StreamReceiver(StreamParameters params) {
        this.params = params;
        
        // Create and start the disruptor
        disruptor = new Disruptor<>(
                Event::new, 
                params.getRingBufferSize(), 
                r -> {
                    Thread t = new Thread(r);
                    t.setName("Disruptor-" + params.getHost() + ":" + params.getPort() + "-" + params.getSourceId());
                    return t;
                }
        );
        disruptor.start();

        // Create the socket connection handler
        handler = new SocketConnectionHandler(
                disruptor.getRingBuffer(),
                params.getHost(), 
                params.getPort(),
                params.getSourceId(),
                ByteOrder.BIG_ENDIAN,
                params.getConnectionTimeout(),
                params.getReadTimeout()
        );

        // Establish the connection and start listening
        try {
            connection = handler.establishConnection();
            handler.listenAndPublish(connection);
        } catch (IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to establish connection: " + e.getMessage(), e);
            throw e; // Re-throw to signal failure to the caller
        }
    }

    /**
     * Get the next event from the ring buffer.
     *
     * @return the next event, or null if no event is available
     */
    @Override
    public Object nextEvent() {
        return handler.getNextEvent();
    }

    /**
     * Get the byte order used for this connection.
     *
     * @return the byte order
     */
    @Override
    public ByteOrder getByteOrder() {
        return handler.getByteOrder();
    }

    /**
     * Close the connection and clean up resources.
     */
    @Override
    public void close() {
        handler.closeConnection(connection);
        disruptor.shutdown();
        LOGGER.info(String.format("StreamReceiver for %s:%d (Source ID: %d) closed", 
                params.getHost(), params.getPort(), params.getSourceId()));
    }

    /**
     * Get the source ID for this receiver.
     *
     * @return the source ID
     */
    public int getSourceId() {
        return params.getSourceId();
    }

    /**
     * Implementation of Callable interface.
     * This method is used when the StreamReceiver is submitted to an ExecutorService.
     *
     * @return a status message
     */
    @Override
    public String call() {
        try {
            // This method will be called when the StreamReceiver is submitted to an ExecutorService
            // It should run until the connection is closed or an error occurs
            while (true) {
                Thread.sleep(1000); // Sleep to avoid busy waiting
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "StreamReceiver thread interrupted";
        } finally {
            close();
        }
    }
} 