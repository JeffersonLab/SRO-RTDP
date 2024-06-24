#!/bin/bash
curl --silent --unix-socket /run/user/${UID}/podman/podman.sock  http://localhost/containers/${1}/stats?stream=false
