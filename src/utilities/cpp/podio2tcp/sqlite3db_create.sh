#!/bin/bash

# Check if a database prefix and option are provided
if [ $# -lt 2 ]; then
    echo "Usage: $0 -s|-c <DB_PREFIX>"
    echo "Example: $0 -s <database_prefix>"
    exit 1
fi

OPTION=$1
DB_NAME_PREFIX=$2
TABLE_NAME="rate_logs"

# Validate the option
if [ "$OPTION" != "-s" ] && [ "$OPTION" != "-c" ]; then
    echo "Invalid option: $OPTION"
    echo "Use -s for the SERVER/receiver or -c for the CLIENT/sender."
    exit 1
fi

# Get the current UTC timestamp in milliseconds
TIMESTAMP=$(date +%s%3N)

# Set the DB name according to the sender/receiver.
if [ "$OPTION" == "-s" ]; then
    DB_NAME="${DB_NAME_PREFIX}_recv_${TIMESTAMP}_$(hostname)$.db"
elif [ "$OPTION" == "-c" ]; then
    DB_NAME="${DB_NAME_PREFIX}_send_${TIMESTAMP}_$(hostname).db"
fi

# Create the SQLite database with the appropriate schema based on the option.
if [ "$OPTION" == "-c" ]; then
    sqlite3 "$DB_NAME" <<EOF
CREATE TABLE $TABLE_NAME (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp_utc_ms INTEGER,
    pid STRING,
    rateHz_read_period REAL,
    rateHz_sent_period REAL,
    rateMbps_read_period REAL,
    rateMbps_sent_period REAL,
    rateHz_read_total REAL,
    rateHz_sent_total REAL,
    rateMbps_read_total REAL,
    rateMbps_sent_total REAL
);
EOF
    echo "Receiver/SERVER database created successfully: '$DB_NAME' with table '$TABLE_NAME'."

elif [ "$OPTION" == "-s" ]; then
    TABLE_NAME="rate_logs"
    sqlite3 "$DB_NAME" <<EOF
CREATE TABLE $TABLE_NAME (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp_utc_ms INTEGER,
    pid STRING,
    rateHz_recv_period REAL,
    rateMbps_recv_period REAL
);
EOF
    echo "Sender/CLIENT database created successfully: '$DB_NAME' with table '$TABLE_NAME'."
fi

# Check if the database was created successfully
if [ $? -ne 0 ]; then
    echo "Failed to create the database or table."
    exit 1
fi
