package org.jlab.ersap.pcap;

import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.epsci.ersap.std.services.ServiceUtils;
import org.jlab.ersap.actor.pcap2streams.IPBasedStreamClient;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.json.JSONObject;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.ByteOrder;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class PcapSource extends AbstractEventReaderService<IPBasedStreamClient> {
    
    private static final Logger LOGGER = Logger.getLogger(PcapSource.class.getName());
    
    private Level logLevel;
    private String socketsFile;
    private IPBasedStreamClient client;
    private List<SocketConfig> socketConfigs;
    private int currentSocketIndex;
    private int currentEvent;
    private Map<String, Integer> packetsReadPerSocket;  // Track packets read per socket
    private Map<String, Integer> expectedPacketsPerSocket;  // Track expected packets per socket
    
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    
    private boolean isEventCountRequest = false;
    
    private static class SocketConfig {
        final String ip;
        final String host;
        final int port;
        
        SocketConfig(String ip, String host, int port) {
            this.ip = ip;
            this.host = host;
            this.port = port;
        }
    }
    
    public PcapSource() {
        super();
        this.socketConfigs = new ArrayList<>();
        this.currentSocketIndex = 0;
        this.currentEvent = 0;
        this.socketsFile = "input/pcap_sockets.txt"; // Default value
        this.packetsReadPerSocket = new HashMap<>();
        this.expectedPacketsPerSocket = new HashMap<>();
        System.err.println("DEBUG: PcapSource constructor called");
    }
    
    @Override
    public EngineData configure(EngineData input) {
        System.err.println("DEBUG: configure method called with mime-type: " + input.getMimeType());
        String mimeType = input.getMimeType();
        
        // Default values
        socketsFile = "input/pcap_sockets.txt";
        logLevel = Level.ALL;
        
        if (mimeType.equalsIgnoreCase(JavaObjectType.JOBJ.mimeType())) {
            String source = (String) input.getData();
            System.err.println("DEBUG: Received JOBJ configuration: " + source);
            // Parse simple key=value format
            String[] parts = source.split("=");
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                
                if ("SOCKETS_FILE".equals(key)) {
                    socketsFile = value;
                    System.err.println("DEBUG: Set SOCKETS_FILE from JOBJ: " + value);
                } else if ("file".equals(key)) {
                    socketsFile = value;
                    System.err.println("DEBUG: Set SOCKETS_FILE from file parameter: " + value);
                } else if ("LOG_LEVEL".equals(key)) {
                    logLevel = Level.parse(value);
                    System.err.println("DEBUG: Set LOG_LEVEL from JOBJ: " + value);
                }
            }
        } else if (mimeType.equalsIgnoreCase(EngineDataType.JSON.mimeType())) {
            try {
                String jsonStr = (String) input.getData();
                System.err.println("DEBUG: Received JSON configuration: " + jsonStr);
                JSONObject data = new JSONObject(jsonStr);
                
                // Extract configuration from JSON
                if (data.has("file")) {
                    socketsFile = data.getString("file");
                    System.err.println("DEBUG: Set SOCKETS_FILE from file parameter: " + socketsFile);
                } else if (data.has("SOCKETS_FILE")) {
                    socketsFile = data.getString("SOCKETS_FILE");
                    System.err.println("DEBUG: Set SOCKETS_FILE from SOCKETS_FILE parameter: " + socketsFile);
                }
                
                if (data.has("LOG_LEVEL")) {
                    logLevel = Level.parse(data.getString("LOG_LEVEL"));
                    System.err.println("DEBUG: Set LOG_LEVEL from JSON: " + logLevel);
                }
            } catch (Exception e) {
                System.err.println("DEBUG: Failed to parse JSON configuration: " + e.getMessage());
                e.printStackTrace(System.err);
                System.err.println("DEBUG: Using default values after JSON parse error");
            }
        } else if (mimeType.equalsIgnoreCase("text/string")) {
            String source = (String) input.getData();
            System.err.println("DEBUG: Received text/string configuration: " + source);
            // Parse simple key=value format
            String[] parts = source.split("=");
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                
                if ("SOCKETS_FILE".equals(key)) {
                    socketsFile = value;
                    System.err.println("DEBUG: Set SOCKETS_FILE from text/string: " + value);
                } else if ("LOG_LEVEL".equals(key)) {
                    logLevel = Level.parse(value);
                    System.err.println("DEBUG: Set LOG_LEVEL from text/string: " + value);
                }
            }
        }
        
        // Validate configuration
        System.err.println("DEBUG: Validating configuration...");
        Path socketsPath = Paths.get(socketsFile);
        if (!socketsPath.isAbsolute()) {
            // Convert to absolute path relative to the pcap-actors directory
            socketsPath = Paths.get("/workspaces/ersap-actors/src/utilities/java/ersap-pcap/pcap-actors").resolve(socketsFile);
        }
        
        System.err.println("DEBUG: Final configuration:");
        System.err.println("DEBUG: - SOCKETS_FILE: " + socketsPath.toAbsolutePath());
        System.err.println("DEBUG: - LOG_LEVEL: " + logLevel);
        
        // Verify the sockets file exists and is readable
        if (!socketsPath.toFile().exists()) {
            String error = "Configuration error: Sockets file does not exist: " + socketsPath.toAbsolutePath();
            System.err.println("DEBUG: " + error);
            throw new RuntimeException(error);
        }
        
        if (!socketsPath.toFile().canRead()) {
            String error = "Configuration error: Cannot read sockets file: " + socketsPath.toAbsolutePath();
            System.err.println("DEBUG: " + error);
            throw new RuntimeException(error);
        }
        
        // Try to load socket configurations during configuration
        try {
            loadSocketConfigs(socketsPath);
            System.err.println("DEBUG: Successfully loaded " + socketConfigs.size() + " socket configurations during setup");
            
            // Initialize the client immediately
            if (client == null) {
                SocketConfig config = socketConfigs.get(currentSocketIndex);
                System.err.println("DEBUG: Initializing client for host: " + config.host + ", port: " + config.port);
                client = createClientWithRetry(config.host, config.port);
                System.err.println("DEBUG: Client initialized successfully");
            }
            
        } catch (IOException e) {
            String error = "Configuration error: Failed to load socket configurations: " + e.getMessage();
            System.err.println("DEBUG: " + error);
            e.printStackTrace(System.err);
            throw new RuntimeException(error);
        }
        
        return null;
    }
    
    private void verifyFileAccess(Path file) throws IOException {
        System.err.println("DEBUG: Verifying file access for: " + file.toAbsolutePath());
        
        // Check if file exists
        if (!file.toFile().exists()) {
            String error = "File does not exist: " + file.toAbsolutePath();
            System.err.println("DEBUG: " + error);
            throw new IOException(error);
        }
        
        // Check if it's a file (not a directory)
        if (!file.toFile().isFile()) {
            String error = "Path is not a file: " + file.toAbsolutePath();
            System.err.println("DEBUG: " + error);
            throw new IOException(error);
        }
        
        // Check if file is readable
        if (!file.toFile().canRead()) {
            String error = "File is not readable: " + file.toAbsolutePath();
            System.err.println("DEBUG: " + error);
            throw new IOException(error);
        }
        
        // Try to open the file for reading to verify access
        try (FileReader reader = new FileReader(file.toFile())) {
            System.err.println("DEBUG: Successfully opened file for reading");
        } catch (IOException e) {
            String error = "Failed to open file for reading: " + e.getMessage();
            System.err.println("DEBUG: " + error);
            throw e;
        }
        
        // Print file details
        System.err.println("DEBUG: File details:");
        System.err.println("DEBUG: - Absolute path: " + file.toAbsolutePath());
        System.err.println("DEBUG: - File size: " + file.toFile().length() + " bytes");
        System.err.println("DEBUG: - Last modified: " + file.toFile().lastModified());
        System.err.println("DEBUG: - Can read: " + file.toFile().canRead());
        System.err.println("DEBUG: - Can write: " + file.toFile().canWrite());
        System.err.println("DEBUG: - Can execute: " + file.toFile().canExecute());
    }
    
    private void loadSocketConfigs(Path file) throws IOException {
        System.err.println("DEBUG: Attempting to load socket configurations from: " + file.toAbsolutePath());
        
        // Verify file access first
        verifyFileAccess(file);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length == 3) {
                        try {
                            int port = Integer.parseInt(parts[2]);
                            if (port <= 0 || port > 65535) {
                                String error = "Invalid port number at line " + lineNumber + ": " + port;
                                System.err.println("DEBUG: " + error);
                                throw new IOException(error);
                            }
                            String socketKey = parts[0] + ":" + parts[1] + ":" + port;
                            socketConfigs.add(new SocketConfig(parts[0], parts[1], port));
                            packetsReadPerSocket.put(socketKey, 0);
                            System.err.println("DEBUG: Added socket config: IP=" + parts[0] + ", host=" + parts[1] + ", port=" + port);
                        } catch (NumberFormatException e) {
                            String error = "Invalid port number format at line " + lineNumber + ": " + parts[2];
                            System.err.println("DEBUG: " + error);
                            throw new IOException(error);
                        }
                    }
                }
            }
        }
        
        if (socketConfigs.isEmpty()) {
            String error = "No valid socket configurations found in file: " + file.toAbsolutePath();
            System.err.println("DEBUG: " + error);
            throw new IOException(error);
        }
        
        // Load expected packet counts from ip-based-config.json
        try {
            Path configPath = file.getParent().resolve("ip-based-config.json");
            if (configPath.toFile().exists()) {
                System.err.println("DEBUG: Loading packet counts from: " + configPath.toAbsolutePath());
                String configContent = new String(java.nio.file.Files.readAllBytes(configPath));
                JSONObject config = new JSONObject(configContent);
                
                for (SocketConfig socket : socketConfigs) {
                    String socketKey = socket.ip + ":" + socket.host + ":" + socket.port;
                    int expectedCount = config.getInt("packet_count");
                    expectedPacketsPerSocket.put(socketKey, expectedCount);
                    System.err.println("DEBUG: Set expected packet count for " + socketKey + " to " + expectedCount);
                }
            } else {
                System.err.println("DEBUG: No ip-based-config.json found, using default packet count");
                // Set a default high value to keep reading until no more packets
                for (SocketConfig socket : socketConfigs) {
                    String socketKey = socket.ip + ":" + socket.host + ":" + socket.port;
                    expectedPacketsPerSocket.put(socketKey, Integer.MAX_VALUE);
                }
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Error loading packet counts: " + e.getMessage());
            e.printStackTrace(System.err);
            // Set default values on error
            for (SocketConfig socket : socketConfigs) {
                String socketKey = socket.ip + ":" + socket.host + ":" + socket.port;
                expectedPacketsPerSocket.put(socketKey, Integer.MAX_VALUE);
            }
        }
        
        System.err.println("DEBUG: Successfully loaded " + socketConfigs.size() + " socket configurations");
    }
    
    private IPBasedStreamClient createClientWithRetry(String host, int port) throws IOException {
        System.err.println("DEBUG: Attempting to create client for host: " + host + ", port: " + port);
        IOException lastException = null;
        
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                IPBasedStreamClient newClient = new IPBasedStreamClient(host, port);
                System.err.println("DEBUG: Attempt " + (i + 1) + " of " + MAX_RETRIES + " to connect");
                newClient.connect();
                System.err.println("DEBUG: Successfully connected to " + host + ":" + port);
                return newClient;
            } catch (IOException e) {
                lastException = e;
                System.err.println("DEBUG: Connection attempt " + (i + 1) + " failed: " + e.getMessage());
                if (i < MAX_RETRIES - 1) {
                    try {
                        System.err.println("DEBUG: Waiting " + RETRY_DELAY_MS + "ms before retry...");
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Connection retry interrupted", ie);
                    }
                }
            }
        }
        
        throw new IOException("Failed to connect after " + MAX_RETRIES + " attempts. Last error: " + lastException.getMessage());
    }
    
    @Override
    protected int readEventCount() throws EventReaderException {
        System.err.println("DEBUG: readEventCount called - checking client state");
        System.err.println("DEBUG: Setting isEventCountRequest to true");
        isEventCountRequest = true;
        
        try {
            // First ensure we have socket configurations loaded
            if (socketConfigs.isEmpty()) {
                System.err.println("DEBUG: No socket configurations loaded, attempting to load");
                try {
                    Path socketsPath = Paths.get("/workspaces/ersap-actors/src/utilities/java/ersap-pcap/pcap-actors/input/pcap_sockets.txt");
                    verifyFileAccess(socketsPath);
                    loadSocketConfigs(socketsPath);
                } catch (IOException e) {
                    String error = "Failed to load socket configurations: " + e.getMessage();
                    System.err.println("DEBUG: " + error);
                    e.printStackTrace(System.err);
                    throw new EventReaderException(error, e);
                }
            }
            
            // Then ensure client is initialized
            if (client == null) {
                System.err.println("DEBUG: Client is null, attempting initialization");
                try {
                    // Create client with current socket configuration
                    SocketConfig config = socketConfigs.get(currentSocketIndex);
                    System.err.println("DEBUG: Creating client for host: " + config.host + ", port: " + config.port);
                    client = createClientWithRetry(config.host, config.port);
                    System.err.println("DEBUG: Client initialized and connected successfully");
                } catch (Exception e) {
                    String error = "Failed to initialize client: " + e.getMessage();
                    System.err.println("DEBUG: " + error);
                    e.printStackTrace(System.err);
                    throw new EventReaderException(error, e);
                }
            }
            
            // Verify client is connected
            if (!client.isConnected()) {
                System.err.println("DEBUG: Client exists but not connected, attempting to reconnect");
                try {
                    SocketConfig config = socketConfigs.get(currentSocketIndex);
                    client = createClientWithRetry(config.host, config.port);
                    System.err.println("DEBUG: Client reconnected successfully");
                } catch (Exception e) {
                    String error = "Failed to reconnect client: " + e.getMessage();
                    System.err.println("DEBUG: " + error);
                    e.printStackTrace(System.err);
                    throw new EventReaderException(error, e);
                }
            }
            
            // Create a new Integer object to ensure we're not returning a ByteBuffer
            Integer result = Integer.valueOf(Integer.MAX_VALUE);
            System.err.println("DEBUG: Client state verified, returning Integer value: " + result);
            return result;
        } finally {
            System.err.println("DEBUG: Setting isEventCountRequest to false in finally block");
            isEventCountRequest = false;
        }
    }
    
    @Override
    protected IPBasedStreamClient createReader(Path file, JSONObject opts) throws EventReaderException {
        System.err.println("DEBUG: createReader called with file: " + (file != null ? file.toAbsolutePath() : "null"));
        
        try {
            // Use absolute path for sockets file
            Path socketsPath = Paths.get("/workspaces/ersap-actors/src/utilities/java/ersap-pcap/pcap-actors/input/pcap_sockets.txt");
            System.err.println("DEBUG: Using absolute path for sockets file: " + socketsPath.toAbsolutePath());
            
            if (!socketsPath.toFile().exists()) {
                String error = "Sockets file does not exist: " + socketsPath.toAbsolutePath();
                System.err.println("DEBUG: " + error);
                throw new IOException(error);
            }
            
            if (!socketsPath.toFile().canRead()) {
                String error = "Cannot read sockets file: " + socketsPath.toAbsolutePath();
                System.err.println("DEBUG: " + error);
                throw new IOException(error);
            }
            
            System.err.println("DEBUG: File exists and is readable");
            
            // Load socket configurations if not already loaded
            if (socketConfigs.isEmpty()) {
                loadSocketConfigs(socketsPath);
            }
            
            // Create configuration for the current socket
            SocketConfig config = socketConfigs.get(currentSocketIndex);
            System.err.println("DEBUG: Attempting connection to host: " + config.host + ", port: " + config.port);
            
            // Create and connect client with retry logic
            client = createClientWithRetry(config.host, config.port);
            
            System.err.println("DEBUG: Successfully connected to stream");
            return client;
        } catch (IOException e) {
            String error = "Could not create reader: " + e.getMessage();
            System.err.println("DEBUG: " + error);
            e.printStackTrace(System.err);
            throw new EventReaderException(error, e);
        }
    }
    
    @Override
    protected void closeReader() {
        if (client != null) {
            client.disconnect();
            System.err.println("Disconnected from stream");
        } else {
            System.err.println("closeReader called but client is null");
        }
    }
    
    @Override
    protected ByteOrder readByteOrder() throws EventReaderException {
        System.err.println("readByteOrder called");
        return ByteOrder.BIG_ENDIAN;
    }
    
    @Override
    protected Object readEvent(int eventNumber) throws EventReaderException {
        System.err.println("DEBUG: readEvent called with eventNumber: " + eventNumber);
        
        // Keep trying until we get a packet or no more packets are available
        while (true) {
            // Try each socket until we get a packet or exhaust all sockets
            for (int i = 0; i < socketConfigs.size(); i++) {
                try {
                    SocketConfig config = socketConfigs.get(i);
                    String socketKey = config.ip + ":" + config.host + ":" + config.port;
                    
                    // If we don't have a client or it's not connected, create a new one
                    if (client == null || !client.isConnected()) {
                        System.err.println("DEBUG: Creating new client for host: " + config.host + ", port: " + config.port);
                        client = createClientWithRetry(config.host, config.port);
                        currentSocketIndex = i;
                    }
                    
                    System.err.println("DEBUG: Reading packet from socket " + (currentSocketIndex + 1) + " of " + socketConfigs.size());
                    byte[] packet = client.readPacket();
                    if (packet != null) {
                        System.err.println("DEBUG: Successfully read packet of size: " + packet.length);
                        packetsReadPerSocket.put(socketKey, packetsReadPerSocket.get(socketKey) + 1);
                        return packet;
                    }
                    
                    // If we got no packet, try the next socket
                    System.err.println("DEBUG: No packet available from socket " + (currentSocketIndex + 1) + ", trying next socket");
                    client.disconnect();
                    client = null;
                    continue;
                    
                } catch (IOException e) {
                    System.err.println("DEBUG: Error reading from socket " + (currentSocketIndex + 1) + ": " + e.getMessage());
                    e.printStackTrace(System.err);
                    // Try the next socket
                    if (client != null) {
                        client.disconnect();
                        client = null;
                    }
                    continue;
                }
            }
            
            // If we've tried all sockets and got no packet, wait a bit before trying again
            System.err.println("DEBUG: No packets available from any socket, waiting before retry");
            try {
                Thread.sleep(100); // Wait 100ms before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventReaderException("Interrupted while waiting for packets", e);
            }
        }
    }
    
    @Override
    protected EngineDataType getDataType() {
        System.err.println("DEBUG: getDataType called");
        System.err.println("DEBUG: Current isEventCountRequest flag: " + isEventCountRequest);
        // For event count requests, return SFIXED32
        if (isEventCountRequest) {
            System.err.println("DEBUG: Returning SFIXED32 for event count request");
            System.err.println("DEBUG: SFIXED32 mime-type: " + EngineDataType.SFIXED32.mimeType());
            return EngineDataType.SFIXED32;
        }
        // For normal packet data, return BYTES
        System.err.println("DEBUG: Returning BYTES for normal packet data");
        System.err.println("DEBUG: BYTES mime-type: " + EngineDataType.BYTES.mimeType());
        return EngineDataType.BYTES;
    }
    
    @Override
    public Set<EngineDataType> getInputDataTypes() {
        System.err.println("getInputDataTypes called");
        Set<EngineDataType> types = new HashSet<>();
        types.add(JavaObjectType.JOBJ);
        types.add(EngineDataType.JSON);
        types.add(EngineDataType.STRING);
        return types;
    }
    
    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        System.err.println("DEBUG: getOutputDataTypes called");
        Set<EngineDataType> types = new HashSet<>();
        types.add(EngineDataType.BYTES);
        types.add(EngineDataType.SFIXED32);  // Add SFIXED32 for event count
        System.err.println("DEBUG: Returning output data types: " + types);
        for (EngineDataType type : types) {
            System.err.println("DEBUG: - " + type.mimeType());
        }
        return types;
    }
    
    @Override
    public String getDescription() {
        return "Reads network packets from IP-based streams";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getAuthor() {
        return "ERSAP Team";
    }
    
    @Override
    public void destroy() {
        System.err.println("destroy called");
        closeReader();
        System.err.println("Destroyed PCAP source");
    }
    
    @Override
    public void reset() {
        System.err.println("reset called");
        closeReader();
        currentSocketIndex = 0;
        System.err.println("Reset PCAP source");
    }
    
    @Override
    public EngineData execute(EngineData input) {
        System.err.println("DEBUG: execute called with mime-type: " + input.getMimeType());
        System.err.println("DEBUG: Input data class: " + (input.getData() != null ? input.getData().getClass().getName() : "null"));
        EngineData output = new EngineData();

        String dt = input.getMimeType();
        if (dt.equalsIgnoreCase(EngineDataType.STRING.mimeType())) {
            String request = (String) input.getData();
            System.err.println("DEBUG: Received request: " + request);
            if (request.equals("next") || request.equals("next-rec")) {
                System.err.println("DEBUG: Handling next/next-rec request");
                getNextEvent(input, output);
            } else if (request.equals("order")) {
                System.err.println("DEBUG: Handling order request");
                getFileByteOrder(output);
            } else if (request.equals("count")) {
                System.err.println("DEBUG: Handling count request");
                getEventCount(output);
                System.err.println("DEBUG: After getEventCount:");
                System.err.println("DEBUG: - Output mime-type: " + output.getMimeType());
                System.err.println("DEBUG: - Output data class: " + (output.getData() != null ? output.getData().getClass().getName() : "null"));
                System.err.println("DEBUG: - Output description: " + output.getDescription());
            } else {
                System.err.println("DEBUG: Unknown request type: " + request);
                ServiceUtils.setError(output, String.format("Wrong input data = '%s'", request));
            }
        } else {
            System.err.println("DEBUG: Unknown mime-type: " + dt);
            String errorMsg = String.format("Wrong input type '%s'", dt);
            ServiceUtils.setError(output, errorMsg);
        }

        return output;
    }
    
    private void getEventCount(EngineData output) {
        System.err.println("DEBUG: getEventCount called");
        if (client == null) {
            System.err.println("DEBUG: No client available, attempting to create one");
            try {
                SocketConfig config = socketConfigs.get(currentSocketIndex);
                client = createClientWithRetry(config.host, config.port);
                System.err.println("DEBUG: Successfully created client");
            } catch (IOException e) {
                String error = "Failed to create client: " + e.getMessage();
                System.err.println("DEBUG: " + error);
                ServiceUtils.setError(output, error, 1);
                return;
            }
        }
        
        try {
            int count = readEventCount();
            System.err.println("DEBUG: Setting event count to: " + count);
            System.err.println("DEBUG: Setting data with mime-type: " + EngineDataType.SFIXED32.mimeType());
            // Create a new Integer object to ensure we're not returning a ByteBuffer
            Integer countObj = Integer.valueOf(count);
            System.err.println("DEBUG: Created Integer object with value: " + countObj);
            System.err.println("DEBUG: Integer object class: " + countObj.getClass().getName());
            output.setData(EngineDataType.SFIXED32.mimeType(), countObj);
            System.err.println("DEBUG: Output data type after setting: " + output.getMimeType());
            System.err.println("DEBUG: Output data class: " + (output.getData() != null ? output.getData().getClass().getName() : "null"));
            output.setDescription("event count");
        } catch (EventReaderException e) {
            String error = "Failed to get event count: " + e.getMessage();
            System.err.println("DEBUG: " + error);
            ServiceUtils.setError(output, error, 1);
        }
    }
    
    private void getNextEvent(EngineData input, EngineData output) {
        System.err.println("DEBUG: getNextEvent called");
        try {
            Object event = readEvent(currentEvent);
            if (event != null) {
                output.setData(getDataType().toString(), event);
                output.setDescription("data");
                currentEvent++;
            } else {
                ServiceUtils.setError(output, "End of file", 1);
            }
        } catch (EventReaderException e) {
            String error = "Error reading event: " + e.getMessage();
            System.err.println("DEBUG: " + error);
            ServiceUtils.setError(output, error, 1);
        }
    }
    
    private void getFileByteOrder(EngineData output) {
        System.err.println("DEBUG: getFileByteOrder called");
        try {
            ByteOrder order = readByteOrder();
            output.setData(EngineDataType.STRING.mimeType(), order.toString());
            output.setDescription("byte order");
        } catch (EventReaderException e) {
            String error = "Error getting byte order: " + e.getMessage();
            System.err.println("DEBUG: " + error);
            ServiceUtils.setError(output, error, 1);
        }
    }
} 