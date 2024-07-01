import time
import json
from typing import List
from fabric import Connection

# Define the remote host and other details
hostname = 'daosfs08.jlab.org'
username = 'xmei'
private_key = '/home/xmei/.ssh/id_ed25519'  # absolute path only

UID = '11066'

IMGAE_ID = '8d91d8e4ec20'  # first 12 digits of the image sha256

REMOTE_FILE_PATH_CONT_STATS = '/tmp/fetch_cont_stats.sh'
LOCAL_FILE_PATH_CONT_STATS = 'fetch_cont_stats_rhel.sh'

REMOTE_FILE_PATH_CONT_IDS = '/tmp/get_cont_ids.sh'
LOCAL_FILE_PATH_CONT_IDS = 'get_cont_ids_rhel.sh'


def delete_remote_file(remote_path):
    conn = Connection(host=hostname, user=username, connect_kwargs={'key_filename': private_key})

    result = conn.run(f'rm {remote_path}', warn=True)  # 'warn=True' to ignore non-zero exit codes

    conn.close()


def upload_file(local_path, remote_path):

    conn = Connection(host=hostname, user=username, connect_kwargs={'key_filename': private_key})

    # Upload file
    conn.put(local_path, remote_path)

    print(f"File uploaded successfully to {hostname} : {remote_path}")

    conn.close()


def get_cont_ids(raw_input: str, image_list=[IMGAE_ID]) -> List[str]:
    """

    Args:
        - raw_input: the stdout of the command. 
    """
    res = []
    json_list = json.loads(raw_input)
    for item in json_list:
        # E.g. 'ImageID': 'sha256:8d91d8e4ec20f35673952c1be3dd7c375a76eddef015db92318c9ca56548d9e5'
        if item['ImageID'][7:19] in image_list:
            res.append(
                item['Id'][:12],   # E.g. 'Id': '3a48dcef87fa5fb0ef6a53baae82ff98e76d76a171c3a8b6ce6e4fb5904af2e4'
                )
    # print(res)
    return res


def get_all_cont_ids_raw():
    conn = Connection(host=hostname, user=username, connect_kwargs={'key_filename': private_key})

    # 'warn=True' to ignore non-zero exit codes
    # 'hide=True' so it will not print out the results
    result = conn.run(f'sh {REMOTE_FILE_PATH_CONT_IDS}', warn=True, hide=True)

    conn.close()

    # Print the command output and errors
    if result.ok:
        # print(result)
        # print(result.stdout)
        return result.stdout.strip()
    else:
        print("Error output:")
        print(result.stderr.strip())


def fetch_single_container_stats(c, remote_file_path, container_id):
    # Run the script remotely
    result = c.run(f'sh {remote_file_path} {container_id}', warn=True)  
    # Print the command output and errors
    if result.ok:
        return result.stdout.strip()
    else:
        print("Error output:")
        print(result.stderr.strip())


# Run the task
if __name__ == "__main__":
    
    # Upload necessary files
    upload_file(LOCAL_FILE_PATH_CONT_IDS, REMOTE_FILE_PATH_CONT_IDS)
    upload_file(LOCAL_FILE_PATH_CONT_STATS, REMOTE_FILE_PATH_CONT_STATS)

    time.sleep(2)

    target_cont_ids = get_cont_ids(get_all_cont_ids_raw())

    conn = Connection(hostname, user=username, connect_kwargs={'key_filename': private_key})
    print(f"Connected to {hostname}")

    # # Forwarding the container stats
    # for _ in range(10):
    #     fetch_container_stats(conn, REMOTE_FILE_PATH_CONT_STATS, CONT_ID)
    #     time.sleep(10)

    # Delete remote files
    delete_remote_file(REMOTE_FILE_PATH_CONT_IDS)
    delete_remote_file(REMOTE_FILE_PATH_CONT_STATS)

    conn.close()
