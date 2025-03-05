package scripts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.jlab.ersap.actor.pcap.engine.MultiSocketSourceEngine;
import org.jlab.ersap.actor.pcap.source.MultiSocketSource;
import org.jlab.epsci.ersap.std.services.EventReaderException;

/**
 * Test client for the MultiSocketSourceEngine.
 * This client connects to multiple socket servers and displays the received data.
 */
public class TestMultiSocketClient {
    
    private static final Logger LOGGER = Logger.getLogger(TestMultiSocketClient.class.getName());
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: TestMultiSocketClient <config-file>");
            System.exit(1);
        }
        
        String configFile = args[0];
        MultiSocketSourceEngine engine = null;
        
        try {
            // Read the configuration file
            String configJson = new String(Files.readAllBytes(Paths.get(configFile)));
            System.out.println("Using configuration: " + configJson);
            
            // Create and configure the engine
            engine = new MultiSocketSourceEngine();
            
            // Configure the engine with the JSON configuration
            EngineData input = new EngineData();
            input.setData(EngineDataType.JSON.mimeType(), configJson);
            
            EngineData response = engine.configure(input);
            
            if (response != null && response.getStatus() == EngineStatus.ERROR) {
                System.err.println("Failed to configure engine: " + response.getDescription());
                System.exit(1);
            }
            
            System.out.println("Engine configured successfully");
            
            // Start reading events
            long startTime = System.currentTimeMillis();
            long lastStatusTime = startTime;
            long eventCount = 0;
            
            while (true) {
                try {
                    // Request the next event
                    EngineData request = new EngineData();
                    request.setData(EngineDataType.STRING.mimeType(), "next");
                    
                    EngineData output = engine.execute(request);
                    
                    if (output != null && output.getStatus() != EngineStatus.ERROR) {
                        eventCount++;
                        
                        // Print status every second
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastStatusTime >= 1000) {
                            double elapsedSeconds = (currentTime - startTime) / 1000.0;
                            double eventsPerSecond = eventCount / elapsedSeconds;
                            
                            System.out.printf("Received %d events (%.2f events/s)\n", 
                                    eventCount, eventsPerSecond);
                            
                            // Print ring buffer status
                            System.out.println(engine.getRingBufferStatusString());
                            
                            lastStatusTime = currentTime;
                        }
                    } else if (output != null && output.getStatus() == EngineStatus.ERROR) {
                        // Check if we've reached the end of the stream
                        if ("End of file".equals(output.getDescription())) {
                            System.out.println("Reached end of stream");
                            break;
                        } else {
                            System.err.println("Error: " + output.getDescription());
                            Thread.sleep(100);
                        }
                    } else {
                        // No events available, sleep a bit
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error getting next event", e);
                    Thread.sleep(100);
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in test client", e);
            e.printStackTrace();
        } finally {
            // Ensure the engine is closed properly
            if (engine != null) {
                try {
                    engine.destroy();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error closing engine", e);
                }
            }
        }
    }
} 