from PyQt6.QtWidgets import QGraphicsView, QGraphicsScene
from PyQt6.QtCore import Qt, QRectF, QPointF
from PyQt6.QtGui import QPainter, QPen, QBrush, QColor
import networkx as nx

class ComponentNode:
    def __init__(self, name, x, y, width=120, height=60):
        self.name = name
        self.x = x
        self.y = y
        self.width = width
        self.height = height
        
    def contains(self, point):
        return (self.x <= point.x() <= self.x + self.width and 
                self.y <= point.y() <= self.y + self.height)
    
    def center(self):
        return QPointF(self.x + self.width/2, self.y + self.height/2)

class WorkflowGraphView(QGraphicsView):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.scene = QGraphicsScene(self)
        self.setScene(self.scene)
        self.setRenderHint(QPainter.RenderHint.Antialiasing)
        
        # Enable drag and drop
        self.setAcceptDrops(True)
        
        # Initialize graph
        self.graph = nx.DiGraph()
        self.nodes = {}
        self.selected_node = None
        
        # Visual settings
        self.node_color = QColor(200, 220, 255)
        self.selected_color = QColor(255, 200, 200)
        self.edge_color = QColor(100, 100, 100)
        
    def dragEnterEvent(self, event):
        if event.mimeData().hasText():
            event.acceptProposedAction()
    
    def dragMoveEvent(self, event):
        event.acceptProposedAction()
    
    def dropEvent(self, event):
        pos = self.mapToScene(event.position().toPoint())
        component_type = event.mimeData().text()
        
        # Create new node
        node_name = f"{component_type}_{len(self.nodes)}"
        node = ComponentNode(node_name, pos.x(), pos.y())
        self.nodes[node_name] = node
        
        # Update graph
        self.graph.add_node(node_name, type=component_type)
        self.update_graph()
        
        event.acceptProposedAction()
    
    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            pos = self.mapToScene(event.pos())
            self.selected_node = None
            
            # Check if clicked on a node
            for node in self.nodes.values():
                if node.contains(pos):
                    self.selected_node = node
                    break
            
            self.update_graph()
        
        super().mousePressEvent(event)
    
    def mouseMoveEvent(self, event):
        if event.buttons() & Qt.MouseButton.LeftButton and self.selected_node:
            pos = self.mapToScene(event.pos())
            self.selected_node.x = pos.x() - self.selected_node.width/2
            self.selected_node.y = pos.y() - self.selected_node.height/2
            self.update_graph()
        
        super().mouseMoveEvent(event)
    
    def mouseReleaseEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton and self.selected_node:
            # Check for connections with other nodes
            pos = self.mapToScene(event.pos())
            for node in self.nodes.values():
                if node != self.selected_node and node.contains(pos):
                    # Add edge to graph
                    self.graph.add_edge(self.selected_node.name, node.name)
            
            self.selected_node = None
            self.update_graph()
        
        super().mouseReleaseEvent(event)
    
    def update_graph(self):
        self.scene.clear()
        
        # Draw edges
        for edge in self.graph.edges():
            start_node = self.nodes[edge[0]]
            end_node = self.nodes[edge[1]]
            
            pen = QPen(self.edge_color)
            pen.setWidth(2)
            self.scene.addLine(
                start_node.center().x(), start_node.center().y(),
                end_node.center().x(), end_node.center().y(),
                pen
            )
        
        # Draw nodes
        for node in self.nodes.values():
            color = self.selected_color if node == self.selected_node else self.node_color
            self.scene.addRect(
                node.x, node.y, node.width, node.height,
                QPen(Qt.PenStyle.SolidLine),
                QBrush(color)
            )
            self.scene.addText(node.name).setPos(
                node.x + 10,
                node.y + node.height/2 - 10
            )
        
        # Adjust scene rect
        self.scene.setSceneRect(self.scene.itemsBoundingRect()) 