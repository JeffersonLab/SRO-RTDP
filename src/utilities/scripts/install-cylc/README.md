# Cylc Installation Guide

This guide provides instructions for setting up the cylc workflow engine environment.

## Prerequisites

- Python 3.6 or higher
- pip (Python package installer)

## Installation Steps

1. Create a virtual environment (recommended):
   ```bash
   python -m venv cylc-env
   source cylc-env/bin/activate
   ```

2. Install cylc using pip:
   ```bash
   pip install cylc-flow
   ```

3. Create the necessary directory structure:
   ```bash
   mkdir -p ~/.cylc/flow
   ```

4. Copy the global configuration file:
   - Place the `global.cylc` file in `~/.cylc/flow/`
   - This file contains platform-specific configurations for running cylc workflows

## Configuration

The `global.cylc` file should be placed in `~/.cylc/flow/` and contains important platform configurations. Please refer to the [global.cylc](global.cylc) file for more details.

## Verification

To verify the installation:
```bash
cylc version
```
To check the configuration file:
```bash
cylc config
```
To check the workflow status:
```bash
cylc tui
```

## Additional Resources

- [Cylc Documentation](https://cylc.github.io/cylc-doc/stable/html/index.html)
- [Cylc GitHub Repository](https://github.com/cylc/cylc-flow)

## Apptainer Configuration

Set the following environment variable for apptainer:
```bash
export APPTAINER_CACHEDIR=/path/to/your/cache/directory
```
Add this line to your `~/.bashrc` or `~/.bash_profile` for persistence:
```bash
echo 'export APPTAINER_CACHEDIR=/path/to/your/cache/directory' >> ~/.bashrc
echo 'export APPTAINER_TMPDIR=/path/to/your/tmp/directory' >> ~/.bashrc
``` 