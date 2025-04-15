# GPU Proxy Cylc Workflow

This workflow orchestrates a GPU proxy experiment with three main components:
1. Receiver: Listens for incoming data
2. GPU Proxy: Processes data using GPU
3. Sender: Sends processed data to the receiver

## Prerequisites

- Access to a SLURM cluster with GPU nodes
- Apptainer installed on the cluster
- Cylc 8 installed and configured
- The `gpu-proxy.sif` container image built and available

## Running the Experiment

1. **Start the Workflow**:
   ```bash
   cylc play gpu-proxy
   ```

2. **Monitor the Workflow**:
   ```bash
   # View workflow status
   cylc tui gpu-proxy
   
   # View task logs
   cylc cat-log gpu-proxy <task_name>  # e.g., cylc cat-log gpu-proxy receiver
   ```

3. **Check Component Outputs**:

   - **Receiver**:
     ```bash
     # Main logs
     cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/receiver/stdout.log
     cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/receiver/stderr.log
     
     # Apptainer container logs
     cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/receiver/apptainer.log
     
     # Process details
     cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/receiver/process.log
     
     # Check receiver IP file
     cat ${CYLC_WORKFLOW_SHARE_DIR}/receiver_ip
     ```

   - **GPU Proxy**:
     ```bash
     # Main logs
     cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/proxy/stdout.log
     cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/proxy/stderr.log
     
     # Apptainer container logs
     cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/proxy/apptainer.log
     
     # Memory usage logs
     cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/proxy/memory.log
     
     # Check proxy IP file
     cat ${CYLC_WORKFLOW_SHARE_DIR}/proxy_ip
     ```

   - **Sender**:
     ```bash
     # Main logs
     cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/sender/stdout.log
     cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/sender/stderr.log
     
     # Apptainer container logs
     cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/sender/apptainer.log
     ```

4. **Stop the Workflow**:
   ```bash
   cylc stop gpu-proxy
   ```

## Directory Structure

The workflow creates the following directory structure under `CYLC_WORKFLOW_SHARE_DIR`:

```
~/<username>/cylc-run/gpu-proxy/run<cycle>/share/
├── logs/
│   ├── receiver/
│   │   ├── stdout.log
│   │   ├── stderr.log
│   │   ├── apptainer.log
│   │   └── process.log
│   ├── proxy/
│   │   ├── stdout.log
│   │   ├── stderr.log
│   │   ├── apptainer.log
│   │   └── memory.log
│   └── sender/
│       ├── stdout.log
│       ├── stderr.log
│       └── apptainer.log
├── output/
│   └── received_data.bin
├── input/
├── receiver_ip
└── proxy_ip
```

For example, to check the receiver's logs:
```bash
# Replace <username> with your username and <cycle> with the run number
cat ~/<username>/cylc-run/gpu-proxy/run<cycle>/share/logs/receiver/stdout.log

# Example with actual values:
cat ~/jeng/cylc-run/gpu-proxy/run7/share/logs/receiver/stdout.log
```

The path components are:
- `~/<username>`: Your home directory
- `cylc-run/gpu-proxy`: The workflow directory
- `run<cycle>`: The run number (e.g., run7)
- `share`: The shared directory containing logs and data

## Workflow Configuration

The workflow is configured in `flow.cylc` with the following settings:

- **Task Dependencies**: 
  - Receiver must start before GPU proxy
  - GPU proxy must start before sender
  - Each task signals when its container is ready using `cylc message`

- **Resource Requirements**:
  - Receiver: 4 CPUs, 8GB memory
  - GPU Proxy: 1 GPU, 12GB memory, 4 CPUs
  - Sender: 4 CPUs, 8GB memory

- **Time Limits**:
  - All tasks: 2 hours (PT2H)

## Troubleshooting

1. **Check SLURM Job Status**:
   ```bash
   squeue -u $USER
   ```

2. **View Detailed Job Information**:
   ```bash
   scontrol show job <job_id>
   ```

3. **Check Container Logs**:
   ```bash
   # For each task, check the apptainer.log file
   cat ${CYLC_WORKFLOW_SHARE_DIR}/logs/<task_name>/apptainer.log
   ```

4. **Verify IP Files**:
   - Ensure `receiver_ip` and `proxy_ip` files are created in `${CYLC_WORKFLOW_SHARE_DIR}`
   - Check that IP addresses are valid and accessible

5. **Check Data Transfer**:
   - Receiver output: `${CYLC_WORKFLOW_SHARE_DIR}/output/received_data.bin`
   - Check file size changes to verify data transfer

## Notes

- The workflow uses message triggers to ensure proper task sequencing
- Each component runs in its own Apptainer container
- Network communication between components is handled via TCP/IP
- All components log their activities to dedicated log directories
- Logs are organized by task and type (stdout, stderr, apptainer, process, memory) 