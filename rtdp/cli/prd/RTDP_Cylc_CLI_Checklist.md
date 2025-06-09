# RTDP Cylc Workflow CLI Checklist

## 1. Workflow Generation
- [x] **Generate workflow from YAML config and Jinja2 template**
  - Command: `python -m rtdp.cli.rtdpcli generate`
  - Supports:  
    - [x] CPU Emulator  
    - [x] GPU Proxy  
    - [x] Chain Workflow  
  - Features:
    - [x] Reads YAML config
    - [x] Renders Jinja2 template
    - [x] Writes `flow.cylc` and `config.yml` to output directory

## 2. Workflow Execution
- [x] **Run workflow from generated directory**
  - Command: `python -m rtdp.cli.rtdpcli run`
  - Supports:  
    - [x] CPU Emulator  
    - [x] GPU Proxy  
    - [x] Chain Workflow  
  - Features:
    - [x] Locates `flow.cylc` and `config.yml`
    - [x] Runs Cylc install/play commands in the workflow directory

## 3. Planned/Other Features (from PRD/flowcharts)
- [ ] **Validate**: Validate YAML config against schema
- [ ] **Status**: Show workflow status
- [ ] **Logs**: Fetch and display logs for tasks
- [ ] **List**: List available/generated workflows
- [ ] **Stop**: Stop a running workflow
- [ ] **Restart**: Restart a workflow
- [ ] **Remove**: Remove a workflow and its files
- [ ] **Help/Docs**: CLI help and usage documentation
- [ ] **Test coverage**: Pytest tests for all CLI features

## 4. Other PRD/Flowchart Items
- [x] **Config-driven workflow generation**
- [x] **Support for multiple workflow types**
- [x] **Consistent output/log directory structure**
- [x] **Container image path configuration**
- [x] **Jinja2 template parameterization**
- [x] **Example configs for each workflow**

---

**Legend:**  
- [x] = Complete  
- [ ] = To Do / In Progress 