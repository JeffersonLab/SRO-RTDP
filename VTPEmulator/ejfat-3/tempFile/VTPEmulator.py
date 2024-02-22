import subprocess
import sys
import argparse 
import yaml


def get_config():
    try:
        with open("VTPConfig.yaml", "r") as f:
            config = yaml.safe_load(f)
            host = config.get("host")
            machines = config.get("machines")
            return host, machines
    except FileNotFoundError:
        return None, None

def main():
	parser = argparse.ArgumentParser(description="Getting the username using -u form commandline")
	parser.add_argument("-u", "--username", help="Specify the username/hostname")
	parser.add_argument("-t", "--targetTime", help="Specify the targetTime in hh:mm format")
	args = parser.parse_args()

	# Set the target time
	TARGET_TIME = ""
	if args.targetTime:
		TARGET_TIME=args.targetTime
	else:
		raise Exception("Target time is not provided. Please use -t paramter to provide it in the format hh:mm")

	# Set the output log file
	OUTPUT_LOG = "output.log"

	# Set the error log file
	ERROR_LOG = "error.log"

	# Set the file containing the host and command mappings
	HOST_COMMAND_FILE = "VTPConfig.yaml"

	# Build the command with a timeout of 0
	command = ["/usr/bin/parallel-ssh", "-o", OUTPUT_LOG, "-e", ERROR_LOG, "-t", "0"]

	# Construct a list of hosts
	host, machines = get_config()
	host_list=""

	if machines:
	        host_list = "'" + " ".join(
	            args.username + "@" + machine["name"] if args.username is not None
	            else (machine["host"] + "@" + machine["name"]) if "host" in machine and not args.username
	            else (host + "@" + machine["name"]) if host and not args.username and not "host" in machine
	            else "Unknown"
		    for machine in machines
	        ) + "'"

	# Add host list to the command
	command.extend(["-H", ''.join(host_list)])

	# Construct the command list
	command_list=""
	if machines:
        	command_list = [f"'{machine.get('script_location', '')} {TARGET_TIME}'" for machine in machines]
        	print("Command List:", command_list)
	# Concatenate all commands
	command.extend(command_list)

	# Print the command to the console
	print("Running the following command:")
	print(" ".join(command))

	# Run the command
	subprocess.run(" ".join(command), shell=True)

if __name__ == "__main__":
    main()
