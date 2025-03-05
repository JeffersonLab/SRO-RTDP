package org.jlab.ersap.actor.pcap.engine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.epsci.ersap.std.services.ServiceUtils;
import org.jlab.ersap.actor.pcap.source.IESource;
import org.jlab.ersap.actor.pcap.source.MultiSocketSource;
import org.jlab.ersap.actor.pcap.source.StreamParameters;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ERSAP engine that reads events from multiple socket sources.
 */
public class MultiSocketSourceEngine extends AbstractEventReaderService<MultiSocketSource> {

    private static final Logger LOGGER = Logger.getLogger(MultiSocketSourceEngine.class.getName());

    private static final String CONFIG_CONNECTIONS = "connections";
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

    /**
     * Default constructor.
     */
    public MultiSocketSourceEngine() {
        // Nothing to do
    }

    @Override
    protected MultiSocketSource createReader(Path file, JSONObject opts) throws EventReaderException {
        // For socket sources, we don't use the file parameter
        // Instead, we create an empty MultiSocketSource that will be configured later
        return new MultiSocketSource();
    }

    /**
     * Creates a reader for the given source.
     *
     * @param source the source string
     * @return a new reader
     * @throws EventReaderException if the reader could not be created
     */
    protected MultiSocketSource createReader(String source) throws EventReaderException {
        // For socket sources, we don't use the source parameter
        // Instead, we create an empty MultiSocketSource that will be configured later
        return new MultiSocketSource();
    }

    @Override
    protected void closeReader() {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing reader", e);
            }
        }
    }

    @Override
    protected int readEventCount() throws EventReaderException {
        // For streaming sources, we don't know the event count in advance
        return Integer.MAX_VALUE;
    }

    @Override
    protected ByteOrder readByteOrder() throws EventReaderException {
        if (reader != null) {
            return reader.getByteOrder();
        }
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    protected Object readEvent(int eventNumber) throws EventReaderException {
        if (reader == null || !reader.isOpen()) {
            throw new EventReaderException("Reader is not open");
        }
        
        try {
            byte[] event = reader.getNextEvent();
            if (event == null) {
                throw new EventReaderException("No event available");
            }
            
            return ByteBuffer.wrap(event);
        } catch (IOException e) {
            throw new EventReaderException("Error reading event", e);
        }
    }

    @Override
    protected EngineDataType getDataType() {
        return EngineDataType.BYTES;
    }

    @Override
    public EngineData configure(EngineData input) {
        if (input.getMimeType().equals(EngineDataType.JSON.mimeType())) {
            String source = (String) input.getData();
            JSONObject jsonObject = new JSONObject(source);
            
            if (!jsonObject.has("connections")) {
                return buildErrorResponse("Missing 'connections' array in configuration");
            }
            
            JSONArray connections = jsonObject.getJSONArray("connections");
            if (connections.length() == 0) {
                return buildErrorResponse("No connections specified in configuration");
            }
            
            List<StreamParameters> parametersList = new ArrayList<>();
            for (int i = 0; i < connections.length(); i++) {
                JSONObject conn = connections.getJSONObject(i);
                if (!conn.has("host") || !conn.has("port")) {
                    return buildErrorResponse("Connection " + i + " missing 'host' or 'port'");
                }
                
                String host = conn.getString("host");
                int port = conn.getInt("port");
                int connectionTimeout = conn.optInt("connectionTimeout", 5000);
                int readTimeout = conn.optInt("readTimeout", 5000);
                
                parametersList.add(new StreamParameters(host, port, connectionTimeout, readTimeout));
            }
            
            try {
                if (reader == null) {
                    reader = new MultiSocketSource();
                }
                
                reader.open(parametersList);
                return buildOKResponse();
            } catch (Exception e) {
                return buildErrorResponse("Failed to open connections: " + e.getMessage());
            }
        } else {
            return buildErrorResponse("Invalid mime-type: " + input.getMimeType());
        }
    }

    /**
     * Gets the ring buffer status as a JSON string.
     *
     * @return the ring buffer status
     */
    public String getRingBufferStatus() {
        if (reader != null) {
            return reader.getRingBufferStatus();
        }
        return "{}";
    }

    /**
     * Gets the ring buffer status as a formatted string.
     *
     * @return the ring buffer status
     */
    public String getRingBufferStatusString() {
        if (reader != null) {
            return reader.getRingBufferStatusString();
        }
        return "No reader available";
    }

    /**
     * Builds an OK response.
     *
     * @return the response
     */
    private EngineData buildOKResponse() {
        EngineData response = new EngineData();
        response.setData(EngineDataType.STRING.mimeType(), "success");
        response.setStatus(EngineStatus.INFO);
        return response;
    }

    /**
     * Builds an error response.
     *
     * @param message the error message
     * @return the response
     */
    private EngineData buildErrorResponse(String message) {
        EngineData response = new EngineData();
        ServiceUtils.setError(response, message);
        return response;
    }

    /**
     * Gets the reader.
     *
     * @return the reader
     */
    public MultiSocketSource getReader() {
        return reader;
    }
} 