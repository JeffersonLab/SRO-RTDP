# ERSAP GUI Editor

A **desktop GUI application** for visually designing and configuring ERSAP (Environment for Realtime Streaming Acquisition and Processing) actor workflows. This tool enables users to create complex distributed data processing pipelines using a drag-and-drop interface without writing any code.

## 🎯 Overview

The ERSAP GUI Editor transforms the complex task of configuring distributed ERSAP workflows into an intuitive visual experience. It provides a complete solution for designing, validating, and exporting actor topologies that can be deployed across multiple nodes in the ERSAP ecosystem.

## ✨ Key Features

### Visual Workflow Design
- **Drag-and-Drop Interface**: Drag actors from palette to canvas
- **Visual Connections**: Click-and-drag to connect actors with visual feedback
- **Real-time Validation**: Immediate feedback on configuration errors
- **Property Editing**: In-place editing of actor properties and parameters

### ERSAP Integration
- **xMsg Topic Support**: Full support for ERSAP's messaging format (`domain:subject:type`)
- **Actor Type Support**: All ERSAP actor types (source, processor, sync)
- **Host Configuration**: Support for distributed deployment across multiple nodes
- **Parameter Management**: Custom parameters for each actor

### Project Management
- **Save/Load Projects**: `.ersapproj` files preserve complete workflow state
- **Export to YAML**: Generate deployment-ready `services.yaml` files
- **Example Templates**: Built-in examples for common use cases
- **Validation**: Comprehensive validation of workflows before export

### User Experience
- **Intuitive Interface**: Familiar desktop application layout
- **Error Handling**: Clear error messages and validation feedback
- **Undo/Redo**: Support for workflow modifications
- **Context Menus**: Right-click actions for common operations

## 🏗️ Architecture

### Core Components

#### Data Model (`core/model.py`)
- **Actor**: Represents ERSAP actors with properties (name, type, input/output topics, host, parameters)
- **ActorType**: Enumeration of actor types (SOURCE, PROCESSOR, SYNC)
- **Connection**: Represents connections between actors
- **ActorGraph**: Manages the complete workflow topology with validation logic

#### YAML Processing (`core/yaml_writer.py`)
- Generates `services.yaml` files for ERSAP deployment
- Creates `.ersapproj` project files for saving/loading workflows
- Validates YAML configurations
- Provides example templates

#### GUI Framework (`gui/`)
- **App.py**: Main application window with menus, toolbar, and component integration
- **actor_palette.py**: Drag-and-drop palette of available actor types
- **canvas.py**: Interactive canvas for placing and connecting actors
- **property_panel.py**: Property editor for configuring actor parameters

### Technology Stack
- **Python 3.12+**: Core application language
- **Tkinter**: Cross-platform GUI framework
- **PyYAML**: YAML file processing
- **pytest**: Testing framework

## 📦 Installation

### Prerequisites
- Python 3.12 or higher
- pip (Python package manager)

### Setup
1. Clone or download the project
2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
3. Run the application:
   ```bash
   python main.py
   ```

## 🚀 Usage

### Basic Workflow
1. **Design**: Drag actors from palette to canvas
2. **Connect**: Create connections between actors
3. **Configure**: Set properties and parameters for each actor
4. **Validate**: System checks for configuration errors
5. **Export**: Generate deployment-ready YAML files
6. **Deploy**: Use generated files with ERSAP orchestrator

### Creating a New Workflow
1. Launch the application
2. Drag actor types from the left palette to the canvas
3. Configure each actor's properties in the right panel:
   - **Name**: Unique identifier (must start with letter/underscore)
   - **Type**: Source, Processor, or Sync
   - **Topics**: Input/output topics in `domain:subject:type` format
   - **Host**: Optional host for distributed deployment
   - **Parameters**: Custom configuration parameters
4. Connect actors by dragging from output ports to input ports
5. Save your project or export to YAML

### File Formats
- **services.yaml**: Standard ERSAP deployment configuration
- **.ersapproj**: Custom project format with metadata and workflow state

## 🔧 Data Validation

The application includes comprehensive validation to ensure workflows are correctly configured:

### Actor Validation
- **Name Validation**: Must start with letter/underscore, alphanumeric only
- **Topic Format Validation**: Enforces `domain:subject:type` format
- **Host Validation**: Basic hostname format checking
- **Type-specific Rules**: 
  - Sources must have output topics, no input topics
  - Processors must have both input and output topics
  - Sync actors must have input topics, no output topics

### Connection Validation
- **Actor Existence**: Connections must reference existing actors
- **Cycle Prevention**: Prevents circular dependencies
- **Duplicate Prevention**: No duplicate connections allowed

### Graph Validation
- **Connectivity**: Ensures all actors are connected
- **Topology**: Validates overall workflow structure

## 🧪 Testing

The project includes comprehensive test coverage:

```bash
# Run all tests
python -m pytest tests/ -v

# Run specific test file
python -m pytest tests/test_model.py -v
```

**Test Coverage**: 41 tests covering:
- Data model validation
- YAML import/export functionality
- Error handling and edge cases
- File I/O operations

## 📁 Project Structure

```
ersap-gui-editor/
├── main.py                 # Application entry point
├── requirements.txt        # Python dependencies
├── README.md              # This file
├── core/                  # Core data model and processing
│   ├── model.py           # Actor, Connection, ActorGraph classes
│   └── yaml_writer.py     # YAML import/export functionality
├── gui/                   # GUI components
│   ├── App.py             # Main application window
│   └── components/        # GUI component modules
│       ├── actor_palette.py
│       ├── canvas.py
│       └── property_panel.py
├── tests/                 # Test suite
│   ├── test_model.py      # Data model tests
│   └── test_yaml_writer.py # YAML processing tests
└── assets/                # Example files and resources
    └── examples/          # Example YAML configurations
```

## 🎨 User Interface

### Main Window Layout
- **Left Panel**: Actor palette with available actor types
- **Center**: Interactive canvas for workflow design
- **Right Panel**: Property editor for selected actors
- **Top**: Menu bar and toolbar
- **Bottom**: Status bar with validation feedback

### Menu Options
- **File**: New, Open, Save, Export, Exit
- **Edit**: Undo, Redo, Cut, Copy, Paste, Delete
- **View**: Zoom, Fit to window, Reset view
- **Help**: About, Documentation

## 🌟 Benefits

- **No-Code Workflow Design**: Visual creation of complex data pipelines
- **Rapid Prototyping**: Quick iteration on workflow designs
- **Error Prevention**: Built-in validation prevents deployment issues
- **Team Collaboration**: Project files can be shared and version controlled
- **Documentation**: Visual representation serves as workflow documentation
- **Accessibility**: Makes ERSAP accessible to non-technical users

## 🔮 Future Enhancements

Potential future features:
- **Template Library**: Pre-built workflow templates
- **Version Control**: Integration with Git for workflow versioning
- **Collaboration**: Real-time collaborative editing
- **Advanced Validation**: More sophisticated topology validation
- **Plugin System**: Extensible actor types and validators
- **Cloud Integration**: Direct deployment to cloud platforms

## 🤝 Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## 📄 License

This project is part of the ERSAP ecosystem. Please refer to the main ERSAP project for licensing information.

## 🆘 Support

For issues and questions:
- Check the validation messages in the application
- Review the test files for usage examples
- Refer to the ERSAP documentation for deployment guidance

---

**ERSAP GUI Editor** - Making distributed data processing workflows accessible to everyone. 