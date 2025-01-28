from PyQt6.QtWidgets import QPushButton, QMainWindow, QHBoxLayout, QVBoxLayout, QGroupBox, QWidget
from PyQt6.QtCore import Qt, QDrag, QMimeData
from graph_widget import WorkflowGraphView

class ComponentButton(QPushButton):
    def __init__(self, text, parent=None):
        super().__init__(text, parent)
        self.setAcceptDrops(True)
    
    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            drag = QDrag(self)
            mime = QMimeData()
            mime.setText(self.text())
            drag.setMimeData(mime)
            drag.exec(Qt.DropAction.MoveAction)

class WorkflowGeneratorGUI(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("CPU Emulator Workflow Generator")
        self.init_ui()
    
    def init_ui(self):
        # Create central widget with splitter
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        main_layout = QHBoxLayout()
        central_widget.setLayout(main_layout)
        
        # Left panel for components
        left_panel = QWidget()
        left_layout = QVBoxLayout()
        left_panel.setLayout(left_layout)
        
        # Component buttons
        components_group = QGroupBox("Components")
        components_layout = QVBoxLayout()
        components_group.setLayout(components_layout)
        
        self.receiver_btn = ComponentButton("Receiver")
        self.emulator_btn = ComponentButton("Emulator")
        self.sender_btn = ComponentButton("Sender")
        
        components_layout.addWidget(self.receiver_btn)
        components_layout.addWidget(self.emulator_btn)
        components_layout.addWidget(self.sender_btn)
        
        left_layout.addWidget(components_group)
        
        # Configuration panels (as before)
        # ...
        
        # Right panel for graph
        self.graph_view = WorkflowGraphView()
        
        # Add panels to main layout
        main_layout.addWidget(left_panel, 1)
        main_layout.addWidget(self.graph_view, 2)
        
        # Buttons at bottom
        button_layout = QHBoxLayout()
        generate_btn = QPushButton("Generate Configuration")
        generate_btn.clicked.connect(self.generate_config)
        button_layout.addWidget(generate_btn)
        left_layout.addLayout(button_layout)
    
    # ... (rest of the class implementation) 