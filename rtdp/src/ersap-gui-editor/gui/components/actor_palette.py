"""
Actor palette component for the ERSAP Actor Editor.
"""

import tkinter as tk
from tkinter import ttk
from core.model import ActorType, Actor


class ActorPalette(ttk.Frame):
    """Palette showing available actor types for drag and drop."""
    
    def __init__(self, parent, app):
        super().__init__(parent)
        self.app = app
        self.setup_ui()
    
    def setup_ui(self):
        """Setup the palette UI."""
        # Title
        title_label = ttk.Label(self, text="Actor Types", font=("Arial", 12, "bold"))
        title_label.pack(pady=(0, 10))
        
        # Actor type buttons
        self.create_actor_button("Source", ActorType.SOURCE, "Produces data")
        self.create_actor_button("Processor", ActorType.PROCESSOR, "Processes data")
        self.create_actor_button("Sink", ActorType.SINK, "Consumes data")
        
        # Separator
        ttk.Separator(self, orient=tk.HORIZONTAL).pack(fill=tk.X, pady=10)
        
        # Instructions
        instructions = """Drag actor types to the canvas or click "Add Actor" to create actors with custom properties."""
        instruction_label = ttk.Label(self, text=instructions, wraplength=180, justify=tk.CENTER)
        instruction_label.pack(pady=10)
    
    def create_actor_button(self, name: str, actor_type: ActorType, description: str):
        """Create a button for an actor type."""
        frame = ttk.Frame(self)
        frame.pack(fill=tk.X, pady=2)
        
        # Button
        button = ttk.Button(frame, text=name, 
                           command=lambda: self.add_actor_to_canvas(actor_type))
        button.pack(fill=tk.X)
        
        # Description
        desc_label = ttk.Label(frame, text=description, font=("Arial", 8), foreground="gray")
        desc_label.pack()
        
        # Bind drag events
        button.bind("<Button-1>", lambda e: self.start_drag(e, actor_type))
        button.bind("<B1-Motion>", self.on_drag)
        button.bind("<ButtonRelease-1>", self.end_drag)
    
    def add_actor_to_canvas(self, actor_type: ActorType):
        """Add an actor of the specified type to the canvas."""
        # Generate a default name
        base_name = actor_type.value
        counter = 1
        name = f"{base_name}_{counter}"
        
        while name in self.app.graph.actors:
            counter += 1
            name = f"{base_name}_{counter}"
        
        # Create actor with default properties
        if actor_type == ActorType.SOURCE:
            actor = Actor(
                name=name,
                type=actor_type,
                output_topic=f"data:events:{name}_output",
                host="localhost"
            )
        elif actor_type == ActorType.PROCESSOR:
            actor = Actor(
                name=name,
                type=actor_type,
                input_topic=f"data:events:{name}_input",
                output_topic=f"data:events:{name}_output",
                host="localhost"
            )
        else:  # SINK
            actor = Actor(
                name=name,
                type=actor_type,
                input_topic=f"data:events:{name}_input",
                host="localhost"
            )
        
        # Add to graph
        errors = self.app.graph.add_actor(actor)
        if not errors:
            self.app.update_ui()
            self.app.set_status(f"Added {actor_type.value} actor: {name}")
        else:
            from tkinter import messagebox
            error_msg = "\n".join(errors)
            messagebox.showerror("Error", f"Failed to add actor:\n{error_msg}")
    
    def start_drag(self, event, actor_type: ActorType):
        """Start dragging an actor type."""
        self.drag_actor_type = actor_type
        self.drag_start_x = event.x
        self.drag_start_y = event.y
    
    def on_drag(self, event):
        """Handle drag motion."""
        if hasattr(self, 'drag_actor_type'):
            # Calculate drag distance
            dx = event.x - self.drag_start_x
            dy = event.y - self.drag_start_y
            distance = (dx * dx + dy * dy) ** 0.5
            
            # Only start drag if moved more than threshold
            if distance > 5:
                self.app.canvas.start_drag_from_palette(self.drag_actor_type, event)
    
    def end_drag(self, event):
        """End dragging."""
        if hasattr(self, 'drag_actor_type'):
            delattr(self, 'drag_actor_type')
            self.app.canvas.end_drag_from_palette() 