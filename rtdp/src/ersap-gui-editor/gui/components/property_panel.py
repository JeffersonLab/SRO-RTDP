"""
Property Panel Component

This module provides the property panel for viewing and editing
actor properties.
"""

import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext
from typing import Dict, Optional
from core.model import Actor, ActorType


class PropertyPanel(ttk.Frame):
    """Property panel for editing actor properties."""
    
    def __init__(self, parent, app):
        super().__init__(parent)
        self.app = app
        self.current_actor: Optional[Actor] = None
        self.property_widgets = {}
        
        self.setup_ui()
    
    def setup_ui(self):
        """Setup the property panel UI."""
        # Title
        title_label = ttk.Label(self, text="Properties", font=("Arial", 12, "bold"))
        title_label.pack(pady=(0, 10))
        
        # Create property fields
        self.create_property_widgets()
        
        # Parameters section
        self.create_parameters_section()
        
        # Buttons
        self.create_buttons()
        
        # Show no selection initially
        self.show_no_selection()
    
    def create_property_widgets(self):
        """Create widgets for actor properties."""
        # Name field
        self.create_property_field("Name:", "name", "entry")
        
        # Type field
        self.create_property_field("Type:", "type", "combobox", 
                                 values=["processor", "collector", "router"])
        
        # Input topic field
        self.create_property_field("Input Topic:", "input_topic", "entry")
        
        # Output topic field
        self.create_property_field("Output Topic:", "output_topic", "entry")
        
        # Host field
        self.create_property_field("Host:", "host", "entry")
        
        # Add targets field for routing actors
        self.targets_listbox = None
    
    def create_property_field(self, label: str, property_name: str, field_type: str, **kwargs):
        """Create a property field with label and input widget."""
        frame = ttk.Frame(self)
        frame.pack(fill=tk.X, pady=2)
        
        # Label
        label_widget = ttk.Label(frame, text=label, width=12)
        label_widget.pack(side=tk.LEFT)
        
        # Input widget
        if field_type == "entry":
            widget = ttk.Entry(frame)
            widget.pack(side=tk.LEFT, fill=tk.X, expand=True)
        elif field_type == "combobox":
            widget = ttk.Combobox(frame, state="readonly", **kwargs)
            widget.pack(side=tk.LEFT, fill=tk.X, expand=True)
        else:
            widget = ttk.Entry(frame)
            widget.pack(side=tk.LEFT, fill=tk.X, expand=True)
        
        # Store widget reference
        self.property_widgets[property_name] = widget
        
        # Bind change events
        if hasattr(widget, 'bind'):
            widget.bind('<KeyRelease>', self.on_property_change)
            if field_type == "combobox":
                widget.bind('<<ComboboxSelected>>', self.on_property_change)
    
    def create_parameters_section(self):
        """Create the parameters section."""
        # Separator
        ttk.Separator(self, orient=tk.HORIZONTAL).pack(fill=tk.X, pady=10)
        
        # Parameters label
        params_label = ttk.Label(self, text="Parameters", font=("Arial", 10, "bold"))
        params_label.pack(pady=(0, 5))
        
        # Parameters text area
        self.params_text = scrolledtext.ScrolledText(self, height=6, width=25)
        self.params_text.pack(fill=tk.BOTH, expand=True, pady=(0, 5))
        self.params_text.bind('<KeyRelease>', self.on_parameters_change)
        
        # Add parameter button
        add_param_btn = ttk.Button(self, text="Add Parameter", command=self.add_parameter_dialog)
        add_param_btn.pack(fill=tk.X, pady=2)
    
    def create_buttons(self):
        """Create action buttons."""
        # Separator
        ttk.Separator(self, orient=tk.HORIZONTAL).pack(fill=tk.X, pady=10)
        
        # Button frame
        button_frame = ttk.Frame(self)
        button_frame.pack(fill=tk.X, pady=5)
        
        # Apply changes button
        apply_btn = ttk.Button(button_frame, text="Apply Changes", command=self.apply_changes)
        apply_btn.pack(fill=tk.X, pady=2)
        
        # Reset button
        reset_btn = ttk.Button(button_frame, text="Reset", command=self.reset_properties)
        reset_btn.pack(fill=tk.X, pady=2)
        
        # Delete outgoing connections button
        delete_connections_btn = ttk.Button(button_frame, text="Delete Outgoing Connections", command=self.delete_outgoing_connections)
        delete_connections_btn.pack(fill=tk.X, pady=2)
        
        # Delete button
        delete_btn = ttk.Button(button_frame, text="Delete Actor", command=self.delete_actor)
        delete_btn.pack(fill=tk.X, pady=2)
    
    def update_display(self):
        """Update the property panel display."""
        if self.current_actor:
            self.show_actor_properties(self.current_actor)
    
    def select_actor(self, actor_name: str):
        """Select an actor to edit."""
        actor = self.app.graph.get_actor_by_name(actor_name)
        if actor:
            self.current_actor = actor
            self.show_actor_properties(actor)
    
    def show_actor_properties(self, actor: Actor):
        """Show properties for the given actor."""
        # Set values in widgets
        self.property_widgets["name"].delete(0, tk.END)
        self.property_widgets["name"].insert(0, actor.name)
        
        # Map internal type to dropdown value (force lowercase for display)
        type_map = {
            ActorType.SOURCE: "source",
            ActorType.PROCESSOR: "processor",
            ActorType.COLLECTOR: "collector",
            ActorType.ROUTING: "router",
            ActorType.SINK: "sink"
        }
        display_type = type_map.get(actor.type, "processor")
        self.property_widgets["type"].set(display_type)
        
        # Disable dropdown for source/sink, enable for others
        if actor.type in (ActorType.SOURCE, ActorType.SINK):
            self.property_widgets["type"].config(state="disabled")
        else:
            self.property_widgets["type"].config(state="readonly")
        
        self.property_widgets["input_topic"].delete(0, tk.END)
        if actor.input_topic:
            self.property_widgets["input_topic"].insert(0, actor.input_topic)
        
        self.property_widgets["output_topic"].delete(0, tk.END)
        if actor.output_topic:
            self.property_widgets["output_topic"].insert(0, actor.output_topic)
        
        self.property_widgets["host"].delete(0, tk.END)
        if actor.host:
            self.property_widgets["host"].insert(0, actor.host)
        
        # Set parameters
        self.params_text.delete("1.0", tk.END)
        for key, value in actor.parameters.items():
            self.params_text.insert(tk.END, f"{key}: {value}\n")
        
        # Update field states based on actor type
        self.update_field_states(actor.type)
        
        # Enable all fields
        for widget in self.property_widgets.values():
            widget.config(state="normal")
        
        # Show targets for routing actors
        if actor.type == ActorType.ROUTING:
            if not self.targets_listbox:
                self.targets_listbox = tk.Listbox(self, selectmode=tk.MULTIPLE, exportselection=0, height=5)
                self.targets_listbox.pack(fill=tk.X, pady=2)
            self.targets_listbox.delete(0, tk.END)
            for other_actor in self.app.graph.actors.values():
                if other_actor.name != actor.name:
                    self.targets_listbox.insert(tk.END, other_actor.name)
            # Select current targets
            for idx, name in enumerate(self.targets_listbox.get(0, tk.END)):
                if name in actor.targets:
                    self.targets_listbox.selection_set(idx)
            self.targets_listbox.lift()
        elif self.targets_listbox:
            self.targets_listbox.pack_forget()
    
    def show_no_selection(self):
        """Show the panel when no actor is selected."""
        # Clear all fields
        for widget in self.property_widgets.values():
            if hasattr(widget, 'delete'):
                try:
                    widget.delete(0, tk.END)
                except tk.TclError:
                    pass
            elif hasattr(widget, 'set'):
                try:
                    widget.set('')
                except tk.TclError:
                    pass
        # Safely clear parameters text
        if hasattr(self, 'params_text') and self.params_text:
            try:
                self.params_text.delete("1.0", tk.END)
            except tk.TclError:
                pass
        # Disable all fields
        for widget in self.property_widgets.values():
            try:
                widget.config(state="disabled")
            except tk.TclError:
                pass
        self.current_actor = None
    
    def update_field_states(self, actor_type: ActorType):
        """Update field states based on actor type."""
        # Enable all fields first
        for widget in self.property_widgets.values():
            widget.config(state="normal")
        
        # Disable fields based on type
        if actor_type == ActorType.SOURCE:
            self.property_widgets["input_topic"].config(state="disabled")
        elif actor_type == ActorType.SINK:
            self.property_widgets["output_topic"].config(state="disabled")
        # For collector, both input and output topics are editable
    
    def on_property_change(self, event=None):
        """Handle property field changes."""
        if self.current_actor:
            # Mark that changes are pending
            self.app.set_status("Properties modified - click 'Apply Changes' to save")
    
    def on_parameters_change(self, event=None):
        """Handle parameters text change."""
        if self.current_actor:
            self.app.set_status("Parameters modified - click 'Apply Changes' to save")
    
    def apply_changes(self):
        """Apply changes to the current actor."""
        if not self.current_actor:
            return
        try:
            old_name = self.current_actor.name
            name = self.property_widgets["name"].get().strip()
            # Map dropdown value back to ActorType
            type_str = self.property_widgets["type"].get()
            type_map = {
                "processor": ActorType.PROCESSOR,
                "collector": ActorType.COLLECTOR,
                "router": ActorType.ROUTING
            }
            actor_type = type_map.get(type_str, ActorType.PROCESSOR)
            input_topic = self.property_widgets["input_topic"].get().strip() or None
            output_topic = self.property_widgets["output_topic"].get().strip() or None
            host = self.property_widgets["host"].get().strip() or None
            params_text = self.params_text.get("1.0", tk.END).strip()
            parameters = {}
            if params_text:
                for line in params_text.split('\n'):
                    if ':' in line:
                        key, value = line.split(':', 1)
                        parameters[key.strip()] = value.strip()
            if actor_type == ActorType.ROUTING and self.targets_listbox:
                selected_indices = self.targets_listbox.curselection()
                targets = [self.targets_listbox.get(i) for i in selected_indices]
            else:
                targets = []
            graph = self.app.graph
            if name != old_name:
                if name in graph.actors:
                    messagebox.showerror("Validation Error", f"An actor with the name '{name}' already exists.")
                    return
                self.current_actor.name = name
                graph.actors[name] = graph.actors.pop(old_name)
                self.app.canvas.update_actor_position_on_rename(old_name, name)
                for conn in graph.connections:
                    if conn.source_actor == old_name:
                        conn.source_actor = name
                    if conn.target_actor == old_name:
                        conn.target_actor = name
                for actor in graph.actors.values():
                    if hasattr(actor, 'targets') and actor.targets:
                        actor.targets = [name if t == old_name else t for t in actor.targets]
                if hasattr(self.current_actor, 'targets') and self.current_actor.targets:
                    self.current_actor.targets = [name if t == old_name else t for t in self.current_actor.targets]
                self.app.selected_actor = name
            self.current_actor.type = actor_type
            self.current_actor.input_topic = input_topic
            self.current_actor.output_topic = output_topic
            self.current_actor.host = host
            self.current_actor.parameters = parameters
            self.current_actor.targets = targets
            errors = self.current_actor.validate()
            if errors:
                error_msg = "\n".join(errors)
                messagebox.showerror("Validation Error", f"Cannot apply changes:\n\n{error_msg}")
                return
            self.app.update_ui()
            self.app.set_status(f"Applied changes to actor: {name}")
            self.select_actor(name)
        except Exception as e:
            messagebox.showerror("Error", f"Failed to apply changes:\n{str(e)}")
    
    def reset_properties(self):
        """Reset properties to current actor values."""
        if self.current_actor:
            self.show_actor_properties(self.current_actor)
            self.app.set_status("Properties reset")
    
    def delete_actor(self):
        """Delete the current actor."""
        if self.current_actor:
            actor_name = self.current_actor.name  # Store name before deletion
            if messagebox.askyesno("Delete Actor", f"Delete actor '{actor_name}'?"):
                self.app.graph.remove_actor(actor_name)
                self.current_actor = None
                self.app.update_ui()
                self.show_no_selection()
                self.app.set_status(f"Deleted actor: {actor_name}")
    
    def add_parameter_dialog(self):
        """Show dialog to add a parameter."""
        if not self.current_actor:
            messagebox.showwarning("No Selection", "Please select an actor first")
            return
        
        dialog = AddParameterDialog(self, self.current_actor)
        self.app.root.wait_window(dialog.dialog)
    
    def add_parameter(self, key: str, value: str):
        """Add a parameter to the current actor."""
        if self.current_actor:
            self.current_actor.parameters[key] = value
            self.show_actor_properties(self.current_actor)
            self.app.set_status(f"Added parameter: {key}")
    
    def on_type_change(self, event=None):
        """Handle actor type change in the property panel."""
        actor_type = ActorType(self.property_widgets["type"].get())
        # If switching to routing, show targets UI and initialize targets if needed
        if actor_type == ActorType.ROUTING:
            if not hasattr(self, 'targets_listbox') or self.targets_listbox is None:
                self.targets_listbox = tk.Listbox(self, selectmode=tk.MULTIPLE, exportselection=0, height=5)
                self.targets_listbox.pack(fill=tk.X, pady=2)
            # Populate listbox with available actors
            self.targets_listbox.delete(0, tk.END)
            for other_actor in self.app.graph.actors.values():
                if self.current_actor and other_actor.name != self.current_actor.name:
                    self.targets_listbox.insert(tk.END, other_actor.name)
            # Select current targets if any
            if self.current_actor and hasattr(self.current_actor, 'targets'):
                for idx, name in enumerate(self.targets_listbox.get(0, tk.END)):
                    if name in self.current_actor.targets:
                        self.targets_listbox.selection_set(idx)
            self.targets_listbox.lift()
        else:
            # If switching away from routing, remove targets UI and clear targets
            if hasattr(self, 'targets_listbox') and self.targets_listbox:
                self.targets_listbox.pack_forget()
            if self.current_actor and hasattr(self.current_actor, 'targets'):
                self.current_actor.targets = []
            # Remove all outgoing connections if switching to sink or source
            if actor_type in [ActorType.SINK, ActorType.SOURCE]:
                self.app.graph.connections = [conn for conn in self.app.graph.connections if conn.source_actor != (self.current_actor.name if self.current_actor else None)]
        # Synchronize topics if needed
        if self.current_actor:
            if actor_type == ActorType.ROUTING:
                if not self.current_actor.output_topic:
                    self.current_actor.output_topic = f"data:{self.current_actor.name}:routed"
                for tname in getattr(self.current_actor, 'targets', []):
                    tactor = self.app.graph.get_actor_by_name(tname)
                    if tactor:
                        tactor.input_topic = self.current_actor.output_topic
            elif actor_type == ActorType.PROCESSOR:
                # If switching from routing to processor, keep only one output connection
                if hasattr(self.current_actor, 'targets') and self.current_actor.targets:
                    if self.current_actor.targets:
                        # Keep only the first target as output
                        first_target = self.current_actor.targets[0]
                        self.current_actor.targets = []
                        # Set output topic and update target's input
                        if self.current_actor.output_topic:
                            tactor = self.app.graph.get_actor_by_name(first_target)
                            if tactor:
                                tactor.input_topic = self.current_actor.output_topic
        self.update_field_states(actor_type)
        self.app.set_status(f"Type changed to {actor_type.value}")

    def delete_outgoing_connections(self):
        """Delete all outgoing connections from the current actor."""
        if self.current_actor:
            actor_name = self.current_actor.name
            if messagebox.askyesno("Delete Connections", f"Delete all outgoing connections from '{actor_name}'?"):
                # Remove from graph.connections
                self.app.graph.connections = {conn for conn in self.app.graph.connections if conn.source_actor != actor_name}
                # If routing, also clear targets
                if hasattr(self.current_actor, 'targets'):
                    self.current_actor.targets = []
                self.app.update_ui()
                self.app.set_status(f"Deleted all outgoing connections from: {actor_name}")
        else:
            messagebox.showwarning("No Selection", "Please select an actor first")


