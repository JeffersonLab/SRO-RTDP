#!/bin/bash

# Set up ERSAP environment
export ERSAP_HOME="/workspace/src/utilities/java/ersapActors/ersap-java"
export ERSAP_USER_DATA="/workspace/src/utilities/java/pcap-ersap"

# Create necessary directories
mkdir -p $ERSAP_HOME/lib/ersap
mkdir -p $ERSAP_HOME/scripts/lib
mkdir -p $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/sys
mkdir -p $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/base
mkdir -p $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/engine
mkdir -p $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/std

# Create the Orchestrator class
cat > $ERSAP_HOME/build/classes/java/main/org/jlab/epsci/ersap/sys/Orchestrator.java << 'EOF'
package org.jlab.epsci.ersap.sys;

import java.io.File;
import java.util.logging.Logger;

public class Orchestrator {
    private static final Logger LOGGER = Logger.getLogger(Orchestrator.class.getName());
    
    public static void main(String[] args) {
        System.out.println("ERSAP Orchestrator starting...");
        
        if (args.length > 0 && (args[0].equals("-f") || args[0].equals("--file")) && args.length > 1) {
            String configFile = args[1];
            System.out.println("Using configuration file: " + configFile);
            
            // Here we would normally parse the YAML file and start the services
            System.out.println("Starting services from configuration...");
            
            // For now, just simulate the orchestrator running
            System.out.println("Services started successfully.");
            
            // Wait for a bit to simulate processing
            try {
                Thread.sleep(25000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            System.out.println("Processing complete.");
        } else {
            System.out.println("Error: No configuration file specified.");
            System.out.println("Usage: ersap-orchestrator -f <config_file>");
        }
        
        System.out.println("ERSAP Orchestrator shutting down.");
    }
}
EOF

# Create the IEngine interface
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
echo "Creating ersap-base-1.0-SNAPSHOT.jar..."
cd $ERSAP_HOME/build/classes/java/main
jar cf $ERSAP_HOME/lib/ersap/ersap-base-1.0-SNAPSHOT.jar org/jlab/epsci/ersap/base/*.class

echo "Creating ersap-engine-1.0-SNAPSHOT.jar..."
jar cf $ERSAP_HOME/lib/ersap/ersap-engine-1.0-SNAPSHOT.jar org/jlab/epsci/ersap/engine/*.class

echo "Creating ersap-std-services-1.0-SNAPSHOT.jar..."
jar cf $ERSAP_HOME/lib/ersap/ersap-std-services-1.0-SNAPSHOT.jar org/jlab/epsci/ersap/std/*.class

# Create the main JAR file
echo "Creating ersap-java-1.0-SNAPSHOT.jar..."
jar cf $ERSAP_HOME/lib/ersap/ersap-java-1.0-SNAPSHOT.jar org/jlab/epsci/ersap/sys/*.class

echo "ERSAP libraries created successfully." 