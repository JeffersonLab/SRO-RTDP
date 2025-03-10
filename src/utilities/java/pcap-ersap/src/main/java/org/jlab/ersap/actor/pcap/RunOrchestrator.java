package org.jlab.ersap.actor.pcap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A simple runner for the ERSAP Orchestrator.
 * This class provides a convenient way to start the ERSAP orchestrator
 * with the appropriate configuration.
 */
public class RunOrchestrator {

    private static final Logger LOGGER = Logger.getLogger(RunOrchestrator.class.getName());

    /**
     * Main method to run the ERSAP orchestrator.
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        try {
            // Set up paths
            String userDir = System.getProperty("user.dir");
            Path configDir = Paths.get(userDir, "config");
            Path outputDir = Paths.get(userDir, "output");

            // Create directories if they don't exist
            Files.createDirectories(configDir);
            Files.createDirectories(outputDir);

            // Copy services.yaml to config directory
            Path sourceYaml = Paths.get(userDir, "src", "main", "java", "org", "jlab", "ersap", "actor", "pcap",
                    "services.yaml");
            Path targetYaml = configDir.resolve("services.yaml");
            if (Files.exists(sourceYaml)) {
                Files.copy(sourceYaml, targetYaml, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Copied services.yaml to " + targetYaml);
            } else {
                System.err.println("Could not find services.yaml at " + sourceYaml);
                return;
            }

            // Set system properties for configuration
            // These can be used to override the default socket buffer size
            if (System.getProperty("socketBufferSize") == null) {
                System.setProperty("socketBufferSize", "11534336");
            }

            // Create and start the orchestrator
            System.out.println("Starting ERSAP orchestrator...");
            CustomOrchestrator orchestrator = new CustomOrchestrator();
            orchestrator.start();

            // Wait for the orchestrator to finish
            System.out.println("ERSAP orchestrator started. Press Ctrl+C to stop.");
            Thread.sleep(TimeUnit.MINUTES.toMillis(10)); // Run for 10 minutes

        } catch (Exception e) {
            System.err.println("Error running ERSAP orchestrator: " + e.getMessage());
            e.printStackTrace();
        }
    }
}