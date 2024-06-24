import time
from fabric import Connection

# Define the remote host and other details
hostname = 'daosfs08.jlab.org'
username = 'xmei'
private_key = '/home/xmei/.ssh/id_ed25519'
user_id = '11066'
container_id = '4583af5e1f74'

remote_file_path = '/tmp/fetch_cont_stats.sh'

def upload_file(remote_path):

    local_path = 'fetch_cont_stats_rhel.sh'

    conn = Connection(host=hostname, user=username, connect_kwargs={'key_filename': private_key})

    # Upload file
    conn.put(local_path, remote_path)

    print(f"File uploaded successfully to {remote_path}")

    conn.close()


def fetch_container_stats(c):
    # Run the command remotely
    result = c.run(f'sh {remote_file_path} {container_id}', warn=True)  # 'warn=True' to ignore non-zero exit codes

    # Print the command output and errors
    if result.ok:
        return result.stdout.strip()
    else:
        print("Error output:")
        print(result.stderr.strip())


def execute_command():
    # Establish SSH connection
    with Connection(hostname, user=username, connect_kwargs={'key_filename': private_key}) as conn:
        print(f"Connected to {hostname}")

        # Execute the task
        fetch_container_stats(conn)

# Run the task
if __name__ == "__main__":
    
    upload_file(remote_file_path)
    while True:
        res = execute_command()
        time.sleep(10)

