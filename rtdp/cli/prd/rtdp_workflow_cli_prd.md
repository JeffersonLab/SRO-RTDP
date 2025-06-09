# Product Requirements Document (PRD): RTDP Workflow CLI

## 0. Environment Setup (Python Virtual Environment)

To ensure a reproducible and isolated environment for both users and developers, all CLI usage and test development should be performed inside a Python virtual environment (venv).

**Recommended steps:**

1. Create and activate a virtual environment:

   **On Linux/macOS:**
   ```bash
   python -m venv venv
   source venv/bin/activate
   ```

   **On Windows:**
   ```bash
   python -m venv venv
   .\venv\Scripts\activate
   ```

2. Install the required packages:
   ```bash
   pip install -r requirements.txt
   ```

This approach is consistent with the RTDP Python application best practices (see `rtdp/python/generate_cylc/README.md`).

---

## 1. Overview

The RTDP Workflow CLI is a command-line tool to streamline the creation, execution, and monitoring of Cylc workflows for RTDP data processing pipelines. It will support all workflow types and components defined in the @rtdp project, and provide a unified interface for both workflow generation and operational management.

---

## 2. Goals

- **Unified CLI** for workflow generation, execution, and monitoring.
- **Support for all RTDP workflow types** (sender, emulator, receiver, load balancer, aggregator, etc.).
- **Integration with Cylc** for workflow orchestration.
- **User-friendly**: Suitable for both new and advanced users.
- **Extensible**: Easy to add new workflow types or components.
- **Scriptable**: Usable in shell scripts, CI/CD, and Jupyter notebooks.

---

## 3. User Stories

- As a user, I want to generate a Cylc workflow from a YAML/JSON config or interactively, so I can quickly set up new pipelines.
- As a user, I want to run a generated workflow on a target platform (e.g., SLURM cluster) using a single CLI command.
- As a user, I want to monitor the status and logs of running workflows from the CLI.
- As a user, I want to validate my workflow configuration before running.
- As a user, I want to list, stop, or restart workflows.
- As a user, I want to export workflow graphs or configs for documentation or sharing.

---

## 4. Functional Requirements

### 4.1 Workflow Generation

- **User Configuration File**:  
  Users must define their workflow YAML (`my_workflow.yml`) based on the required variables and structure found in the target `flow.cylc` template.  
  - The CLI will provide a schema or template (auto-generated or documented) that lists all required variables (e.g., `BASE_PORT`, `COMPONENTS`, `THREADS`, `LATENCY`, `MEM_FOOTPRINT`, `OUTPUT_SIZE`, `SLEEP`, `VERBOSE`, resource directives, etc.).
  - The CLI will validate the user's YAML to ensure all required variables for the workflow are present and correctly typed.
  - The CLI will provide a command to print or export the required variables for a given workflow template, helping users author valid configs.
  - The CLI will support both a "strict" mode (all required variables must be present) and a "defaulted" mode (missing variables are filled with documented defaults).

- **Mapping to flow.cylc**:  
  The CLI will map user-provided YAML variables directly to the environment variables, resource directives, and other settings in the generated `flow.cylc` file.  
  - Any variable used in `[[[environment]]]` or `[[[directives]]]` in `flow.cylc` must be settable via the user config.
  - The CLI will warn or error if a required variable is missing.

- **Documentation/Help**:  
  The CLI will provide documentation and examples for the required YAML structure, including:
  - A command to print a sample config for a given workflow type.
  - Inline help for each variable (description, type, default).

- Generate Cylc workflow files (`flow.cylc`, configs) from:
  - YAML/JSON config files
  - Interactive prompts (optional)
  - Jupyter notebook cells (optional)
- Validate configs against schema before generation.
- Support all RTDP component types and their options.
- Output ready-to-run workflow directories.

### 4.2 Workflow Execution

- Run a workflow using Cylc (`cylc install`, `cylc play`).
- Support platform-specific options (e.g., SLURM partition, resources).
- Optionally build or pull required containers (Docker/Singularity).

### 4.3 Workflow Monitoring

- List running and completed workflows.
- Show workflow status, task status, and logs.
- Stream logs for specific tasks/components.
- Optionally visualize workflow DAG/graph in terminal or export as image.

### 4.4 Workflow Management

- Stop, restart, or remove workflows.
- Export workflow configuration or graph.
- Clean up workflow outputs and logs.

---

## 5. Non-Functional Requirements

- **Cross-platform**: Linux, macOS (Windows optional).
- **Python 3.8+** (or as required by RTDP stack).
- **Extensible**: Plugin system for new component types.
- **Documentation**: CLI help, man page, and example configs.
- **Testable**: Unit and integration tests.

---

## 6. Technical Considerations

- **CLI Framework**: Click, Typer, argparse, or similar.
- **Cylc Integration**: Shell out to `cylc` commands, parse outputs.
- **Config Validation**: Use JSONSchema or PyYAML validation.
- **Monitoring**: Poll Cylc status, parse logs, or use Cylc APIs if available.
- **Jupyter Integration**: Optional, via IPython magics or notebook widgets.
- **Container Support**: Optionally build/pull Docker/Singularity images.

---

## 7. Example CLI Commands

```sh
# Print required variables for a workflow template
rtdpcli template-vars --workflow-type cpu-emu

# Generate a workflow, validating all required variables
rtdpcli generate --config my_workflow.yml --output ./myflow

# Validate a config file against the required variables
rtdpcli validate --config my_workflow.yml --workflow-type cpu-emu

# Run a workflow
rtdpcli run --workflow ./myflow

# Monitor a workflow
rtdpcli status --workflow cpu-emu

# Stream logs
rtdpcli logs --workflow cpu-emu --task emulator

# List all workflows
rtdpcli list

# Stop a workflow
rtdpcli stop --workflow cpu-emu
```

---

## 8. Open Questions

- Should the CLI support interactive mode for config generation?
- Should it support remote monitoring (e.g., via SSH)?
- How much Jupyter integration is needed?
- Should it support workflow graph export (e.g., Graphviz, PNG)?

---

## 9. Deliverables

- CLI tool (`rtdpcli` or similar) with subcommands for generate, run, monitor, manage.
- Example configs and documentation.
- Test suite.
- PRD and design docs in `prd/`.

---

*This is a first draft for review and revision.* 