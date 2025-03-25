#!/bin/bash

# Exit on error
set -e

echo "Installing ERSAP-JAVA..."

# Set ERSAP_HOME
export ERSAP_HOME=$HOME/ersap-install

# Create ersap-install directory
mkdir -p $ERSAP_HOME/lib

# Clone repositories if they don't exist
mkdir -p ERSAP
cd ERSAP

if [ ! -d "ersap-java" ]; then
    git clone --recurse-submodules --depth 1 -b upgradeGradle https://github.com/JeffersonLab/ersap-java.git
fi

if [ ! -d "ersap-actor" ]; then
    git clone --depth 1 https://github.com/JeffersonLab/ersap-actor.git
fi

# Build and install ersap-java
echo "Building ersap-java..."
cd ersap-java
ERSAP_HOME=$ERSAP_HOME ./gradlew deploy
ERSAP_HOME=$ERSAP_HOME ./gradlew publishToMavenLocal
cd ..

# Build and install ersap-actor
echo "Building ersap-actor..."
cd ersap-actor
ERSAP_HOME=$ERSAP_HOME ./gradlew deploy
ERSAP_HOME=$ERSAP_HOME ./gradlew publishToMavenLocal

# Copy the built jar to ERSAP_HOME/lib
echo "Installing ersap-actor..."
cp build/libs/ersap-actor-1.0-SNAPSHOT.jar $ERSAP_HOME/lib/
cd .. 