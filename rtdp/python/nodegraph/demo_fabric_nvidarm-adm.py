import time
import json
from typing import Set, List, Dict
from fabric import Connection

# Define the remote host and other details
HOST_LIST = ['daosfs08.jlab.org']
USER = 'xmei'
UID = '11066'
KEYFILE = '/home/xmei/.ssh/id_ed25519'  # absolute path only

TARGET_IMGAE_IDS = {
    # hostname : image_id_set
    'daosfs08.jlab.org': {'8d91d8e4ec20'}   # first 12 digits of the image sha256
}

LOCAL_PATH_LIST = ['fetch_cont_stats_rhel.sh', 'get_cont_ids_rhel.sh']
REMOTE_PATH_LIST = ['/tmp/fetch_cont_stats.sh', '/tmp/get_cont_ids.sh']

def delete_remote_files(target_hosts: List[str], remote_path_list: List[str]):
    for h in target_hosts:
        conn = Connection(host=h, user=USER, connect_kwargs={'key_filename': KEYFILE})

        for _file in (remote_path_list):
            # Delete file
            result = conn.run(f'rm {_file}', warn=True)  # 'warn=True' to ignore non-zero exit codes

        conn.close()

    print("\n\nCleaned up the uploaded files!\n")


def upload_files(target_hosts: List[str], local_path_list: List[str], remote_path_list: List[str]):
    for h in target_hosts:
        conn = Connection(host=h, user=USER, connect_kwargs={'key_filename': KEYFILE})

        for i in range(len(local_path_list)):
            # Upload file
            conn.put(local_path_list[i], remote_path_list[i])

            print(f"File [{local_path_list[i]}] uploaded successfully to {h} : {remote_path_list[i]}")

        conn.close()

    print("Upload finished.\n")


def get_cont_ids_by_host(raw_input: str, image_id_set: Set[str]) -> List[str]:
    """
    A pure data ETL script to filter out the needed image from the raw output.
    The "ImageID" field is a SHA256 string and unique. Use "ImageID" to identify our target image ids.

    Args:
        - raw_input: the stdout of the command.
        - image_id_set: the SHA256 of all the needed images.

    Returns:
        - A list of 12-digit container ids. Eg: ['', ]
    """
    res = []
    json_list = json.loads(raw_input)
    for item in json_list:
        """
        {
            'Id': '3a48dcef87fa5fb0ef6a53baae82ff98e76d76a171c3a8b6ce6e4fb5904af2e4',
            'Names': ['/determined_fermi'],
            'Image': 'docker.io/gurjyan/ejfat-et:v0.1',
            'ImageID': 'sha256:8d91d8e4ec20f35673952c1be3dd7c375a76eddef015db92318c9ca56548d9e5',
            'Command': '',
            'Created': 1719861828,
            'Ports': [],
            'Labels': {'org.opencontainers.image.ref.name': 'ubuntu', 'org.opencontainers.image.version': '22.04'},
            'State': 'running',
            'Status': 'Up 19 hours',
            'NetworkSettings': {'Networks': {'host': {
                                                    'IPAMConfig': None,
                                                    'Links': None,
                                                    'Aliases': None,
                                                    'NetworkID': 'host',
                                                    'EndpointID': '',
                                                    'Gateway': '',
                                                    'IPAddress': '',
                                                    'IPPrefixLen': 0,
                                                    'IPv6Gateway': '',
                                                    'GlobalIPv6Address': '',
                                                    'GlobalIPv6PrefixLen': 0,
                                                    'MacAddress': '','DriverOpts': None
                                    }}},
            'Mounts': [], 'Name': '', 'Config': None, 'NetworkingConfig': None,
            'Platform': None, 'AdjustCPUShares': False
        }
        """
        # print(item)    # print whole bunch
        # E.g. 'ImageID': 'sha256:8d91d8e4ec20f35673952c1be3dd7c375a76eddef015db92318c9ca56548d9e5'
        if item['ImageID'][7:19] not in image_id_set:
            continue

        res.append(
            item['Id'][:12],   # E.g. 'Id': '3a48dcef87fa5fb0ef6a53baae82ff98e76d76a171c3a8b6ce6e4fb5904af2e4'
            )
    # print(res)
    return res


