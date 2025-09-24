"""
Canvas component for displaying and interacting with the actor graph.
"""

import tkinter as tk
from tkinter import ttk, messagebox
import math
from core.model import Actor, ActorType, Connection


class Canvas(tk.Canvas):
    """Canvas for displaying and interacting with the actor graph."""
    
    def __init__(self, parent, app):
        super().__init__(parent, bg="white", relief=tk.SUNKEN, bd=1)
        self.app = app
        
        # Actor display properties
        self.actor_width = 120
        self.actor_height = 80
        self.actor_spacing = 200
        self.connection_color = "#666666"
        self.selected_color = "#4CAF50"
        self.default_color = "#2196F3"
        
        # State variables
        self.selected_actor = None
        self.connection_mode = False
        self.connection_source = None
        self.drag_actor = None
        self.drag_start_pos = None
        self.zoom_level = 1.0
        self.drag_offset = (0, 0)
        
        # Actor positions (name -> (x, y))
        self.actor_positions = {}
        self.connection_items = {}  # Map canvas item id -> (source, target)
        
        self.setup_bindings()
        self.update_display()
    
    def setup_bindings(self):
        """Setup mouse and keyboard bindings."""
        # Mouse bindings
        self.bind("<Button-1>", self.on_click)
        self.bind("<ButtonPress-1>", self.on_mouse_press)
        self.bind("<B1-Motion>", self.on_mouse_drag)
        self.bind("<ButtonRelease-1>", self.on_mouse_release)
        self.bind("<Button-3>", self.on_right_click)
        
        # Keyboard bindings
        self.bind("<Key>", self.on_key)
        self.bind("<Delete>", self.on_delete)
        
        # Focus
        self.focus_set()
    
    def update_display(self):
        """Update the canvas display."""
        self.delete("all")
        self.draw_connections()
        self.draw_actors()
        self.draw_connection_mode_indicator()
    
    def draw_actors(self):
        """Draw all actors on the canvas."""
        for actor in self.app.graph.actors.values():
            self.draw_actor(actor)
    
    def draw_actor(self, actor: Actor):
        """Draw a single actor."""
        # Get position from actor data or create new position
        if actor.x is not None and actor.y is not None:
            # Use saved position from actor
            self.actor_positions[actor.name] = (actor.x, actor.y)
        elif actor.name not in self.actor_positions:
            # Calculate new position
            self.actor_positions[actor.name] = self.calculate_actor_position(actor)
        
        x, y = self.actor_positions[actor.name]
        
        # Determine color
        color = self.get_actor_color(actor.type, selected=(actor.name == self.selected_actor))
        
        # Draw actor box
        x1 = x - self.actor_width // 2
        y1 = y - self.actor_height // 2
        x2 = x + self.actor_width // 2
        y2 = y + self.actor_height // 2
        
        # Main rectangle
        rect_id = self.create_rectangle(x1, y1, x2, y2, fill=color, outline="black", width=2, tags=f"actor_{actor.name}")
        self.tag_bind(rect_id, "<Button-1>", self.on_click)
        
        # Type indicator
        type_y = y1 + 15
        # Map internal type to user-friendly label (uppercase)
        type_labels = {
            ActorType.SOURCE: "SOURCE",
            ActorType.PROCESSOR: "PROCESSOR",
            ActorType.SINK: "SINK",
            ActorType.ROUTING: "ROUTER",
            ActorType.COLLECTOR: "COLLECTOR"
        }
        type_label = type_labels.get(actor.type, str(actor.type).upper())
        type_id = self.create_text(x, type_y, text=type_label, 
                        fill="white", font=("Arial", 10, "bold"), tags=f"actor_{actor.name}")
        self.tag_bind(type_id, "<Button-1>", self.on_click)
        
        # Name
        name_y = y1 + 35
        name_id = self.create_text(x, name_y, text=actor.name, 
                        fill="white", font=("Arial", 9), tags=f"actor_{actor.name}")
        self.tag_bind(name_id, "<Button-1>", self.on_click)
        
        # Host (if specified)
        if actor.host:
            host_y = y1 + 55
            host_id = self.create_text(x, host_y, text=f"@{actor.host}", 
                            fill="white", font=("Arial", 7), tags=f"actor_{actor.name}")
            self.tag_bind(host_id, "<Button-1>", self.on_click)
        
        # Right-click context menu
        self.tag_bind(f"actor_{actor.name}", "<Button-3>", lambda e, a=actor: self.show_actor_context_menu(e, a))
    
    def draw_connections(self):
        """Draw all connections between actors."""
        self.delete("connection")
        self.connection_items.clear()
        for actor in self.app.graph.actors.values():
            if hasattr(actor, 'type') and getattr(actor, 'type', None) == ActorType.ROUTING and getattr(actor, 'targets', None):
                for target_name in actor.targets:
                    if target_name in self.actor_positions:
                        self._draw_connection_with_menu(actor.name, target_name)
            else:
                for conn in self.app.graph.connections:
                    self._draw_connection_with_menu(conn.source_actor, conn.target_actor)
        # Raise all connection lines above actors
        self.tag_raise("connection")
    
    def _draw_connection_with_menu(self, source_name, target_name):
        if source_name in self.actor_positions and target_name in self.actor_positions:
            x1, y1 = self.actor_positions[source_name]
            x2, y2 = self.actor_positions[target_name]
            # Draw thick invisible line for hit area
            hit_id = self.create_line(x1, y1, x2, y2, arrow=tk.LAST, fill="", width=8, stipple="gray25", tags="connection")
            # Draw visible line on top
            line_id = self.create_line(x1, y1, x2, y2, arrow=tk.LAST, fill="gray", width=2, tags="connection")
            self.connection_items[hit_id] = (source_name, target_name)
            def on_right_click_connection(e, s=source_name, t=target_name):
                self.show_connection_context_menu(e, s, t)
                return "break"
            self.tag_bind(hit_id, "<Button-3>", on_right_click_connection)
    
    def draw_connection_mode_indicator(self):
        """Draw indicator when in connection mode."""
        if self.connection_mode:
            self.create_text(10, 10, text="Connection Mode - Click source actor", 
                           fill="red", font=("Arial", 10, "bold"), anchor=tk.NW)
    
    def get_actor_color(self, actor_type: ActorType, selected=False) -> str:
        """Get color for actor type, with special highlight if selected."""
        # Main colors
        colors = {
            ActorType.SOURCE: "#4CAF50",      # Green
            ActorType.PROCESSOR: "#2196F3",   # Blue
            ActorType.SINK: "#FF9800",        # Orange
            ActorType.ROUTING: "#9C27B0",     # Purple
            ActorType.COLLECTOR: "#009688"    # Teal
        }
        # Highlight colors
        highlight_colors = {
            ActorType.SOURCE: "#388E3C",      # Darker green
            ActorType.PROCESSOR: "#1565C0",   # Darker blue
            ActorType.SINK: "#F57C00",        # Darker orange
            ActorType.ROUTING: "#6A1B9A",     # Darker purple
            ActorType.COLLECTOR: "#00695C"    # Darker teal
        }
        if selected:
            return highlight_colors.get(actor_type, "#333333")
        return colors.get(actor_type, self.default_color)
    
    def calculate_actor_position(self, actor: Actor) -> tuple:
        """Calculate position for a new actor."""
        # Simple grid layout
        actors = list(self.app.graph.actors.values())
        index = actors.index(actor)
        
        cols = 3
        row = index // cols
        col = index % cols
        
        x = 150 + col * self.actor_spacing
        y = 100 + row * self.actor_spacing
        
        # Save position back to actor object
        actor.x = x
        actor.y = y
        
        return (x, y)
    
    def on_click(self, event):
        """Handle mouse click."""
        # Convert to canvas coordinates
        x = self.canvasx(event.x)
        y = self.canvasy(event.y)
        
        # Check if clicked on an actor
        actor_name = self.find_actor_at_position(x, y)
        
        if actor_name:
            if self.connection_mode:
                self.handle_connection_click(actor_name)
            else:
                self.select_actor(actor_name)
        else:
            # Clicked on empty space
            if self.connection_mode:
                self.cancel_connection_mode()
            else:
                self.clear_selection()
    
    def on_key(self, event):
        """Handle keyboard input."""
        if event.char.lower() == 'c':
            self.start_connection_mode()
        elif event.char.lower() == 'escape':
            self.cancel_connection_mode()
    
    def on_delete(self, event):
        """Handle delete key."""
        if self.selected_actor:
            self.delete_selected_actor()
    
    def find_actor_at_position(self, x: float, y: float) -> str:
        """Find actor at given position."""
        for actor in self.app.graph.actors.values():
            if actor.name in self.actor_positions:
                ax, ay = self.actor_positions[actor.name]
                if (abs(x - ax) <= self.actor_width // 2 and 
                    abs(y - ay) <= self.actor_height // 2):
                    return actor.name
        return None
    
    def select_actor(self, actor_name: str):
        """Select an actor."""
        self.selected_actor = actor_name
        self.app.selected_actor = actor_name
        self.app.property_panel.select_actor(actor_name)
        self.update_display()
    
    def clear_selection(self):
        """Clear current selection."""
        self.selected_actor = None
        self.app.selected_actor = None
        self.update_display()
    
    def start_connection_mode(self):
        """Start connection mode."""
        self.connection_mode = True
        self.connection_source = None
        self.app.set_status("Connection mode: Click source actor")
        self.update_display()
    
    def cancel_connection_mode(self):
        """Cancel connection mode."""
        self.connection_mode = False
        self.connection_source = None
        self.app.set_status("Connection mode cancelled")
        self.update_display()
    
    def handle_connection_click(self, actor_name: str):
        """Handle click during connection mode."""
        if not self.connection_source:
            # First click - select source
            self.connection_source = actor_name
            self.app.set_status(f"Connection mode: Click target actor (from {actor_name})")
        else:
            # Second click - create connection
            if self.connection_source != actor_name:
                self.create_connection(self.connection_source, actor_name)
            self.cancel_connection_mode()
    
    def create_connection(self, source: str, target: str):
        try:
            source_actor = self.app.graph.get_actor_by_name(source)
            target_actor = self.app.graph.get_actor_by_name(target)
            if not source_actor or not target_actor:
                raise Exception(f"Source or target actor not found: {source}, {target}")

            from core.model import ActorType

            # --- Auto-convert processor to collector if multiple inputs ---
            if target_actor.type == ActorType.PROCESSOR:
                input_count = sum(1 for conn in self.app.graph.connections if conn.target_actor == target)
                if input_count >= 1:
                    target_actor.type = ActorType.COLLECTOR
                    self.app.set_status(f"Actor '{target}' converted to collector due to multiple inputs.")
                    self.app.property_panel.select_actor(target)
            if target_actor.type == ActorType.COLLECTOR:
                if target_actor.input_topic:
                    source_actor.output_topic = target_actor.input_topic
                for conn in self.app.graph.connections:
                    if conn.target_actor == target:
                        proc = self.app.graph.get_actor_by_name(conn.source_actor)
                        if proc and proc.type == ActorType.PROCESSOR:
                            proc.output_topic = target_actor.input_topic
            elif source_actor.output_topic:
                target_actor.input_topic = source_actor.output_topic
            if source_actor.type == ActorType.ROUTING:
                if not source_actor.output_topic:
                    source_actor.output_topic = f"data:{source_actor.name}:routed"
                for tname in source_actor.targets:
                    tactor = self.app.graph.get_actor_by_name(tname)
                    if tactor:
                        tactor.input_topic = source_actor.output_topic
                        # Ensure processors have unique output topics
                        if tactor.type == ActorType.PROCESSOR:
                            tactor.output_topic = f"data:events:{tactor.name}_output"
                target_actor.input_topic = source_actor.output_topic
                # Ensure target processor has unique output topic
                if target_actor.type == ActorType.PROCESSOR:
                    target_actor.output_topic = f"data:events:{target_actor.name}_output"
            if source_actor.type == ActorType.PROCESSOR:
                current_targets = [conn.target_actor for conn in self.app.graph.connections if conn.source_actor == source]
                if len(current_targets) == 1 and current_targets[0] != target:
                    source_actor.type = ActorType.ROUTING
                    source_actor.targets = [current_targets[0], target]
                    self.app.graph.connections = {conn for conn in self.app.graph.connections if conn.source_actor != source}
                    if not source_actor.output_topic:
                        source_actor.output_topic = f"data:{source_actor.name}:routed"
                    for tname in source_actor.targets:
                        tactor = self.app.graph.get_actor_by_name(tname)
                        if tactor:
                            tactor.input_topic = source_actor.output_topic
                            # Ensure processors have unique output topics
                            if tactor.type == ActorType.PROCESSOR:
                                tactor.output_topic = f"data:events:{tactor.name}_output"
                    self.app.set_status(f"Actor '{source}' converted to routing with targets: {source_actor.targets}")
                    self.app.property_panel.select_actor(source)
                    self.app.update_ui()
                    return
            if source_actor.type == ActorType.ROUTING:
                if target not in source_actor.targets:
                    source_actor.targets.append(target)
                    if source_actor.output_topic:
                        target_actor.input_topic = source_actor.output_topic
                        # Ensure target processor has unique output topic
                        if target_actor.type == ActorType.PROCESSOR:
                            target_actor.output_topic = f"data:events:{target_actor.name}_output"
                    self.app.set_status(f"Added target '{target}' to routing actor '{source}'")
                    self.app.property_panel.select_actor(source)
                    self.app.update_ui()
                    return
            connection = Connection(source_actor=source, target_actor=target)
            errors = self.app.graph.add_connection(connection)
            if errors:
                error_msg = "\n".join(errors)
                messagebox.showerror("Connection Error", f"Cannot create connection:\n\n{error_msg}")
            else:
                self.app.update_ui()
                self.app.set_status(f"Connected {source} → {target}")
        except Exception as e:
            messagebox.showerror("Error", f"Failed to create connection:\n{str(e)}")
    
    def delete_selected_actor(self):
        """Delete the selected actor."""
        if self.selected_actor:
            if messagebox.askyesno("Delete Actor", f"Delete actor '{self.selected_actor}'?"):
                self.app.graph.remove_actor(self.selected_actor)
                if self.selected_actor in self.actor_positions:
                    del self.actor_positions[self.selected_actor]
                self.selected_actor = None
                self.app.update_ui()
                self.app.set_status(f"Deleted actor: {self.selected_actor}")
    
    def show_actor_context_menu(self, event, actor: Actor):
        """Show context menu for an actor."""
        menu = tk.Menu(self, tearoff=0)
        
        menu.add_command(label=f"Edit {actor.name}", 
                        command=lambda: self.app.property_panel.select_actor(actor.name))
        menu.add_separator()
        menu.add_command(label="Start Connection", 
                        command=self.start_connection_mode)
        menu.add_command(label="Delete All Outgoing Connections", 
                        command=lambda: self.delete_all_outgoing_connections(actor.name))
        menu.add_separator()
        menu.add_command(label="Delete", 
                        command=lambda: self.delete_actor(actor.name))
        
        menu.tk_popup(event.x_root, event.y_root)
    
    def show_canvas_context_menu(self, event):
        """Show context menu for canvas."""
        menu = tk.Menu(self, tearoff=0)
        
        menu.add_command(label="Add Actor", 
                        command=self.app.add_actor_dialog)
        menu.add_separator()
        menu.add_command(label="Start Connection", 
                        command=self.start_connection_mode)
        menu.add_command(label="Clear Selection", 
                        command=self.clear_selection)
        
        menu.tk_popup(event.x_root, event.y_root)
    
    def delete_actor(self, actor_name: str):
        """Delete an actor."""
        if messagebox.askyesno("Delete Actor", f"Delete actor '{actor_name}'?"):
            self.app.graph.remove_actor(actor_name)
            if actor_name in self.actor_positions:
                del self.actor_positions[actor_name]
            if self.selected_actor == actor_name:
                self.selected_actor = None
            self.app.update_ui()
            self.app.set_status(f"Deleted actor: {actor_name}")
    
    def start_drag_from_palette(self, actor_type: ActorType, event):
        """Start drag from palette."""
        # This would be implemented for drag-and-drop from palette
        pass
    
    def end_drag_from_palette(self):
        """End drag from palette."""
        # This would be implemented for drag-and-drop from palette
        pass
    
    def zoom_in(self):
        """Zoom in on the canvas."""
        self.zoom_level = min(self.zoom_level * 1.2, 3.0)
        self.scale("all", 0, 0, self.zoom_level, self.zoom_level)
    
    def zoom_out(self):
        """Zoom out on the canvas."""
        self.zoom_level = max(self.zoom_level / 1.2, 0.3)
        self.scale("all", 0, 0, self.zoom_level, self.zoom_level)
    
    def reset_zoom(self):
        """Reset zoom level."""
        self.zoom_level = 1.0
        self.scale("all", 0, 0, 1.0, 1.0)
    
    def update_actor_position_on_rename(self, old_name, new_name):
        """Preserve actor position when renaming."""
        if old_name in self.actor_positions:
            self.actor_positions[new_name] = self.actor_positions.pop(old_name)
    
    def on_mouse_press(self, event):
        # Only start dragging if not in connection mode and click is on an actor
        if self.connection_mode:
            return
        x = self.canvasx(event.x)
        y = self.canvasy(event.y)
        actor_name = self.find_actor_at_position(x, y)
        if actor_name:
            self.drag_actor = actor_name
            ax, ay = self.actor_positions[actor_name]
            self.drag_offset = (x - ax, y - ay)
        else:
            self.drag_actor = None
            self.drag_offset = (0, 0)

    def on_mouse_drag(self, event):
        if self.connection_mode:
            return
        if self.drag_actor:
            x = self.canvasx(event.x)
            y = self.canvasy(event.y)
            dx, dy = self.drag_offset
            new_pos = (x - dx, y - dy)
            self.actor_positions[self.drag_actor] = new_pos
            
            # Update position in actor object
            actor = self.app.graph.get_actor_by_name(self.drag_actor)
            if actor:
                actor.x, actor.y = new_pos
            
            self.update_display()

    def on_mouse_release(self, event):
        if self.connection_mode:
            return
        self.drag_actor = None
        self.drag_offset = (0, 0)

    def on_right_click(self, event):
        x = self.canvasx(event.x)
        y = self.canvasy(event.y)
        actor_name = self.find_actor_at_position(x, y)
        if actor_name:
            actor = self.app.graph.get_actor_by_name(actor_name)
            self.show_actor_context_menu(event, actor)
        else:
            self.show_canvas_context_menu(event)

    def show_connection_context_menu(self, event, source, target):
        menu = tk.Menu(self, tearoff=0)
        menu.add_command(label="Delete Connection", command=lambda: self.delete_connection(source, target))
        menu.tk_popup(event.x_root, event.y_root)

    def delete_connection(self, source, target):
        # Remove from graph.connections
        self.app.graph.connections = {conn for conn in self.app.graph.connections if not (conn.source_actor == source and conn.target_actor == target)}
        # If routing, also update targets
        actor = self.app.graph.get_actor_by_name(source)
        if actor and hasattr(actor, 'targets') and target in getattr(actor, 'targets', []):
            actor.targets = [t for t in actor.targets if t != target]
        self.update_display()
        self.app.set_status(f"Deleted connection: {source} → {target}")

    def delete_all_outgoing_connections(self, actor_name: str):
        """Delete all outgoing connections from an actor."""
        if messagebox.askyesno("Delete Connections", f"Delete all outgoing connections from '{actor_name}'?"):
            # Remove from graph.connections
            self.app.graph.connections = {conn for conn in self.app.graph.connections if conn.source_actor != actor_name}
            # If routing, also clear targets
            actor = self.app.graph.get_actor_by_name(actor_name)
            if actor and hasattr(actor, 'targets'):
                actor.targets = []
            self.update_display()
            self.app.set_status(f"Deleted all outgoing connections from: {actor_name}") 