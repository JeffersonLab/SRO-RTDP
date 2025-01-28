import sys
from PyQt6.QtWidgets import (QApplication, QMainWindow, QWidget, QVBoxLayout, 
                            QHBoxLayout, QLabel, QLineEdit, QPushButton, 
                            QSpinBox, QGroupBox, QScrollArea, QMessageBox)
from PyQt6.QtCore import Qt
import yaml
import os

class ComponentWidget(QGroupBox):
    """Base widget for component configuration"""
    def __init__(self, title, parent=None):
        super().__init__(title, parent)
        self.layout = QVBoxLayout()
        self.setLayout(self.layout)
        self.fields = {}
        
    def add_field(self, name, label, widget_type=QLineEdit, default_value=None):
        """Add a field to the component"""
        container = QWidget()
        layout = QHBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        
        label_widget = QLabel(label)
        field_widget = widget_type()
        if default_value is not None:
            if isinstance(field_widget, QLineEdit):
                field_widget.setText(str(default_value))
            elif isinstance(field_widget, QSpinBox):
                field_widget.setValue(default_value)
        
        layout.addWidget(label_widget)
        layout.addWidget(field_widget)
        container.setLayout(layout)
        
        self.layout.addWidget(container)
        self.fields[name] = field_widget
        
    def get_config(self):
        """Get component configuration as dictionary"""
        config = {}
        for name, widget in self.fields.items():
            if isinstance(widget, QLineEdit):
                config[name] = widget.text()
            elif isinstance(widget, QSpinBox):
                config[name] = widget.value()
        return config

class ReceiverWidget(ComponentWidget):
    def __init__(self, parent=None):
        super().__init__("Receiver Configuration", parent)
        self.add_field("port", "Port:", default_value="50080")
        self.add_field("cpus", "CPUs:", widget_type=QSpinBox, default_value=4)
        self.add_field("memory", "Memory (GB):", default_value="8G")
        self.add_field("partition", "Partition:", default_value="ifarm")

class EmulatorWidget(ComponentWidget):
    def __init__(self, name, parent=None):
        super().__init__(f"Emulator {name} Configuration", parent)
        self.name = name
        self.add_field("port", "Port:", default_value="50888")
        self.add_field("cpus", "CPUs:", widget_type=QSpinBox, default_value=4)
        self.add_field("memory", "Memory (GB):", default_value="16G")
        self.add_field("partition", "Partition:", default_value="ifarm")
        self.add_field("threads", "Threads:", widget_type=QSpinBox, default_value=4)
        self.add_field("latency", "Latency (per GB):", widget_type=QSpinBox, default_value=50)
        self.add_field("mem_footprint", "Memory Footprint (GB):", default_value="0.05")
        self.add_field("output_size", "Output Size (GB):", default_value="0.001")

class SenderWidget(ComponentWidget):
    def __init__(self, parent=None):
        super().__init__("Sender Configuration", parent)
        self.add_field("cpus", "CPUs:", widget_type=QSpinBox, default_value=4)
        self.add_field("memory", "Memory (GB):", default_value="8G")
        self.add_field("partition", "Partition:", default_value="ifarm")
        self.add_field("test_data_size", "Test Data Size:", default_value="100M")

