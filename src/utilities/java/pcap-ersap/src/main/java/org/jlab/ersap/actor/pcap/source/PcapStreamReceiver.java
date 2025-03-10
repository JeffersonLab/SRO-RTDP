package org.jlab.ersap.actor.pcap.source;

import java.net.Socket;
import java.nio.ByteOrder;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.ersap.actor.util.IESource;

import com.lmax.disruptor.dsl.Disruptor;

/**
 * Receiver for PCAP stream data.
 * This class implements the IESource interface and provides
 * a way to receive PCAP data from a socket connection.
 */
public class PcapStreamReceiver implements IESource, Callable<String> {

    private static final Logger LOGGER = Logger.getLogger(PcapStreamReceiver.class.getName());

    private SocketConnectionHandler handler;
    private Socket connection;

    /**
     * Constructor to initialize the PCAP stream receiver.
     *
     * @param p The stream parameters
     */
    public PcapStreamReceiver(StreamParameters p) {
        // Create a Disruptor with the specified ring buffer size
        Disruptor<Event> disruptor = new Disruptor<>(
                Event::new, // Event factory
                p.getRingBufferSize(), // Size of the ring buffer
                Runnable::run // Thread factory
        );
        disruptor.start();

        handler = new SocketConnectionHandler(
                disruptor.getRingBuffer(), // Pass the ring buffer to the handler
                p.getHost(), p.getPort(),
                ByteOrder.BIG_ENDIAN,
                p.getConnectionTimeout(),
                p.getReadTimeout(),
                p.getSocketBufferSize());

        connection = (Socket) handler.establishConnection();
        if (connection != null) {
            handler.listenAndPublish(connection);
        } else {
            LOGGER.log(Level.SEVERE, "Failed to establish connection to {0}:{1}",
                    new Object[] { p.getHost(), p.getPort() });
        }
    }

    @Override
    public Object nextEvent() {
        Event event = handler.getNextEvent();
        return event != null ? event.getData() : null;
    }

    @Override
    public ByteOrder getByteOrder() {
        return handler.getByteOrder();
    }

    @Override
    public void close() {
        if (connection != null) {
            handler.closeConnection(connection);
        }
    }

    @Override
    public String call() throws Exception {
        // This method is required by the Callable interface
        // It's used when this class is submitted to an ExecutorService
        try {
            while (true) {
                Object event = nextEvent();
                if (event == null) {
                    break;
                }
                // Process the event if needed
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in PcapStreamReceiver: {0}", e.getMessage());
            throw e;
        } finally {
            close();
        }
        return "PcapStreamReceiver completed";
    }
}