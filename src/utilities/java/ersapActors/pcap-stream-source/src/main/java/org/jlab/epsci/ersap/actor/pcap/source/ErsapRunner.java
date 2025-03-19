package org.jlab.epsci.ersap.actor.pcap.source;

import org.jlab.epsci.ersap.base.ErsapLang;
import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ErsapRunner {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: ErsapRunner <services.yaml> <dataset.txt>");
            System.exit(1);
        }

        String servicesFile = args[0];
        String datasetFile = args[1];

        try {
            // Start the MockPcapServer
            startMockServer();

            // Parse configuration
            Map<String, Object> config = parseConfig(servicesFile);

            // Start DPE
            Process dpe = startDPE();

            // Wait for DPE to start
            TimeUnit.SECONDS.sleep(2);

            // Start services
            startServices(config);

            // Process dataset
            processDataset(datasetFile);

            // Wait for completion
            TimeUnit.SECONDS.sleep(5);

            // Cleanup
            dpe.destroy();

        } catch (Exception e) {
            System.err.println("Error running ERSAP workflow: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void startMockServer() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "java",
            "-cp",
            System.getProperty("java.class.path"),
            "org.jlab.epsci.ersap.actor.pcap.source.MockPcapServer",
            "/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap",
            "9000"
        );
        pb.inheritIO();
        pb.start();
    }

    private static Process startDPE() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "java",
            "-cp",
            System.getProperty("java.class.path"),
            "org.jlab.epsci.ersap.sys.Dpe",
            "--host", "localhost",
            "--port", String.valueOf(ErsapConstants.JAVA_PORT)
        );
        pb.inheritIO();
        return pb.start();
    }

    private static Map<String, Object> parseConfig(String configFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(configFile)) {
            return new Yaml().load(fis);
        }
    }

    private static void startServices(Map<String, Object> config) throws Exception {
        // Get service configurations
        Map<String, Object> ioServices = (Map<String, Object>) config.get("io-services");
        Map<String, Object> services = (Map<String, Object>) config.get("services");

        // Start source service
        Map<String, Object> sourceConfig = (Map<String, Object>) ioServices.get("reader");
        startService(sourceConfig);

        // Start processor service
        Map<String, Object> processorConfig = (Map<String, Object>) services.get("processor");
        startService(processorConfig);

        // Start sink service
        Map<String, Object> sinkConfig = (Map<String, Object>) ioServices.get("writer");
        startService(sinkConfig);
    }

    private static void startService(Map<String, Object> config) throws Exception {
        String className = config.get("class").toString();
        String name = config.get("name").toString();
        
        ProcessBuilder pb = new ProcessBuilder(
            "java",
            "-cp",
            System.getProperty("java.class.path"),
            className,
            "--name", name
        );
        
        if (config.containsKey("config")) {
            Map<String, Object> serviceConfig = (Map<String, Object>) config.get("config");
            for (Map.Entry<String, Object> entry : serviceConfig.entrySet()) {
                pb.command().add("--" + entry.getKey());
                pb.command().add(entry.getValue().toString());
            }
        }
        
        pb.inheritIO();
        pb.start();
    }

    private static void processDataset(String datasetFile) throws IOException {
        // Start the orchestrator to process the dataset
        ProcessBuilder pb = new ProcessBuilder(
            "java",
            "-cp",
            System.getProperty("java.class.path"),
            "org.jlab.epsci.ersap.sys.Orchestrator",
            "--file", datasetFile
        );
        pb.inheritIO();
        pb.start();
    }
}