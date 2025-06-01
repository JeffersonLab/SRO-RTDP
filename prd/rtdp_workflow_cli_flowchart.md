# RTDP Workflow CLI: Design Diagrams

## 1. High-Level Flowchart

This flowchart illustrates the main user interactions and CLI operations for the RTDP Workflow CLI.

```mermaid
flowchart TD
    A[User] -->|Writes YAML config| B[RTDP CLI: generate]
    B -->|Validates config| C{Config valid?}
    C -- No --> D[Show error/help]
    C -- Yes --> E[Generate flow.cylc & files]
    E --> F[RTDP CLI: run]
    F -->|Runs cylc install/play| G[Cylc Workflow Running]
    G --> H[RTDP CLI: status/logs]
    H -->|Monitors & streams logs| I[User]
    G --> J[RTDP CLI: stop/restart]
    J -->|Manages workflow| G
    E --> K[RTDP CLI: validate]
    K -->|Checks config| C
    E --> L[RTDP CLI: template-vars]
    L -->|Shows required variables| A
```

---

## 2. Category Chart (Functional Areas)

This chart shows the main functional areas of the CLI and their relationships.

```mermaid
graph TD
    CLI[RTDP Workflow CLI]
    CLI --> Gen[Workflow Generation]
    CLI --> Run[Workflow Execution]
    CLI --> Mon[Workflow Monitoring]
    CLI --> Mgmt[Workflow Management]
    Gen -->|Input| Config[User YAML/JSON Config]
    Gen -->|Output| Cylc[flow.cylc, configs]
    Run -->|Uses| Cylc
    Run -->|Calls| CylcCmd[Cylc install/play]
    Mon -->|Reads| Status[Workflow Status/Logs]
    Mgmt -->|Controls| Cylc
    CLI --> Doc[Documentation/Help]
    CLI --> Ext[Extensibility (Plugins)]
```

---

## 3. Component/Command Category Table

| Category             | Subcommands/Features                | Description                                 |
|----------------------|-------------------------------------|---------------------------------------------|
| Workflow Generation  | `generate`, `validate`, `template-vars` | Create and validate workflow configs        |
| Workflow Execution   | `run`                               | Launch workflows via Cylc                   |
| Workflow Monitoring  | `status`, `logs`, `list`            | Monitor, list, and stream workflow status   |
| Workflow Management  | `stop`, `restart`, `remove`         | Manage workflow lifecycle                   |
| Documentation/Help   | `help`, `template-vars`             | Show help, required variables, examples     |
| Extensibility        | Plugin system                       | Add new component types or workflow logic   |

</rewritten_file> 