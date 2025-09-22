# Cylc Workflow Generator

A web-based interface for generating Cylc workflow configurations for CPU Emulator testing. This tool provides an intuitive way to configure and generate YAML configurations for the CPU Emulator workflow components: sender, receiver, and emulator.

## Features

- Web-based interface with form validation
- Visual workflow graph representation
- Automatic YAML configuration generation
- Default values for common settings
- Input validation for all fields
- Downloadable configuration file

## Prerequisites

- Python 3.10 or higher
- pip (Python package installer)
- Virtual environment (recommended)

## Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd <repository-directory>
```

2. Create and activate a virtual environment (recommended):
```bash
# On Linux/macOS
python -m venv venv
source venv/bin/activate

# On Windows
python -m venv venv
.\venv\Scripts\activate
```

3. Install the required packages:
```bash
pip install -r requirements.txt
```

## Configuration

1. Set up environment variables (optional):
```bash
# Linux/macOS
export SECRET_KEY="your-secret-key"

# Windows
set SECRET_KEY=your-secret-key
```

If not set, a default development key will be used.

## Running the Application

1. Start the Flask application:
```bash
python app.py
```

2. Open your web browser and navigate to:
```
http://localhost:5000
```

## Usage

1. Fill in the form fields with your desired configuration values:
   - Workflow Settings (name, description)
   - Platform Settings (platform name, Cylc path, hosts, job runner)
   - Resource Settings for each component (receiver, emulator, sender)
   - Network Settings (ports)
   - Emulator Configuration (threads, latency, memory footprint)
   - Test Data Settings
   - Container Settings

2. Click the "Generate Configuration" button to download the `config.yml` file.

## Form Field Descriptions

### Workflow Settings
- **Workflow Name**: Name of your Cylc workflow
- **Description**: Brief description of the workflow

### Platform Settings
- **Platform Name**: Name of the computing platform (e.g., jlab_slurm)
- **Cylc Path**: Path to Cylc environment
- **Hosts**: Host machine for running the workflow
- **Job Runner**: Job scheduling system (e.g., slurm)

### Component Settings (Receiver/Emulator/Sender)
- **Tasks**: Number of tasks to run
- **CPUs per Task**: Number of CPUs allocated per task
- **Memory**: Memory allocation (e.g., 8G)
- **Partition**: Computing partition/queue name
- **Timeout**: Maximum runtime for the component

### Network Settings
- **Receiver Port**: Port for the receiver component (1024-65535)
- **Emulator Port**: Port for the emulator component (1024-65535)

### Emulator Configuration
- **Threads**: Number of processing threads
- **Latency**: Processing latency per GB
- **Memory Footprint**: Memory usage in GB
- **Output Size**: Size of output data in GB

### Test Data Settings
- **Test Data Size**: Size of test data (e.g., 100M)

### Container Settings
- **Container Image**: Name of the Singularity container image
- **Docker Source**: Source Docker image for building the container

## File Structure

```
.
├── app.py              # Main Flask application
├── forms.py            # Form definitions
├── graph_generator.py  # Workflow graph visualization
├── requirements.txt    # Python dependencies
├── setup.cfg          # MyPy configuration
├── static/            # Static files (images, etc.)
└── templates/         # HTML templates
    ├── base.html
    └── index.html
```

## Development

For development purposes, the application runs in debug mode by default. To run in production:

1. Set up a proper web server (e.g., Gunicorn)
2. Set `SECRET_KEY` environment variable
3. Disable debug mode

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

[Add your license information here]

## Support

[Add support contact information here]

## Components

The system supports three types of components that can be connected to form data processing pipelines:

### Sender Component

The sender component is responsible for generating and sending data to downstream components. Features include:

- Support for reading from data sources or generating test data
- Configurable data formats (raw, HDF5, ROOT)
- Adjustable chunk sizes for data transmission
- Test data generation with different patterns (random, sequential, custom)
- Flow control with configurable sending rates

Configuration options:
```yaml
sender_config:
  data_source: "path/to/data"  # Optional data source
  data_format: "raw"           # raw, hdf5, or root
  chunk_size: "1M"            # Size of data chunks to send
  test_data:
    size: "100M"              # Total amount of test data
    pattern: "random"         # random, sequential, or custom
```

### Emulator Component

The emulator component simulates different types of data processing workloads. Features include:

- Multi-threaded processing
- Configurable processing types (CPU, memory, I/O intensive)
- Adjustable memory footprint and latency
- Customizable output size ratio
- Real-time processing statistics

Configuration options:
```yaml
emulator_config:
  threads: 4                  # Number of worker threads
  latency: 50                # Processing latency in ms
  mem_footprint: 0.05        # Memory footprint in GB
  output_size: 0.001         # Output size ratio
  processing_type: "cpu_intensive"  # cpu_intensive, memory_intensive, io_intensive
```

### Receiver Component

The receiver component receives and processes data from upstream components. Features include:

- Configurable output directory for received data
- Optional data validation
- Compression support
- Automatic file rotation based on buffer size
- Progress monitoring and statistics

Configuration options:
```yaml
receiver_config:
  output_dir: "received_data"  # Directory for storing received data
  data_validation: true       # Enable data validation
  buffer_size: "64M"         # Buffer size before file rotation
  compression: false         # Enable data compression
```

## Network Configuration

All components support network configuration for distributed operation:

```yaml
network:
  listen_port: 5000          # Port to listen for incoming connections
  bind_address: "0.0.0.0"    # Bind address for the server
  connect_to:                # List of upstream components
    - host: "component1.example.com"
      port: 5000
    - host: "component2.example.com"
      port: 5001
```

## Resource Configuration

Each component requires resource configuration for deployment:

```yaml
resources:
  partition: "compute"        # Compute partition/queue
  cpus_per_task: 4           # Number of CPUs
  mem: "4G"                  # Memory allocation
```

## Usage

1. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

2. Create a workflow configuration file:
   ```yaml
   workflow:
     name: "example_workflow"
     description: "Example data processing workflow"
   
   platform:
     name: "local"
     job_runner: "slurm"
   
   components:
     sender1:
       type: "sender"
       resources: {...}
       network: {...}
       sender_config: {...}
     
     emulator1:
       type: "emulator"
       resources: {...}
       network: {...}
       emulator_config: {...}
     
     receiver1:
       type: "receiver"
       resources: {...}
       network: {...}
       receiver_config: {...}
   
   edges:
     - from: "sender1"
       to: "emulator1"
     - from: "emulator1"
       to: "receiver1"
   ```

3. Use the web interface to create and manage workflows, or use the command-line tools:
   ```bash
   python -m generate_workflow workflow_config.yml
   ```

## Development

- Run tests: `python -m pytest tests/`
- Format code: `black .`
- Type checking: `mypy .`
- Lint: `flake8` 