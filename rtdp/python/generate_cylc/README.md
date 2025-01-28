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