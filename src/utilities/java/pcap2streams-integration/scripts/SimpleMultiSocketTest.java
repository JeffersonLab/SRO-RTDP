package scripts;

import org.jlab.ersap.actor.pcap.source.MultiSocketSource;
import org.jlab.ersap.actor.pcap.source.StreamParameters;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple test client for the MultiSocketSource class.
 * This client connects to multiple socket servers and displays the received data.
 */
public class SimpleMultiSocketTest {
    private static final Logger LOGGER = Logger.getLogger(SimpleMultiSocketTest.class.getName());
    private static final int MAX_RETRIES = 10;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int DATA_CHECK_TIMEOUT_MS = 5000;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: SimpleMultiSocketTest <config_file> [timeout_seconds]");
            System.exit(1);
        }

        String configFile = args[0];
        int timeoutSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        MultiSocketSource source = null;
        try {
            // Read configuration
            System.out.println("Reading configuration from: " + configFile);
            String jsonContent = new String(Files.readAllBytes(Paths.get(configFile)));
            System.out.println("Raw JSON content: " + jsonContent);
            
            JSONObject config = new JSONObject(jsonContent);
            System.out.println("Parsed JSON: " + config.toString());
            
            JSONArray connections = config.getJSONArray("connections");
            System.out.println("Using configuration: " + config.toString(4));
            System.out.println();

            // Create and configure source
            source = new MultiSocketSource();
            List<StreamParameters> paramsList = new ArrayList<>();
            
            for (int i = 0; i < connections.length(); i++) {
                JSONObject conn = connections.getJSONObject(i);
                String host = conn.getString("host");
                int port = conn.getInt("port");
                int connectionTimeout = conn.optInt("connection_timeout", 5000);
                int readTimeout = conn.optInt("read_timeout", 30000);
                int bufferSize = conn.optInt("buffer_size", 1024);

                paramsList.add(new StreamParameters(host, port, connectionTimeout, readTimeout));
                System.out.println("Added connection: " + host + ":" + port);
            }

            // Open connections
            System.out.println("Opening connections...");
            source.open(paramsList);

            // Wait for connections to be established with retries
            System.out.println("Waiting for connections to be established...");
            boolean connected = false;
            int retryCount = 0;
            
            while (!connected && retryCount < MAX_RETRIES) {
                int numConnected = source.getConnectedSourceCount();
                if (numConnected > 0) {
                    connected = true;
                    System.out.println("Successfully connected to " + numConnected + " out of " + 
                                      connections.length() + " sources");
                    
                    // Now check if we can actually receive data
                    System.out.println("Checking for data reception...");
                    long startTime = System.currentTimeMillis();
                    boolean dataReceived = false;
                    
                    while (System.currentTimeMillis() - startTime < DATA_CHECK_TIMEOUT_MS) {
                        byte[] data = source.getNextEvent();
                        if (data != null && data.length > 0) {
                            System.out.println("Data received! First packet size: " + data.length + " bytes");
                            dataReceived = true;
                            break;
                        }
                        Thread.sleep(100);
                    }
                    
                    if (!dataReceived) {
                        System.out.println("No data received within timeout period");
                        connected = false;
                    }
                } else {
                    retryCount++;
                    System.out.println("Connected to " + numConnected + " out of " + 
                                      connections.length() + " sources (retry " + retryCount + "/" + MAX_RETRIES + ")");
                    Thread.sleep(RETRY_DELAY_MS);
                }
            }

            if (!connected) {
                System.out.println("No connections established after " + MAX_RETRIES + " retries. Exiting.");
                return;
            }

            // Process data for the specified timeout
            System.out.println("Processing data for " + timeoutSeconds + " seconds...");
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (timeoutSeconds * 1000L);
            int eventCount = 0;
            
            while (System.currentTimeMillis() < endTime) {
                byte[] data = source.getNextEvent();
                if (data != null) {
                    eventCount++;
                    if (eventCount % 1000 == 0) {
                        System.out.println("Processed " + eventCount + " events");
                    }
                } else {
                    // Small sleep to avoid busy waiting
                    Thread.sleep(10);
                }
            }
            
            System.out.println("Test completed. Processed " + eventCount + " events in " + 
                              timeoutSeconds + " seconds");
            
            // Print final status
            System.out.println("\nFinal connection status:");
            System.out.println(source.getRingBufferStatusString());

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in test", e);
        } finally {
            if (source != null) {
                try {
                    source.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error closing source", e);
                }
            }
        }
    }
} 