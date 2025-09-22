#!/bin/bash

job_id=$1

while true; do
  job_info=$(scontrol show job $job_id)
  node_list=$(echo "$job_info" | grep -oP 'NodeList=\K\S+')

  if [[ -n "$node_list" ]]; then
    echo "Job is running on node(s): $node_list"
    break
  fi
done