class WorkflowGeneratorGUI(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("CPU Emulator Workflow Generator")
        self.init_ui()
        
    def init_ui(self):
        # Create central widget and layout
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        main_layout = QVBoxLayout()
        central_widget.setLayout(main_layout)
        
        # Create scroll area for components
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll_widget = QWidget()
        self.components_layout = QVBoxLayout()
        scroll_widget.setLayout(self.components_layout)
        scroll.setWidget(scroll_widget)
        
        # Platform configuration
        platform_group = QGroupBox("Platform Configuration")
        platform_layout = QVBoxLayout()
        platform_group.setLayout(platform_layout)
        
        self.platform_fields = {}
        platform_fields = [
            ("name", "Platform Name:", "jlab_slurm"),
            ("cylc_path", "Cylc Path:", "/path/to/cylc-env/bin/"),
            ("hosts", "Hosts:", "username@host"),
        ]
        
        for name, label, default in platform_fields:
            container = QWidget()
            layout = QHBoxLayout()
            label_widget = QLabel(label)
            field_widget = QLineEdit(default)
            layout.addWidget(label_widget)
            layout.addWidget(field_widget)
            container.setLayout(layout)
            platform_layout.addWidget(container)
            self.platform_fields[name] = field_widget
        
        # Add components
        self.receiver = ReceiverWidget()
        self.emulators = []
        self.sender = SenderWidget()
        
        # Add platform and components to layout
        main_layout.addWidget(platform_group)
        main_layout.addWidget(scroll)
        
        # Add initial components
        self.components_layout.addWidget(self.receiver)
        self.add_emulator()  # Add first emulator
        self.components_layout.addWidget(self.sender)
        
        # Buttons
        button_layout = QHBoxLayout()
        add_emulator_btn = QPushButton("Add Emulator")
        add_emulator_btn.clicked.connect(self.add_emulator)
        generate_btn = QPushButton("Generate Configuration")
        generate_btn.clicked.connect(self.generate_config)
        
        button_layout.addWidget(add_emulator_btn)
        button_layout.addWidget(generate_btn)
        main_layout.addLayout(button_layout)
        
    def add_emulator(self):
        """Add a new emulator component"""
        emulator = EmulatorWidget(len(self.emulators) + 1)
        self.emulators.append(emulator)
        # Insert before sender
        self.components_layout.insertWidget(self.components_layout.count() - 1, emulator)
        
    def generate_config(self):
        """Generate and save workflow configuration"""
        config = {
            "workflow": {
                "name": "cpu-emu",
                "description": "Cylc-based CPU Emulator Testing Workflow"
            },
            "platform": {
                name: widget.text()
                for name, widget in self.platform_fields.items()
            },
            "resources": {
                "receiver": self._get_receiver_config(),
                "emulators": self._get_emulators_config(),
                "sender": self._get_sender_config()
            },
            "network": {
                "receiver_port": int(self.receiver.fields["port"].text())
            },
            "test_data": {
                "size": self.sender.fields["test_data_size"].text()
            },
            "containers": {
                "cpu_emulator": {
                    "image": "cpu-emu.sif",
                    "docker_source": "jlabtsai/rtdp-cpu_emu:latest"
                }
            }
        }
        
        # Save configuration
        os.makedirs("generated", exist_ok=True)
        config_path = "generated/config.yml"
        with open(config_path, 'w') as f:
            yaml.dump(config, f, default_flow_style=False)
            
        QMessageBox.information(self, "Success", 
                              f"Configuration generated successfully!\nSaved to: {config_path}")
    
    def _get_receiver_config(self):
        """Get receiver configuration"""
        return {
            "ntasks": 1,
            "cpus_per_task": self.receiver.fields["cpus"].value(),
            "mem": self.receiver.fields["memory"].text(),
            "partition": self.receiver.fields["partition"].text(),
            "timeout": "2h"
        }
    
    def _get_emulators_config(self):
        """Get configuration for all emulators"""
        return [{
            "name": f"emulator{i+1}",
            "ntasks": 1,
            "cpus_per_task": emu.fields["cpus"].value(),
            "mem": emu.fields["memory"].text(),
            "partition": emu.fields["partition"].text(),
            "timeout": "2h",
            "config": {
                "threads": emu.fields["threads"].value(),
                "latency": emu.fields["latency"].value(),
                "mem_footprint": float(emu.fields["mem_footprint"].text()),
                "output_size": float(emu.fields["output_size"].text()),
                "port": int(emu.fields["port"].text())
            }
        } for i, emu in enumerate(self.emulators)]
    
    def _get_sender_config(self):
        """Get sender configuration"""
        return {
            "ntasks": 1,
            "cpus_per_task": self.sender.fields["cpus"].value(),
            "mem": self.sender.fields["memory"].text(),
            "partition": self.sender.fields["partition"].text(),
            "timeout": "2h"
        }

def main():
    app = QApplication(sys.argv)
    window = WorkflowGeneratorGUI()
    window.resize(800, 600)
    window.show()
    sys.exit(app.exec())

if __name__ == "__main__":
    main() 