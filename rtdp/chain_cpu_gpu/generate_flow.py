#!/usr/bin/env python3

import yaml
import os
import sys

def generate_flow_cylc(config_path):
    # Read the chain configuration
    with open(config_path, 'r') as f:
        config = yaml.safe_load(f)
    
    if not config or 'chain' not in config:
        print("Error: Invalid chain configuration")
        sys.exit(1)
    
    components = config['chain']
    if not components:
        print("Error: No components found in chain configuration")
        sys.exit(1)
    
    # Generate the flow.cylc content
    flow_content = """[scheduler]
    allow implicit tasks = True
    UTC mode = True

[scheduling]
    cycling mode = integer
    initial cycle point = 1
    final cycle point = 1

    [[graph]]
        R1 = \"""
            # Start chain
            receiver:ready => component_0:ready
"""
    
    # Add component dependencies
    for i in range(len(components)):
        if i < len(components) - 1:
            flow_content += f"            component_{i}:ready => component_{i+1}:ready\n"
        else:
            flow_content += f"            component_{i}:ready => sender\n"
    
    flow_content += """
            # Completion chain (separate from start chain)
            sender:succeeded => !receiver
            receiver:completed
        \"""

[runtime]
    [[root]]
        # Common settings for all tasks
        platform = jlab_slurm
        [[[job]]]
            execution time limit = PT2H    # 2 hours timeout
        
        [[[directives]]]
            --ntasks = 1
            --output = slurm_%j.log
            --error = slurm_%j.log
        
        [[[environment]]]
            # Path to SIF files
            CPU_EMU_SIF = "$CYLC_WORKFLOW_RUN_DIR/sifs/cpu-emu.sif"
            GPU_PROXY_SIF = "$CYLC_WORKFLOW_RUN_DIR/sifs/gpu-proxy.sif"
            
            # Configuration file
            CHAIN_CONFIG = "$CYLC_WORKFLOW_RUN_DIR/chain_config.yaml"
            
            # Add local bin to PATH
            PATH = "$CYLC_WORKFLOW_RUN_DIR/bin:$PATH"
            
            # Set YQ_PATH to ensure we use the local yq
            YQ_PATH = "$CYLC_WORKFLOW_RUN_DIR/bin/yq"

            # Directory paths
            OUTPUT_DIR = "$CYLC_WORKFLOW_SHARE_DIR/output"
            INPUT_DIR = "$CYLC_WORKFLOW_SHARE_DIR/input"
            LOG_DIR = "$CYLC_WORKFLOW_SHARE_DIR/logs"
"""
    
    # Generate component tasks
    for i, comp in enumerate(components):
        comp_type = comp['type']
        node = comp['node']
        in_port = comp['in_port']
        out_port = comp['out_port']
        
        # Get next node for non-last components
        next_node = components[i+1]['node'] if i < len(components) - 1 else None
        
        flow_content += f"""
    [[component_{i}]]
        [[[outputs]]]
            ready = "ready"
        [[[environment]]]
            # Add local bin to PATH for this task
            PATH = "$CYLC_WORKFLOW_RUN_DIR/bin:$PATH"
            YQ_PATH = "$CYLC_WORKFLOW_RUN_DIR/bin/yq"
        script = \"""
            # Create log directory
            mkdir -p ${{LOG_DIR}}/component_{i}

            # Redirect all output to log files
            exec 1> >(tee -a "${{LOG_DIR}}/component_{i}/stdout.log")
            exec 2> >(tee -a "${{LOG_DIR}}/component_{i}/stderr.log")

            echo "[\\\$(date -u '+%Y-%m-%dT%H:%M:%SZ')] Starting component {i} of type {comp_type}"
"""
        
        if comp_type == 'cpu':
            flow_content += f"""
            # CPU component
            apptainer run --pwd /app $CPU_EMU_SIF emulator \
                -r {in_port} \
                -i {next_node} \
                -p {out_port} \
                -t {comp['params']['threads']} \
                -b {comp['params']['latency']} \
                -m {comp['params']['mem_footprint']} \
                -o {comp['params']['output_size']} \
                2>${{LOG_DIR}}/component_{i}/apptainer.log
"""
        else:
            flow_content += f"""
            # GPU component
            apptainer run --nv --pwd /app $GPU_PROXY_SIF proxy \
                -r {in_port} \
                -i {next_node} \
                -p {out_port} \
                -w {comp['params']['matrix_width']} \
                -s {comp['params']['send_rate']} \
                -g {comp['params']['group_size']} \
                2>${{LOG_DIR}}/component_{i}/apptainer.log
"""
        
        flow_content += """
            # Signal ready state
            echo "[\\\$(date -u '+%Y-%m-%dT%H:%M:%SZ')] Signaling ready state"
            cylc message "ready"
            echo "[\\\$(date -u '+%Y-%m-%dT%H:%M:%SZ')] Ready message sent"
        \"""
"""
        
        # Add SLURM directives based on component type
        if comp_type == 'cpu':
            flow_content += f"""
        [[[directives]]]
            --job-name = component_{i}
            --partition = ifarm
            --cpus-per-task = 8
            --mem = 16G
"""
        else:
            flow_content += f"""
        [[[directives]]]
            --job-name = component_{i}
            --partition = gpu
            --cpus-per-task = 4
            --mem = 8G
            --gres = gpu:A100:1
"""
    
    # Add sender task
    first_comp = components[0]
    flow_content += f"""
    [[sender]]
        [[[outputs]]]
            ready = "ready"
            completed = "send_complete"
        [[[environment]]]
            # Add local bin to PATH for this task
            PATH = "$CYLC_WORKFLOW_RUN_DIR/bin:$PATH"
        script = \"""
            # Create log directory
            mkdir -p ${{LOG_DIR}}/sender

            # Redirect all output to log files
            exec 1> >(tee -a "${{LOG_DIR}}/sender/stdout.log")
            exec 2> >(tee -a "${{LOG_DIR}}/sender/stderr.log")

            echo "[\\\$(date -u '+%Y-%m-%dT%H:%M:%SZ')] Starting sender task"
            
            # Create input directory
            mkdir -p ${{INPUT_DIR}}
            
            # Run sender
            apptainer run --pwd /app $CPU_EMU_SIF sender -i {first_comp['node']} -p {first_comp['in_port']} 2>${{LOG_DIR}}/sender/apptainer.log
            
            # Signal completion
            cylc message -- "send_complete"
        \"""
        [[[directives]]]
            --job-name = sender
            --partition = ifarm
            --cpus-per-task = 8
            --mem = 16G
"""
    
    # Add receiver task
    last_comp = components[-1]
    flow_content += f"""
    [[receiver]]
        [[[outputs]]]
            ready = "ready"
            completed = "Transfer completed successfully"
        [[[environment]]]
            # Add local bin to PATH for this task
            PATH = "$CYLC_WORKFLOW_RUN_DIR/bin:$PATH"
        script = \"""
            # Create log directory
            mkdir -p ${{LOG_DIR}}/receiver

            # Redirect all output to log files
            exec 1> >(tee -a "${{LOG_DIR}}/receiver/stdout.log")
            exec 2> >(tee -a "${{LOG_DIR}}/receiver/stderr.log")

            TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
            echo "[$TIMESTAMP] Starting receiver task"
            
            # Create output directory
            mkdir -p ${{OUTPUT_DIR}}
            
            # Start receiver in background
            apptainer run --pwd /app $CPU_EMU_SIF receiver -z -r {last_comp['out_port']} > ${{OUTPUT_DIR}}/received_data.bin 2>${{LOG_DIR}}/receiver/apptainer.log &
            
            RECV_PID=$!
            TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
            echo "[$TIMESTAMP] Receiver started with PID $RECV_PID"
            
            # Brief pause to let process start
            sleep 2
            
            # Signal readiness if process is running
            if kill -0 $RECV_PID 2>/dev/null; then
                TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
                echo "[$TIMESTAMP] Signaling ready state"
                cylc message "ready"
                echo "[$TIMESTAMP] Ready message sent"
            else
                TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
                echo "[$TIMESTAMP] ERROR: Receiver process not running" >&2
                cat ${{LOG_DIR}}/receiver/apptainer.log >&2
                exit 1
            fi
            
            # Create a flag file to indicate we should keep running
            KEEP_RUNNING=1
            trap 'KEEP_RUNNING=0' TERM INT
            
            # Create a flag file to track completion
            COMPLETION_FILE="${{CYLC_WORKFLOW_SHARE_DIR}}/receiver_complete"
            rm -f "${{COMPLETION_FILE}}"
            
            # Initialize file size tracking
            PREV_SIZE=0
            if [ -f "${{OUTPUT_DIR}}/received_data.bin" ]; then
                PREV_SIZE=$(stat -c %s "${{OUTPUT_DIR}}/received_data.bin" || echo 0)
            fi
            
            # Monitor the receiver process
            while [ $KEEP_RUNNING -eq 1 ] && kill -0 $RECV_PID 2>/dev/null; do
                TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
                echo "[$TIMESTAMP] Receiver process is running with PID $RECV_PID"
                echo "Process details:" >> ${{LOG_DIR}}/receiver/process.log
                ps -fp $RECV_PID >> ${{LOG_DIR}}/receiver/process.log 2>&1 || true
                
                # Check if data has been received by monitoring file size changes
                CURRENT_SIZE=0
                if [ -f "${{OUTPUT_DIR}}/received_data.bin" ]; then
                    CURRENT_SIZE=$(stat -c %s "${{OUTPUT_DIR}}/received_data.bin" || echo 0)
                fi
                
                if [ $CURRENT_SIZE -gt $PREV_SIZE ] && [ ! -f "${{COMPLETION_FILE}}" ]; then
                    TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
                    echo "[$TIMESTAMP] Data received successfully (size: $CURRENT_SIZE bytes)"
                    echo "Transfer completed successfully"
                    cylc message -- "Transfer completed successfully"
                    touch "${{COMPLETION_FILE}}"
                fi
                
                PREV_SIZE=$CURRENT_SIZE
                sleep 5
            done
            
            # Check final status
            FINAL_SIZE=0
            if [ -f "${{OUTPUT_DIR}}/received_data.bin" ]; then
                FINAL_SIZE=$(stat -c %s "${{OUTPUT_DIR}}/received_data.bin" || echo 0)
            fi
            
            if [ $FINAL_SIZE -gt 0 ]; then
                TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
                echo "[$TIMESTAMP] Transfer completed successfully (final size: $FINAL_SIZE bytes)"
                exit 0
            else
                TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
                echo "[$TIMESTAMP] ERROR: Transfer failed or incomplete" >&2
                echo "Last few lines of apptainer log:" >&2
                tail -n 20 ${{LOG_DIR}}/receiver/apptainer.log >&2
                exit 1
            fi
        \"""
        [[[directives]]]
            --job-name = receiver
            --partition = ifarm
            --cpus-per-task = 8
            --mem = 16G
"""
    
    # Write the generated flow.cylc file
    cylc_dir = 'cylc'
    if not os.path.exists(cylc_dir):
        print(f"Error: {cylc_dir} directory does not exist. Please create it first.")
        sys.exit(1)
    with open(os.path.join(cylc_dir, 'flow.cylc'), 'w') as f:
        f.write(flow_content)
    
    print(f"Successfully generated flow.cylc in {cylc_dir}/ directory")

if __name__ == '__main__':
    if len(sys.argv) != 2:
        print("Usage: python generate_flow.py <chain_config.yaml>")
        sys.exit(1)
    
    config_path = sys.argv[1]
    generate_flow_cylc(config_path) 