#!/bin/bash

# This script fixes the package structure issues in the Java files

echo "Fixing package structure..."

# Create the correct directory structure
mkdir -p /workspace/src/utilities/java/pcap-ersap/src/main/java/org/jlab/ersap/actor/pcap
mkdir -p /workspace/src/utilities/java/pcap-ersap/src/main/java/org/jlab/ersap/actor/pcap/services
mkdir -p /workspace/src/utilities/java/pcap-ersap/src/main/java/org/jlab/ersap/actor/pcap/data

# Move files to the correct directories
find /workspace/src/utilities/java/pcap-ersap/src/main/java -name "*.java" -type f | while read file; do
    # Get the package declaration from the file
    package=$(grep -m 1 "^package" "$file" | sed 's/package \(.*\);/\1/')
    
    if [ -n "$package" ]; then
        # Create the target directory based on the package
        target_dir="/workspace/src/utilities/java/pcap-ersap/src/main/java/$(echo $package | tr '.' '/')"
        
        # Create the target directory if it doesn't exist
        mkdir -p "$target_dir"
        
        # Get the filename
        filename=$(basename "$file")
        
        # Move the file to the target directory
        if [ "$file" != "$target_dir/$filename" ]; then
            echo "Moving $file to $target_dir/$filename"
            cp "$file" "$target_dir/$filename"
        fi
    fi
done

# Create a symbolic link for the ERSAP libraries
mkdir -p /workspace/src/utilities/java/pcap-ersap/lib
ln -sf /workspace/src/utilities/java/ersapActors/ersap-java/lib/ersap/*.jar /workspace/src/utilities/java/pcap-ersap/lib/

# Update the build.gradle file to include the ERSAP libraries
cat > /workspace/src/utilities/java/pcap-ersap/build.gradle << 'EOF'
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
    implementation 'org.json:json:20231013'
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

echo "Package structure fixed successfully." 