class AddParameterDialog:
    """Dialog for adding a parameter."""
    
    def __init__(self, parent: PropertyPanel, actor: Actor):
        self.parent = parent
        self.actor = actor
        
        self.dialog = tk.Toplevel(parent.app.root)
        self.dialog.title("Add Parameter")
        self.dialog.geometry("300x150")
        self.dialog.transient(parent.app.root)
        self.dialog.grab_set()
        
        self.setup_ui()
        self.center_dialog()
    
    def setup_ui(self):
        """Setup the dialog UI."""
        main_frame = ttk.Frame(self.dialog, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Parameter key
        ttk.Label(main_frame, text="Parameter Key:").grid(row=0, column=0, sticky=tk.W, pady=5)
        self.key_var = tk.StringVar()
        ttk.Entry(main_frame, textvariable=self.key_var).grid(row=0, column=1, sticky=tk.EW, pady=5)
        
        # Parameter value
        ttk.Label(main_frame, text="Parameter Value:").grid(row=1, column=0, sticky=tk.W, pady=5)
        self.value_var = tk.StringVar()
        ttk.Entry(main_frame, textvariable=self.value_var).grid(row=1, column=1, sticky=tk.EW, pady=5)
        
        # Buttons
        button_frame = ttk.Frame(main_frame)
        button_frame.grid(row=2, column=0, columnspan=2, pady=20)
        
        ttk.Button(button_frame, text="Add", command=self.add_parameter).pack(side=tk.LEFT, padx=5)
        ttk.Button(button_frame, text="Cancel", command=self.dialog.destroy).pack(side=tk.LEFT, padx=5)
        
        # Configure grid weights
        main_frame.columnconfigure(1, weight=1)
    
    def center_dialog(self):
        """Center the dialog on the parent window."""
        self.dialog.update_idletasks()
        x = (self.dialog.winfo_screenwidth() // 2) - (300 // 2)
        y = (self.dialog.winfo_screenheight() // 2) - (150 // 2)
        self.dialog.geometry(f"300x150+{x}+{y}")
    
    def add_parameter(self):
        """Add the parameter."""
        key = self.key_var.get().strip()
        value = self.value_var.get().strip()
        
        if not key:
            messagebox.showwarning("Invalid Key", "Parameter key is required")
            return
        
        if key in self.actor.parameters:
            if not messagebox.askyesno("Parameter Exists", 
                                     f"Parameter '{key}' already exists. Overwrite?"):
                return
        
        self.parent.add_parameter(key, value)
        self.dialog.destroy() 