#!/bin/bash

# Check if a database prefix is provided
if [ -z "$1" ]; then
    echo "Usage: $0 <DB_PREFIX>"
    echo "Example: $0 my_database_prefix"
    exit 1
fi

# Define the database name and table name
DB_NAME_PREFIX=$1
TIMESTAMP=$(date +%s%3N)  # Get the current UTC timestamp in milliseconds
DB_NAME="${DB_NAME_PREFIX}_${TIMESTAMP}.db"
TABLE_NAME="rate_logs"

# # Remove the existing database if it exists (optional)
# if [ -f "$DB_NAME" ]; then
#     echo "Removing existing database: $DB_NAME"
#     rm "$DB_NAME"
# fi

# Create the database and define the table format
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

# Check if the database was created successfully
if [ $? -eq 0 ]; then
    echo "Database created successfully: '$DB_NAME' with table '$TABLE_NAME'."
else
    echo "Failed to create the database or table."
    exit 1
fi

export RTDP_PODIO_SENDER_DB_NAME=${DB_NAME}