def fetch_cont_ids_by_host(hostname: str):
    conn = Connection(host=hostname, user=USER, connect_kwargs={'key_filename': KEYFILE})

    # Make sure /tmp/get_cont_ids.sh exists!
    # 'warn=True' to ignore non-zero exit codes
    # 'hide=True' so it will not print out the results
    result = conn.run(f'sh /tmp/get_cont_ids.sh', warn=True, hide=True)

    conn.close()

    # Print the command output and errors
    if result.ok:
        # print(result)
        # print(result.stdout)
        return result.stdout.strip()
    else:
        print("Error output:")
        print(result.stderr.strip())


def get_cont_ids_all(target_hosts: List[str], target_image_ids: Dict[str, Set[str]]):
    """
    Returns:
        - Dict[hostname: cont_id_list]. Eg. {'daosfs08.jlab.org': ['3a48dcef87fa', 'b7ce4c5652cc']}
    """
    res = {}
    for h in target_hosts:
        res[h] = get_cont_ids_by_host(fetch_cont_ids_by_host(h), target_image_ids[h])
    return res


def fetch_cont_stats_by_host(hostname: str, cont_ids: List[str]):

    conn = Connection(host=hostname, user=USER, connect_kwargs={'key_filename': KEYFILE})

    for _id in cont_ids:
        # print(hostname, _id)
        # Make sure the file exists on the remote host!
        raw_res = conn.run(f'sh /tmp/fetch_cont_stats.sh {_id}', warn=True, hide=True)
        if raw_res.ok:
            """
            Result returned from RHEL system:
            {
                "read":"2024-07-02T12:22:47.517567987-04:00",
                "preread":"0001-01-01T00:00:00Z",  # No meaning
                "pids_stats":{"current":47},
                "blkio_stats":{
                    "io_service_bytes_recursive":[],
                    "io_serviced_recursive":null,
                    "io_queue_recursive":null,
                    "io_service_time_recursive":null,
                    "io_wait_time_recursive":null,
                    "io_merged_recursive":null,
                    "io_time_recursive":null,
                    "sectors_recursive":null
                    },
                "num_procs":0,
                "storage_stats":{},
                "cpu_stats":{
                    "cpu_usage":{"total_usage":0,"usage_in_kernelmode":0,"usage_in_usermode":0},
                    # the cumulative CPU time used by all CPUs on the host system, measured in nanoseconds
                    "system_cpu_usage":24256226138203,
                    "online_cpus":64,
                    "cpu":0,
                    "throttling_data":{"periods":0,"throttled_periods":0,"throttled_time":0}
                    },
                "precpu_stats":{  # looks like no meaning
                    "cpu_usage":{"total_usage":0,"usage_in_kernelmode":0,"usage_in_usermode":0},
                    "cpu":0,
                    "throttling_data":{"periods":0,"throttled_periods":0,"throttled_time":0}
                    },
                "memory_stats":{"usage":45731840,"limit":540118855680},
                "name":"unruffled_maxwell",
                "id":"b7ce4c5652ccf36a44489183c928952a1a598c1e7d07974e1763cfaa69aa7418",
                "networks":{
                    "network":{
                        "rx_bytes":0,
                        "rx_packets":0,
                        "rx_errors":0,
                        "rx_dropped":0,
                        "tx_bytes":0,
                        "tx_packets":0,
                        "tx_errors":0,
                        "tx_dropped":0
                        }
                    }
                }
            """
            print(raw_res.stdout.strip())
        else:
            print(f"[fetch_single_container_stats({_id})], Error output:")
            print(result.stderr.strip())

    conn.close()


def get_cont_stats_all(target_cont_ids: Dict[str, List[str]]):
    for h in target_cont_ids.keys():
        fetch_cont_stats_by_host(h, target_cont_ids[h])


# Run the task
if __name__ == "__main__":

    # Upload necessary files
    upload_files(target_hosts=HOST_LIST, local_path_list=LOCAL_PATH_LIST, remote_path_list=REMOTE_PATH_LIST)

    time.sleep(2)
    ####
    # NOTE & TODO: the contents sit in-between ####...#### are based on RHEL podman returns.
    #       Debian docker returns and to be explored.

    # Get all the container ids on all the hosts
    target_cont_ids = get_cont_ids_all(target_hosts=HOST_LIST, target_image_ids=TARGET_IMGAE_IDS)

    # Fetch the stats every x seconds
    i = 0
    while( i != 10):
        get_cont_stats_all(target_cont_ids)
        time.sleep(15)  # sleep for x seconds
        i += 1
        print()
    ####

    delete_remote_files(target_hosts=HOST_LIST, remote_path_list=REMOTE_PATH_LIST)
