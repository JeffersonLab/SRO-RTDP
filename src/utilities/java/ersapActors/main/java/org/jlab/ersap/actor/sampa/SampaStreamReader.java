package org.jlab.ersap.actor.sampa;

import org.jlab.ersap.engine.EngineData;
import org.jlab.ersap.engine.EngineDataType;
import org.jlab.ersap.std.services.AbstractEventReaderService;
import org.jlab.ersap.std.services.EventReaderException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;

/**
 * ERSAP service that reads SAMPA data from a network stream.
 */
public class SampaStreamReader extends AbstractEventReaderService {

    private static final String PORT_KEY = "port";
    private static final String HOST_KEY = "host";
    private static final String MAX_BUFFER_SIZE_KEY = "max-buffer-size";

    private static final int DEFAULT_PORT = 5000;
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_MAX_BUFFER_SIZE = 65536;

    private ServerSocketChannel serverSocketChannel;
    private SocketChannel clientChannel;
    private ByteBuffer buffer;
    private int maxBufferSize;
    private boolean isConnected = false;
    private long eventCount = 0;

    @Override
    protected EngineData readEvent(EngineData input) throws EventReaderException {
        try {
            if (!isConnected) {
                acceptConnection();
            }

            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                // Connection closed by client
                closeConnection();
                return null;
            }

            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            buffer.clear();

            EngineData output = new EngineData();
            output.setData(SampaDataType.SAMPA_STREAM, data);
            output.setDescription("SAMPA data stream packet");
            output.setEventNumber(eventCount++);

            return output;
        } catch (IOException e) {
            throw new EventReaderException("Error reading from socket", e);
        }
    }

    @Override
    protected void openFile(String filename) throws EventReaderException {
        try {
            serverSocketChannel = ServerSocketChannel.open();
            String host = getHost();
            int port = getPort();
            
            serverSocketChannel.socket().bind(new InetSocketAddress(host, port));
            serverSocketChannel.configureBlocking(true);
            
            buffer = ByteBuffer.allocate(maxBufferSize);
            
            System.out.println("SampaStreamReader: Listening on " + host + ":" + port);
        } catch (IOException e) {
            throw new EventReaderException("Error opening server socket", e);
        }
    }

    private void acceptConnection() throws IOException {
        System.out.println("SampaStreamReader: Waiting for connection...");
        clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(true);
        isConnected = true;
        System.out.println("SampaStreamReader: Client connected from " + 
                clientChannel.getRemoteAddress());
    }

    private void closeConnection() throws IOException {
        if (clientChannel != null && clientChannel.isOpen()) {
            clientChannel.close();
        }
        isConnected = false;
        System.out.println("SampaStreamReader: Client disconnected");
    }

    @Override
    protected void closeFile() throws EventReaderException {
        try {
            closeConnection();
            if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
                serverSocketChannel.close();
            }
            System.out.println("SampaStreamReader: Server socket closed");
        } catch (IOException e) {
            throw new EventReaderException("Error closing server socket", e);
        }
    }

    @Override
    protected int readEventCount() throws EventReaderException {
        return -1; // Unknown number of events in a stream
    }

    @Override
    protected EngineDataType getDataType() {
        return SampaDataType.SAMPA_STREAM;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return Set.of(EngineDataType.JSON);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return Set.of(SampaDataType.SAMPA_STREAM);
    }

    @Override
    public void reset() {
        try {
            closeFile();
            openFile("");
        } catch (EventReaderException e) {
            System.err.println("Error resetting SampaStreamReader: " + e.getMessage());
        }
    }

    @Override
    public void configure(JSONObject config) {
        if (config.has(MAX_BUFFER_SIZE_KEY)) {
            maxBufferSize = config.getInt(MAX_BUFFER_SIZE_KEY);
        } else {
            maxBufferSize = DEFAULT_MAX_BUFFER_SIZE;
        }
        System.out.println("SampaStreamReader: Configured with max buffer size = " + maxBufferSize);
    }

    private int getPort() {
        if (getConfig().has(PORT_KEY)) {
            return getConfig().getInt(PORT_KEY);
        }
        return DEFAULT_PORT;
    }

    private String getHost() {
        if (getConfig().has(HOST_KEY)) {
            return getConfig().getString(HOST_KEY);
        }
        return DEFAULT_HOST;
    }
} 