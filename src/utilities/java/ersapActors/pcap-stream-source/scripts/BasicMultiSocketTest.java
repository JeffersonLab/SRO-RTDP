package scripts;

import org.jlab.ersap.actor.pcap.source.MultiSocketSource;
import org.jlab.ersap.actor.pcap.source.StreamParameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple test client for the MultiSocketSource class.
 * This doesn't use JSON parsing to avoid any issues with the JSON library.
 */
public class BasicMultiSocketTest {
    private static final Logger LOGGER = Logger.getLogger(BasicMultiSocketTest.class.getName());
    private static final int MAX_RETRIES = 20;
    private static final int RETRY_DELAY_MS = 500;
    private static final int DATA_CHECK_TIMEOUT_MS = 10000;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: BasicMultiSocketTest <timeout_seconds>");
            System.exit(1);
        }

        int timeoutSeconds = Integer.parseInt(args[0]);
        
        // Hardcoded connection parameters
        String host1 = "localhost";
        int port1 = 9000;
        String host2 = "localhost";
        int port2 = 9001;
        int connectionTimeout = 5000;
        int readTimeout = 30000;
        int bufferSize = 1024;

        MultiSocketSource source = null;
        try {
            // Create and configure source
            source = new MultiSocketSource();
            List<StreamParameters> paramsList = new ArrayList<>();
            
            // Add connections
            paramsList.add(new StreamParameters(host1, port1, connectionTimeout, readTimeout));
            System.out.println("Added connection: " + host1 + ":" + port1);
            
            paramsList.add(new StreamParameters(host2, port2, connectionTimeout, readTimeout));
            System.out.println("Added connection: " + host2 + ":" + port2);

            // Open connections
            System.out.println("Opening connections...");
            source.open(paramsList);
            
            // Wait a bit for connections to be established
            System.out.println("Waiting for initial connection setup...");
            Thread.sleep(2000);

            // Wait for connections to be established with retries
            System.out.println("Checking connection status...");
            boolean connected = false;
            int retryCount = 0;
            
            while (!connected && retryCount < MAX_RETRIES) {
                int numConnected = source.getConnectedSourceCount();
                System.out.println("Connected sources: " + numConnected);
                
                if (numConnected > 0) {
                    connected = true;
                    System.out.println("Successfully connected to " + numConnected + " out of " + 
                                      paramsList.size() + " sources");
                    
                    // Print ring buffer status
                    System.out.println("Ring buffer status:");
                    System.out.println(source.getRingBufferStatusString());
                    
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
                        
                        // Check if we're still connected
                        if (source.getConnectedSourceCount() == 0) {
                            System.out.println("Lost all connections while waiting for data");
                            connected = false;
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
                                      paramsList.size() + " sources (retry " + retryCount + "/" + MAX_RETRIES + ")");
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
                    if (eventCount % 100 == 0) {
                        System.out.println("Processed " + eventCount + " events");
                        // Print connection status
                        System.out.println("Connected sources: " + source.getConnectedSourceCount());
                    }
                } else {
                    // Check if we're still connected
                    if (source.getConnectedSourceCount() == 0) {
                        System.out.println("Lost all connections, attempting to reconnect...");
                        source.close();
                        Thread.sleep(1000);
                        source.open(paramsList);
                        Thread.sleep(1000);
                        if (source.getConnectedSourceCount() == 0) {
                            System.out.println("Failed to reconnect. Exiting.");
                            break;
                        }
                    }
                    
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
            e.printStackTrace();
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