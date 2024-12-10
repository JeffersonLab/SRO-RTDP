#!/bin/bash

# InfluxDB line protocol: https://docs.influxdata.com/influxdb/v2/get-started/write/
# measurement,tag1=val1,tag2=val2 field1="v1",field2=1i 0000000000000000000
# measurement: Everything before the first unescaped comma before the first whitespace.
# tag set: Key-value pairs between the first unescaped comma and the first unescaped whitespace.
# field set: Key-value pairs between the first and second unescaped whitespaces.
# timestamp: Integer value after the second unescaped whitespace.
# Lines are separated by the newline character (\n). Line protocol is whitespace sensitive.

# InfluxDB schema
# - measurement: podio2tcp
# - tags
# hostname: ${HOSTNAME}
# pid: pid in SQL
# role: RECV
# - fields
# rateHz_period: rateHz_period in SQL (float)
# rateMbps_period: rateMbps_period in SQL (float)
# - timestamp: Unix timestamp in millisecond precision

# NOTE: for all the lines below double-quote or single-quote is a must-have and very SENSITIVE!!!

SQLITE_DBNAME=$1
INFLUXDB_PORT=$2

INFLUXDB_URL=http://129.57.70.25:${INFLUXDB_PORT}  # ifarm2401 address
INFLUXDB_MEASUREMENT_NAME=podio2tcp

# Activate InfluxDB env vars
SCRIPTS_PREFIX=${HOME}/projects/SRO-RTDP/farm-tests/slurm-podio2tcp-influxdb-demo
source ${SCRIPTS_PREFIX}/influxdb_setenv.bash

## Get the InfluxDB token
token=$(bash ${SCRIPTS_PREFIX}/influxdb_get_token.bash)
while [[ -z "$token" ]]; do
    echo "Token is null. Try again after 15 seconds..."
    sleep 15
    
    token=$(bash ${SCRIPTS_PREFIX}/influxdb_get_token.bash)
done
export INFLUXDB_TOKEN=$token
# echo $INFLUXDB_TOKEN

## Check if the InfluxDB url accessible
if ! curl -fsS "${INFLUXDB_URL}/health"; then
  echo "InfluxDB health check failed. Exiting."
  exit 1
fi

sleep 5

LAST_ID=0
# Helper function to transfer the sql res into influxdb line protocol lines.
### RUN on the receiver node to get the correct hostname.
transfer_recv_sql_records() {
    local sql_res="$1"
    # echo $sql_res
    local influxdb_data=""

    # Sample $sql_res: 517|1733772184881|629190|38.168|115.265 516|1733772183571|629190|38.959|117.653
    while IFS='|' read -r id timestamp_utc_ms pid rateHz_period rateMbps_period; do
        # NOTE: CASE and WHITESPACE sensitive!!!
        # InfluxDB line protocol: measurement, tags
        influxdb_data+="${INFLUXDB_MEASUREMENT_NAME},hostname=${HOSTNAME},pid=${pid},role=RECV "
        # InfluxDB line protocol: fields
        influxdb_data+="rateHz_period=${rateHz_period},rateMbps_period=${rateMbps_period} "
        # InfluxDB line protocol: timestamp in ms.
        # DONOT use "${timestamp_utc_ms}\n" since it will add "\\n" instead of '\n' and cause bugs.
        influxdb_data+="${timestamp_utc_ms}"$'\n'
    done <<< "$sql_res"
    
    echo "${influxdb_data}"
}

## Main loop
set -e # Exit up on fail
while true; do
    SQL_CMD="SELECT * FROM rate_logs WHERE id > $LAST_ID ORDER BY id ASC;"
    sql_query_res=$(sqlite3 ${SQLITE_DBNAME} "${SQL_CMD}")
    # echo ${sql_query_res}
    # Check if the result is non-empty
    if [[ -n "$sql_query_res" ]]; then
        NEW_LINES=$(echo "$sql_query_res" | wc -l)
        LAST_ID=$((${NEW_LINES} + $LAST_ID))
        echo "Read sqlite up to record id=$(($LAST_ID - 1))"
    else
        echo "No new records found, record id=$LAST_ID."
        sleep 20
        continue
    fi

    influxdb_data=$(transfer_recv_sql_records "$sql_query_res")

    ## Query the DB to verify:
    # curl --request POST "$INFLUXDB_URL/query?org=$INFLUXDB_ORG&bucket=${INFLUXDB_BUCKET}" \
    #     --header "Authorization: Token $INFLUXDB_TOKEN" \
    #     --data-urlencode "rp=autogen" --data-urlencode "db=bucket_podio2tcp" \
    #     --data-urlencode "q=SELECT * FROM podio2tcp WHERE time >= '2024-12-01T08:00:00Z'"
    curl -X POST "${INFLUXDB_URL}/api/v2/write?org=${INFLUXDB_ORG}&bucket=${INFLUXDB_BUCKET}&precision=ms" \
        --header "Authorization: Token ${INFLUXDB_TOKEN}" \
        --header "Content-Type: text/plain; charset=utf-8" \
        --header "Accept: application/json" \
        --data-binary "${influxdb_data}"

    sleep 15
done
