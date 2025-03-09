#!/bin/bash

# Set up ERSAP environment
export ERSAP_HOME="/workspace/src/utilities/java/ersapActors/ersap-java"
export ERSAP_USER_DATA="/workspace/src/utilities/java/pcap-ersap"

# Create necessary directories
mkdir -p $ERSAP_HOME/scripts/unix
mkdir -p $ERSAP_HOME/config

# Create the ersap-orchestrator script
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
CLASSPATH="$ERSAP_USER_DATA/build/libs/*:$ERSAP_USER_DATA/lib/*:$ERSAP_HOME/build/libs/*:$ERSAP_HOME/lib/*:$ERSAP_HOME/lib/*"

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
cat > $ERSAP_HOME/config/logging.properties << 'EOF'
handlers=java.util.logging.ConsoleHandler
java.util.logging.ConsoleHandler.level=ALL
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
java.util.logging.SimpleFormatter.format=%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s %4$s: %5$s%n

.level=INFO
org.jlab.epsci.ersap.level=INFO
EOF

echo "ERSAP environment set up successfully." 