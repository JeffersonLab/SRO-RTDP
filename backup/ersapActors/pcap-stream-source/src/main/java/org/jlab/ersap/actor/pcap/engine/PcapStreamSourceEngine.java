package org.jlab.ersap.actor.pcap.engine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ERSAP source engine that reads data from a socket stream.
 */
public class PcapStreamSourceEngine extends AbstractEventReaderService<IESource> {

    private static final Logger LOGGER = Logger.getLogger(PcapStreamSourceEngine.class.getName());

    private static final String CONFIG_HOST = "host";
    private static final String CONFIG_PORT = "port";
    private static final String CONFIG_CONNECTION_TIMEOUT = "connection_timeout";
    private static final String CONFIG_READ_TIMEOUT = "read_timeout";
    private static final String CONFIG_BUFFER_SIZE = "buffer_size";

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 9000;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 5000;
    private static final int DEFAULT_READ_TIMEOUT = 30000;
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    @Override
    protected IESource createReader(String source) throws EventReaderException {
        StreamParameters parameters = new StreamParameters(
                DEFAULT_HOST,
                DEFAULT_PORT,
                DEFAULT_CONNECTION_TIMEOUT,
                DEFAULT_READ_TIMEOUT);

        try {
            SocketSource socketSource = new SocketSource(parameters, DEFAULT_BUFFER_SIZE);
            socketSource.open();
            return socketSource;
        } catch (IOException e) {
            throw new EventReaderException(e);
        }
    }

    @Override
    protected void closeReader() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing reader", e);
        }
    }

    @Override
    protected int readEventCount() throws EventReaderException {
        return Integer.MAX_VALUE; // Stream has unlimited events
    }

    @Override
    protected ByteOrder readByteOrder() throws EventReaderException {
        return ByteOrder.LITTLE_ENDIAN; // Default byte order
    }

    @Override
    protected Object readEvent(int eventNumber) throws EventReaderException {
        if (!reader.isConnected()) {
            throw new EventReaderException("Not connected to source");
        }

        Event event = reader.getEvent();
        if (event == null) {
            throw new EventReaderException("Failed to get event");
        }

        return ByteBuffer.wrap(event.getData(), 0, event.getLength());
    }

    @Override
    protected EngineData createResponse(Object event) {
        ByteBuffer buffer = (ByteBuffer) event;

        EngineData data = new EngineData();
        data.setData(EngineDataType.BYTES, buffer);

        return data;
    }

    @Override
    protected Set<EngineDataType> getDataTypes() {
        return Set.of(EngineDataType.BYTES);
    }

    @Override
    public EngineData configure(EngineData input) {
        if (input.getMimeType().equals(EngineDataType.JSON.mimeType())) {
            String source = (String) input.getData();
            try {
                JSONObject config = new JSONObject(source);

                String host = config.optString(CONFIG_HOST, DEFAULT_HOST);
                int port = config.optInt(CONFIG_PORT, DEFAULT_PORT);
                int connectionTimeout = config.optInt(CONFIG_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT);
                int readTimeout = config.optInt(CONFIG_READ_TIMEOUT, DEFAULT_READ_TIMEOUT);
                int bufferSize = config.optInt(CONFIG_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);

                StreamParameters parameters = new StreamParameters(
                        host,
                        port,
                        connectionTimeout,
                        readTimeout);

                // Close existing reader if any
                closeReader();

                // Create new reader with updated parameters
                try {
                    SocketSource socketSource = new SocketSource(parameters, bufferSize);
                    socketSource.open();
                    reader = socketSource;

                    LOGGER.info("Configured source: " + host + ":" + port);
                    return ErsapUtil.buildOKResponse();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error configuring source", e);
                    return ErsapUtil.buildErrorResponse(e.getMessage());
                }
            } catch (JSONException e) {
                LOGGER.log(Level.SEVERE, "Invalid configuration", e);
                return ErsapUtil.buildErrorResponse("Invalid configuration: " + e.getMessage());
            }
        }

        return super.configure(input);
    }

    /**
     * Gets the ring buffer status.
     * 
     * @return the ring buffer status as a JSON string
     */
    public String getRingBufferStatus() {
        if (reader instanceof SocketSource) {
            SocketSource socketSource = (SocketSource) reader;
            RingBufferMonitor monitor = socketSource.getRingBufferMonitor();

            if (monitor != null) {
                JSONObject status = new JSONObject();
                try {
                    status.put("bufferSize", socketSource.getBufferSize());
                    status.put("usedSlots", monitor.getUsedSlots());
                    status.put("availableSlots", monitor.getAvailableSlots());
                    status.put("fillLevelPercentage", monitor.getFillLevelPercentage());
                    status.put("consumerLag", monitor.getConsumerLag());
                    status.put("totalEventsPublished", monitor.getTotalEventsPublished());
                    status.put("totalEventsConsumed", monitor.getTotalEventsConsumed());
                    status.put("totalBytesPublished", monitor.getTotalBytesPublished());
                    status.put("publishThroughputEventsPerSecond", monitor.getPublishThroughput(TimeUnit.SECONDS));
                    status.put("publishThroughputMBPerSecond",
                            monitor.getBytesThroughput(TimeUnit.SECONDS) / (1024 * 1024));
                    status.put("host", socketSource.getParameters().getHost());
                    status.put("port", socketSource.getParameters().getPort());
                    status.put("connected", socketSource.isConnected());

                    return status.toString(2); // Pretty print with 2-space indentation
                } catch (JSONException e) {
                    return "Error creating JSON status: " + e.getMessage();
                }
            }

            return "Ring buffer monitor not available";
        }

        return "Reader is not a SocketSource";
    }

    /**
     * Gets the ring buffer status as a formatted string.
     * 
     * @return the ring buffer status as a formatted string
     */
    public String getRingBufferStatusString() {
        if (reader instanceof SocketSource) {
            SocketSource socketSource = (SocketSource) reader;
            return socketSource.getRingBufferStatus();
        }

        return "Reader is not a SocketSource";
    }
}