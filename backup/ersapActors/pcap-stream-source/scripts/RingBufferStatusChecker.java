import org.jlab.epsci.ersap.base.Core;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * A simple command-line tool to check the ring buffer status of a running PCAP
 * Stream Source.
 */
public class RingBufferStatusChecker {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out
                    .println("Usage: java RingBufferStatusChecker <container_name> <service_name> <interval_seconds>");
            System.out.println("Example: java RingBufferStatusChecker pcap-container pcap-source 5");
            System.exit(1);
        }

        String containerName = args[0];
        String serviceName = args[1];
        int intervalSeconds = Integer.parseInt(args[2]);

        System.out.println("Checking ring buffer status for service: " + containerName + ":" + serviceName);
        System.out.println("Update interval: " + intervalSeconds + " seconds");

        // Create ERSAP core
        Core core = new Core();

        // Create a custom request to get the ring buffer status
        EngineData request = new EngineData();
        request.setData(EngineDataType.STRING, "getRingBufferStatus");

        // Service address
        String serviceAddress = containerName + ":" + serviceName;

        // Continuously check the status
        while (true) {
            try {
                // Send the request
                EngineData response = core.syncSend(serviceAddress, request);

                // Check if the response is valid
                if (response != null && response.getData() != null) {
                    String status = (String) response.getData();

                    // Try to parse as JSON for pretty printing
                    try {
                        JSONObject jsonStatus = new JSONObject(status);
                        System.out.println("\n--- Ring Buffer Status at " +
                                java.time.LocalDateTime.now() + " ---");
                        System.out.println("Buffer Size: " + jsonStatus.getInt("bufferSize"));
                        System.out.println("Used Slots: " + jsonStatus.getLong("usedSlots") +
                                " (" + jsonStatus.getDouble("fillLevelPercentage") + "%)");
                        System.out.println("Available Slots: " + jsonStatus.getLong("availableSlots"));
                        System.out.println("Consumer Lag: " + jsonStatus.getLong("consumerLag"));
                        System.out.println("Total Events Published: " + jsonStatus.getLong("totalEventsPublished"));
                        System.out.println("Total Events Consumed: " + jsonStatus.getLong("totalEventsConsumed"));
                        System.out.println("Total Bytes Published: " + jsonStatus.getLong("totalBytesPublished") +
                                " (" + (jsonStatus.getLong("totalBytesPublished") / (1024 * 1024)) + " MB)");
                        System.out.println("Throughput: " +
                                String.format("%.2f", jsonStatus.getDouble("publishThroughputEventsPerSecond")) +
                                " events/s (" +
                                String.format("%.2f", jsonStatus.getDouble("publishThroughputMBPerSecond")) +
                                " MB/s)");
                        System.out.println("Connection: " +
                                jsonStatus.getString("host") + ":" + jsonStatus.getInt("port") +
                                " (" + (jsonStatus.getBoolean("connected") ? "Connected" : "Disconnected") + ")");
                    } catch (Exception e) {
                        // Not JSON or error parsing, just print the raw status
                        System.out.println("\n--- Ring Buffer Status at " +
                                java.time.LocalDateTime.now() + " ---");
                        System.out.println(status);
                    }
                } else {
                    System.out.println("No response received from service");
                }
            } catch (Exception e) {
                System.err.println("Error checking ring buffer status: " + e.getMessage());
            }

            // Wait for the next check
            TimeUnit.SECONDS.sleep(intervalSeconds);
        }
    }
}