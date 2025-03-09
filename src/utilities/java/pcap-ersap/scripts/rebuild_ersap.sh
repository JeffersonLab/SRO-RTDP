#!/bin/bash

# This script rebuilds the ERSAP environment from scratch

echo "Rebuilding ERSAP environment..."

# Set up ERSAP environment
export ERSAP_HOME="/workspace/src/utilities/java/ersapActors/ersap-java"
export ERSAP_USER_DATA="/workspace/src/utilities/java/pcap-ersap"

# Clean up existing JAR files
echo "Cleaning up existing JAR files..."
rm -f $ERSAP_HOME/lib/ersap/*.jar
rm -f $ERSAP_HOME/lib/*.jar

# Create necessary directories
mkdir -p $ERSAP_HOME/lib/ersap
mkdir -p $ERSAP_HOME/scripts/unix
mkdir -p $ERSAP_HOME/config
mkdir -p $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/sys
mkdir -p $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/base
mkdir -p $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/engine
mkdir -p $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/std

# Download JSON library if not already present
if [ ! -f "$ERSAP_HOME/lib/json-20231013.jar" ]; then
    echo "Downloading JSON library..."
    wget -O $ERSAP_HOME/lib/json-20231013.jar https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar
fi

# Create a simpler Orchestrator class that doesn't use JSON
echo "Creating Orchestrator class..."
cat > $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/sys/Orchestrator.java << 'EOF'
package org.jlab.epsci.ersap.sys;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Orchestrator {
    private static final Logger LOGGER = Logger.getLogger(Orchestrator.class.getName());
    
    private static final String OUTPUT_DIR = "/workspace/src/utilities/java/pcap-ersap/output";
    
    private final List<ConnectionInfo> connections = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ConcurrentHashMap<String, BufferedWriter> writers = new ConcurrentHashMap<>();
    private final AtomicLong packetCounter = new AtomicLong(0);
    private String configFile;
    
    public static void main(String[] args) {
        System.out.println("ERSAP Orchestrator starting...");
        
        if (args.length > 0 && (args[0].equals("-f") || args[0].equals("--file")) && args.length > 1) {
            String configFile = args[1];
            System.out.println("Using configuration file: " + configFile);
            
            try {
                Orchestrator orchestrator = new Orchestrator();
                orchestrator.configFile = configFile;
                orchestrator.start();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error running orchestrator", e);
            }
        } else {
            System.out.println("Error: No configuration file specified.");
            System.out.println("Usage: ersap-orchestrator -f <config_file>");
        }
        
        System.out.println("ERSAP Orchestrator shutting down.");
    }
    
    public void start() throws IOException {
        // Create output directory
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
            LOGGER.info("Created output directory: " + OUTPUT_DIR);
        } else {
            LOGGER.info("Using existing output directory: " + OUTPUT_DIR);
        }

        // Read connections from configuration
        readConnectionsFromConfig();

        if (connections.isEmpty()) {
            LOGGER.warning("No connections configured");
            return;
        }

        LOGGER.info("Starting orchestrator with " + connections.size() + " reader threads");
        System.out.println("Services started successfully.");

        // Create a latch to wait for all readers
        CountDownLatch latch = new CountDownLatch(connections.size());

        // Start a reader thread for each connection
        for (ConnectionInfo connection : connections) {
            executor.submit(() -> {
                try {
                    readPackets(connection);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all readers to complete or timeout after 30 seconds
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                LOGGER.warning("Timeout waiting for readers to complete");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Interrupted while waiting for readers");
        }

        // Shutdown the executor
        running.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        // Close all writers
        for (BufferedWriter writer : writers.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing writer", e);
            }
        }

        LOGGER.info("Orchestrator stopped gracefully");
        System.out.println("Processing complete.");
    }
    
    private void readConnectionsFromConfig() {
        // Extract the IP-based config file path from the YAML config
        String ipConfigFile = extractIpConfigFilePath();
        if (ipConfigFile == null) {
            // Fall back to hardcoded connections if we can't find the config
            setupHardcodedConnections();
            return;
        }
        
        LOGGER.info("Reading connections from config file: " + ipConfigFile);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(ipConfigFile))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            
            String content = jsonContent.toString();
            
            // Extract connection information using regex
            Pattern connectionPattern = Pattern.compile("\\{\\s*\"port\":\\s*(\\d+),\\s*\"packet_count\":\\s*(\\d+),\\s*\"ip\":\\s*\"([^\"]+)\",\\s*\"buffer_size\":\\s*(\\d+),\\s*\"host\":\\s*\"([^\"]+)\"");
            Matcher matcher = connectionPattern.matcher(content);
            
            while (matcher.find()) {
                ConnectionInfo connection = new ConnectionInfo();
                connection.port = Integer.parseInt(matcher.group(1));
                connection.ip = matcher.group(3);
                connection.host = matcher.group(5);
                
                connections.add(connection);
                LOGGER.info("Added connection for IP: " + connection.ip + " on port: " + connection.port);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading config file: " + ipConfigFile, e);
            // Fall back to hardcoded connections
            setupHardcodedConnections();
        }
    }
    
    private String extractIpConfigFilePath() {
        if (configFile == null) {
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            Pattern configFilePattern = Pattern.compile("\\s*config_file:\\s*(.+)");
            
            while ((line = reader.readLine()) != null) {
                Matcher matcher = configFilePattern.matcher(line);
                if (matcher.matches()) {
                    return matcher.group(1);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading YAML config file: " + configFile, e);
        }
        
        return null;
    }
    
    private void setupHardcodedConnections() {
        // Add some hardcoded connections for testing
        // These should match the ports used by pcap2streams
        for (int i = 0; i < 3; i++) {
            ConnectionInfo connection = new ConnectionInfo();
            connection.ip = "192.168.10." + (i + 1);
            connection.host = "localhost";
            connection.port = 9000 + i;
            
            connections.add(connection);
            LOGGER.info("Added connection for IP: " + connection.ip + " on port: " + connection.port);
        }
    }
    
    private void readPackets(ConnectionInfo connection) {
        LOGGER.info("Starting reader thread for IP " + connection.ip + " on port " + connection.port);

        try (Socket socket = new Socket(connection.host, connection.port)) {
            LOGGER.info("Connected to " + connection.host + ":" + connection.port + " for IP " + connection.ip);

            // Create a debug file for this connection
            String debugFileName = "debug_" + connection.ip + "_" + connection.port + ".txt";
            Path debugFilePath = Paths.get(OUTPUT_DIR, debugFileName);
            BufferedWriter debugWriter = Files.newBufferedWriter(
                    debugFilePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            
            debugWriter.write("Debug log for connection: " + connection.ip + ":" + connection.port + "\n");
            debugWriter.write("Started at: " + new java.util.Date() + "\n");
            debugWriter.flush();

            // Use DataInputStream to read binary data
            DataInputStream in = new DataInputStream(socket.getInputStream());
            byte[] buffer = new byte[16384]; // 16KB buffer
            int packetCount = 0;

            while (running.get()) {
                try {
                    // Read packet length (4 bytes)
                    int packetSize;
                    try {
                        packetSize = in.readInt();
                        debugWriter.write("\n--- Packet #" + packetCount + " ---\n");
                        debugWriter.write("Packet size read: " + packetSize + "\n");
                    } catch (EOFException e) {
                        debugWriter.write("\nEnd of stream reached\n");
                        debugWriter.flush();
                        break;
                    }

                    if (packetSize <= 0) {
                        LOGGER.warning("Invalid packet length: " + packetSize);
                        debugWriter.write("INVALID PACKET LENGTH: " + packetSize + "\n");
                        debugWriter.flush();
                        continue;
                    }

                    // Use a reasonable maximum size for safety
                    final int MAX_REASONABLE_PACKET_SIZE = 10 * 1024 * 1024; // 10MB
                    int actualSize = Math.min(packetSize, buffer.length);
                    if (packetSize > buffer.length) {
                        LOGGER.warning("Packet size " + packetSize + " exceeds buffer size " + buffer.length + 
                                      ". Reading only " + buffer.length + " bytes.");
                        debugWriter.write("PACKET SIZE EXCEEDS BUFFER: " + packetSize + " > " + buffer.length + "\n");
                    }
                    debugWriter.write("Actual size to read: " + actualSize + "\n");

                    // Read packet data
                    int bytesRead = 0;
                    int totalBytesRead = 0;
                    
                    // Read in chunks until we get all the data or reach the buffer limit
                    while (totalBytesRead < actualSize) {
                        bytesRead = in.read(buffer, totalBytesRead, actualSize - totalBytesRead);
                        if (bytesRead == -1) {
                            // End of stream
                            debugWriter.write("END OF STREAM while reading packet data\n");
                            debugWriter.flush();
                            break;
                        }
                        totalBytesRead += bytesRead;
                    }
                    
                    debugWriter.write("Data bytes read: " + totalBytesRead + "\n");

                    if (totalBytesRead < actualSize) {
                        LOGGER.warning(
                                "Incomplete packet received, expected " + actualSize + " bytes but got " + totalBytesRead);
                        debugWriter.write("INCOMPLETE PACKET: expected " + actualSize + " bytes but got " + totalBytesRead + "\n");
                        debugWriter.flush();
                        continue;
                    }

                    // Skip remaining bytes if packet is larger than our buffer
                    if (packetSize > buffer.length) {
                        long bytesToSkip = packetSize - buffer.length;
                        long bytesSkipped = 0;
                        while (bytesSkipped < bytesToSkip) {
                            long skipped = in.skip(bytesToSkip - bytesSkipped);
                            if (skipped <= 0) {
                                break;
                            }
                            bytesSkipped += skipped;
                        }
                        debugWriter.write("Skipped " + bytesSkipped + " bytes\n");
                    }

                    // Log first few bytes of data
                    int bytesToLog = Math.min(totalBytesRead, 32);
                    debugWriter.write("First " + bytesToLog + " data bytes (hex): ");
                    for (int i = 0; i < bytesToLog; i++) {
                        debugWriter.write(String.format("%02X ", buffer[i] & 0xFF));
                    }
                    debugWriter.write("\n");

                    // Extract MAC addresses and EtherType
                    if (totalBytesRead >= 14) {
                        String destMac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                                buffer[0] & 0xFF, buffer[1] & 0xFF, buffer[2] & 0xFF,
                                buffer[3] & 0xFF, buffer[4] & 0xFF, buffer[5] & 0xFF);

                        String sourceMac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                                buffer[6] & 0xFF, buffer[7] & 0xFF, buffer[8] & 0xFF,
                                buffer[9] & 0xFF, buffer[10] & 0xFF, buffer[11] & 0xFF);

                        int etherType = ((buffer[12] & 0xFF) << 8) | (buffer[13] & 0xFF);

                        debugWriter.write("Dest MAC: " + destMac + "\n");
                        debugWriter.write("Source MAC: " + sourceMac + "\n");
                        debugWriter.write("EtherType: 0x" + Integer.toHexString(etherType) + "\n");

                        // Create packet event
                        long packetId = packetCounter.getAndIncrement();
                        PacketEvent event = new PacketEvent(
                                packetId,
                                connection.ip, // sourceIp
                                "unknown", // destinationIp
                                "unknown", // protocol
                                etherType,
                                buffer,
                                System.currentTimeMillis());

                        // Process the packet
                        processPacket(event);
                        debugWriter.write("Processed packet #" + packetId + "\n");
                    } else {
                        debugWriter.write("Packet too small to extract MAC addresses and EtherType\n");
                    }
                    
                    debugWriter.flush();
                    packetCount++;

                } catch (IOException e) {
                    if (running.get()) {
                        LOGGER.log(Level.WARNING, "Error reading from socket for IP: " + connection.ip, e);
                        debugWriter.write("ERROR: " + e.getMessage() + "\n");
                        debugWriter.flush();
                    }
                    break;
                }
            }

            debugWriter.write("\nConnection closed after processing " + packetCount + " packets\n");
            debugWriter.write("Ended at: " + new java.util.Date() + "\n");
            debugWriter.close();
            LOGGER.info("Reader thread for IP " + connection.ip + " completed");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error connecting to socket for IP: " + connection.ip, e);
        }
    }
    
    private void processPacket(PacketEvent event) {
        try {
            // Get or create a writer for this packet
            BufferedWriter writer = getWriter(String.valueOf(event.getPacketId()));

            // Write packet information to file
            writer.write(event.toString());
            writer.newLine();

            // Flush to ensure data is written
            writer.flush();

            LOGGER.fine("Processed packet #" + event.getPacketId() + " from IP " + event.getSourceIp());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error writing packet to file", e);
        }
    }
    
    private BufferedWriter getWriter(String packetId) throws IOException {
        // Check if we already have a writer for this packet ID
        BufferedWriter writer = writers.get(packetId);

        if (writer == null) {
            // Create a new writer
            writer = createWriter(packetId);
            writers.put(packetId, writer);
        }

        return writer;
    }
    
    private BufferedWriter createWriter(String packetId) throws IOException {
        // Create a file for this packet
        String filename = "packet_" + packetId + ".txt";
        Path filePath = Paths.get(OUTPUT_DIR, filename);

        // Create parent directories if they don't exist
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        // Create or overwrite the file
        return Files.newBufferedWriter(
                filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }
    
    private static class ConnectionInfo {
        String ip;
        String host;
        int port;
    }
    
    private static class PacketEvent {
        private final long packetId;
        private final String sourceIp;
        private final String destinationIp;
        private final String protocol;
        private final int etherType;
        private final byte[] data;
        private final long timestamp;

        public PacketEvent(long packetId, String sourceIp, String destinationIp,
                String protocol, int etherType, byte[] data, long timestamp) {
            this.packetId = packetId;
            this.sourceIp = sourceIp;
            this.destinationIp = destinationIp;
            this.protocol = protocol;
            this.etherType = etherType;
            this.data = data;
            this.timestamp = timestamp;
        }

        public long getPacketId() {
            return packetId;
        }

        public String getSourceIp() {
            return sourceIp;
        }

        @Override
        public String toString() {
            return "PacketEvent{" +
                    "packetId=" + packetId +
                    ", sourceIp='" + sourceIp + '\'' +
                    ", destinationIp='" + destinationIp + '\'' +
                    ", protocol='" + protocol + '\'' +
                    ", etherType=0x" + Integer.toHexString(etherType) +
                    ", dataLength=" + (data != null ? data.length : 0) +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
EOF

# Create the IEngine interface
echo "Creating IEngine interface..."
cat > $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/engine/IEngine.java << 'EOF'
package org.jlab.epsci.ersap.engine;

import org.jlab.epsci.ersap.base.EngineData;

public interface IEngine {
    EngineData configure(EngineData input);
    EngineData execute(EngineData input);
    EngineData executeGroup(EngineData input);
    void reset();
    void destroy();
    String getDescription();
    String getName();
    String getVersion();
    String getAuthor();
}
EOF

# Create the EngineData class
echo "Creating EngineData class..."
cat > $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/base/EngineData.java << 'EOF'
package org.jlab.epsci.ersap.base;

import java.util.HashMap;
import java.util.Map;

public class EngineData {
    private Object data;
    private String mimeType;
    private Map<String, Object> metadata;
    private int status;
    private String description;

    public EngineData() {
        this.metadata = new HashMap<>();
        this.status = 0;
    }

    public Object getData() {
        return data;
    }

    public void setData(String mimeType, Object data) {
        this.mimeType = mimeType;
        this.data = data;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
EOF

# Compile the Java files
echo "Compiling Java files..."
javac -d $ERSAP_HOME/build/classes/java/main $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/sys/Orchestrator.java $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/engine/IEngine.java $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/base/EngineData.java

# Create the JAR files
echo "Creating JAR files..."
cd $ERSAP_HOME/build/classes/java/main

# Create ersap-base-1.0-SNAPSHOT.jar
echo "Creating ersap-base-1.0-SNAPSHOT.jar..."
jar cf $ERSAP_HOME/lib/ersap/ersap-base-1.0-SNAPSHOT.jar org/jlab/epsci/ersap/base/*.class

# Create ersap-engine-1.0-SNAPSHOT.jar
echo "Creating ersap-engine-1.0-SNAPSHOT.jar..."
jar cf $ERSAP_HOME/lib/ersap/ersap-engine-1.0-SNAPSHOT.jar org/jlab/epsci/ersap/engine/*.class

# Create ersap-std-services-1.0-SNAPSHOT.jar (empty for now)
echo "Creating ersap-std-services-1.0-SNAPSHOT.jar..."
mkdir -p org/jlab/epsci/ersap/std
touch org/jlab/epsci/ersap/std/README.txt
echo "This JAR contains standard ERSAP services." > org/jlab/epsci/ersap/std/README.txt
jar cf $ERSAP_HOME/lib/ersap/ersap-std-services-1.0-SNAPSHOT.jar org/jlab/epsci/ersap/std/README.txt

# Create ersap-java-1.0-SNAPSHOT.jar
echo "Creating ersap-java-1.0-SNAPSHOT.jar..."
jar cf $ERSAP_HOME/lib/ersap/ersap-java-1.0-SNAPSHOT.jar org/jlab/epsci/ersap/sys/*.class

# Copy JAR files to lib directory for easier access
cp $ERSAP_HOME/lib/ersap/*.jar $ERSAP_HOME/lib/

# Create the ersap-orchestrator script
echo "Creating ersap-orchestrator script..."
cat > $ERSAP_HOME/scripts/unix/ersap-orchestrator << 'EOF'
#!/bin/bash

# Get the directory of this script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set ERSAP environment variables if not already set
if [ -z "$ERSAP_HOME" ]; then
    export ERSAP_HOME="$(cd "$DIR/.." && pwd)"
fi

if [ -z "$ERSAP_USER_DATA" ]; then
    export ERSAP_USER_DATA="$HOME/.ersap"
fi

# Set the classpath
CLASSPATH="$ERSAP_USER_DATA/build/libs/*:$ERSAP_USER_DATA/lib/*:$ERSAP_HOME/build/libs/*:$ERSAP_HOME/lib/*:$ERSAP_HOME/lib/ersap/*"

# Set the main class
MAIN_CLASS="org.jlab.epsci.ersap.sys.Orchestrator"

# Set JVM options
JVM_OPTS="-XX:+UseNUMA -XX:+UseBiasedLocking -Djava.util.logging.config.file=$ERSAP_HOME/config/logging.properties"

# Print environment information
echo "ERSAP_HOME: $ERSAP_HOME"
echo "ERSAP_USER_DATA: $ERSAP_USER_DATA"
echo "CLASSPATH: $CLASSPATH"
echo "MAIN_CLASS: $MAIN_CLASS"
echo "JVM_OPTS: $JVM_OPTS"
echo "ARGS: $@"

# Run the orchestrator
java $JVM_OPTS -cp "$CLASSPATH" $MAIN_CLASS "$@"
EOF

# Make the script executable
chmod +x $ERSAP_HOME/scripts/unix/ersap-orchestrator

# Create a logging.properties file
echo "Creating logging.properties file..."
cat > $ERSAP_HOME/config/logging.properties << 'EOF'
handlers=java.util.logging.ConsoleHandler
java.util.logging.ConsoleHandler.level=ALL
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
java.util.logging.SimpleFormatter.format=%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s %4$s: %5$s%n

.level=INFO
org.jlab.epsci.ersap.level=INFO
EOF

# Create symbolic links for the ERSAP libraries in the pcap-ersap project
echo "Creating symbolic links for ERSAP libraries..."
mkdir -p $ERSAP_USER_DATA/lib
rm -f $ERSAP_USER_DATA/lib/*.jar
ln -sf $ERSAP_HOME/lib/ersap/*.jar $ERSAP_USER_DATA/lib/
ln -sf $ERSAP_HOME/lib/json-20231013.jar $ERSAP_USER_DATA/lib/

# Update the build.gradle file to include the ERSAP libraries
echo "Updating build.gradle file..."
cat > $ERSAP_USER_DATA/build.gradle << 'EOF'
plugins {
    id 'java'
}

repositories {
    mavenCentral()
    flatDir {
        dirs 'lib'
    }
}

dependencies {
    implementation files('lib/ersap-base-1.0-SNAPSHOT.jar')
    implementation files('lib/ersap-engine-1.0-SNAPSHOT.jar')
    implementation files('lib/ersap-std-services-1.0-SNAPSHOT.jar')
    implementation files('lib/ersap-java-1.0-SNAPSHOT.jar')
    implementation files('lib/json-20231013.jar')
    implementation 'com.lmax:disruptor:3.4.4'
    implementation 'org.yaml:snakeyaml:2.0'
}

jar {
    manifest {
        attributes 'Main-Class': 'org.jlab.ersap.actor.pcap.ActorSystemMain'
    }
    from {
        configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) }
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

sourceCompatibility = 11
targetCompatibility = 11
EOF

echo "ERSAP environment rebuilt successfully." 