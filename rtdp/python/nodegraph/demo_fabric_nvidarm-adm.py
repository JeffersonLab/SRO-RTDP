import time
from fabric import Connection

# Define the remote host and other details
hostname = 'daosfs08.jlab.org'
username = 'xmei'
private_key = '/home/xmei/.ssh/id_ed25519'  # absolute path only

CONT_ID = 'b24776a86238'  # first 12 digits of the full container id
UID = '11066'

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


def get_all_cont_ids():
    conn = Connection(host=hostname, user=username, connect_kwargs={'key_filename': private_key})

    # 'hide=True' so it will not print out the results
    result = conn.run(f'sh {REMOTE_FILE_PATH_CONT_IDS}', warn=True, hide=True)

    conn.close()

    # Print the command output and errors
    if result.ok:
        return result.stdout.strip()
    else:
        print("Error output:")
        print(result.stderr.strip())


def fetch_container_stats(c, remote_file_path, container_id):
    # Run the script remotely
    result = c.run(f'sh {remote_file_path} {container_id}', warn=True)  # 'warn=True' to ignore non-zero exit codes

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

    get_all_cont_ids()

    conn = Connection(hostname, user=username, connect_kwargs={'key_filename': private_key})
    print(f"Connected to {hostname}")

    # Forwarding the container stats
    for _ in range(10):
        fetch_container_stats(conn, REMOTE_FILE_PATH_CONT_STATS, CONT_ID)
        time.sleep(10)

    # Delete remote files
    delete_remote_file(REMOTE_FILE_PATH_CONT_IDS)
    delete_remote_file(REMOTE_FILE_PATH_CONT_STATS)

    conn.close()
