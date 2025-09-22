#!/bin/bash

get_influxdb_token() {
    #<=== Update path
    local INFLUXDB_CONFIG=/w/epsci-sciwork18/xmei/projects/SRO-RTDP/farm-tests/influxdb-config/influx-configs
    output=$(grep -E '^\s*[^#]*token\s*=' ${INFLUXDB_CONFIG})
    token=$(echo $output | awk -F '"' '{print $2}')
    echo ${token}
}
get_influxdb_token