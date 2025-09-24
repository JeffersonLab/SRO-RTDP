"""
Main application for the ERSAP Actor Editor GUI.
"""

import tkinter as tk
from tkinter import ttk, messagebox, filedialog, scrolledtext
import sys
import os

# Add the current directory to the path so we can import our modules
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

try:
    from core.model import Actor, ActorType, Connection, ActorGraph
    from core.yaml_writer import YAMLWriter
    from core.yaml_reader import YAMLReader
    from gui.components.actor_palette import ActorPalette
    from gui.components.canvas import Canvas
    from gui.components.property_panel import PropertyPanel
except ImportError as e:
    print(f"Error importing GUI components: {e}")
    print("Make sure all required files are present.")
    sys.exit(1)


class ERSAPEditorApp:
    """Main application class for the ERSAP Actor Editor."""
    
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("ERSAP Actor Editor")
        self.root.geometry("1200x800")
        
        # Initialize data
        self.graph = ActorGraph()
        self.yaml_writer = YAMLWriter()
        self.yaml_reader = YAMLReader()
        self.selected_actor = None
        self.current_file = None
        
        # Setup UI
        self.setup_menu()
        self.setup_toolbar()
        self.setup_main_layout()
        self.setup_status_bar()
        self.setup_event_bindings()
        
        # Load example data
        self.load_example_data()
    
    def setup_menu(self):
        """Setup the main menu bar."""
        menubar = tk.Menu(self.root)
        self.root.config(menu=menubar)
        
        # File menu
        file_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="File", menu=file_menu)
        file_menu.add_command(label="New Project", command=self.new_project)
        file_menu.add_command(label="Open Project", command=self.open_project)
        file_menu.add_command(label="Open YAML", command=self.open_yaml)
        file_menu.add_command(label="Save Project", command=self.save_project)
        file_menu.add_command(label="Save Project As", command=self.save_project_as)
        file_menu.add_separator()
        file_menu.add_command(label="Export YAML", command=self.export_yaml)
        file_menu.add_separator()
        file_menu.add_command(label="Exit", command=self.root.quit)
        
        # Edit menu
        edit_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="Edit", menu=edit_menu)
        edit_menu.add_command(label="Add Actor", command=self.add_actor_dialog)
        edit_menu.add_command(label="Delete Selected", command=self.delete_selected)
        edit_menu.add_separator()
        edit_menu.add_command(label="Clear Project", command=self.clear_project)
        
        # View menu
        view_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="View", menu=view_menu)
        view_menu.add_command(label="Zoom In", command=self.zoom_in)
        view_menu.add_command(label="Zoom Out", command=self.zoom_out)
        view_menu.add_command(label="Reset Zoom", command=self.reset_zoom)
        
        # Tools menu
        tools_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="Tools", menu=tools_menu)
        tools_menu.add_command(label="Validate Graph", command=self.validate_graph)
        tools_menu.add_command(label="Show Example YAML", command=self.show_example_yaml)
        
        # Help menu
        help_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="Help", menu=help_menu)
        help_menu.add_command(label="About", command=self.show_about)
    
    def setup_toolbar(self):
        """Setup the toolbar."""
        toolbar = ttk.Frame(self.root)
        toolbar.pack(fill=tk.X, padx=5, pady=2)
        
        ttk.Button(toolbar, text="Add Actor", command=self.add_actor_dialog).pack(side=tk.LEFT, padx=2)
        ttk.Button(toolbar, text="Connect", command=self.start_connection_mode).pack(side=tk.LEFT, padx=2)
        ttk.Button(toolbar, text="Delete", command=self.delete_selected).pack(side=tk.LEFT, padx=2)
        
        ttk.Separator(toolbar, orient=tk.VERTICAL).pack(side=tk.LEFT, fill=tk.Y, padx=5)
        
        ttk.Button(toolbar, text="Validate", command=self.validate_graph).pack(side=tk.LEFT, padx=2)
        ttk.Button(toolbar, text="Export YAML", command=self.export_yaml).pack(side=tk.LEFT, padx=2)
        
        ttk.Separator(toolbar, orient=tk.VERTICAL).pack(side=tk.LEFT, fill=tk.Y, padx=5)
        
        self.actor_count_label = ttk.Label(toolbar, text="Actors: 0")
        self.actor_count_label.pack(side=tk.RIGHT, padx=5)
    
    def setup_main_layout(self):
        """Setup the main layout with canvas and panels."""
        main_frame = ttk.Frame(self.root)
        main_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        # Left panel - Actor Palette
        left_panel = ttk.Frame(main_frame, width=200)
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 5))
        left_panel.pack_propagate(False)
        
        self.actor_palette = ActorPalette(left_panel, self)
        self.actor_palette.pack(fill=tk.BOTH, expand=True)
        
        # Center panel - Canvas
        center_panel = ttk.Frame(main_frame)
        center_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        
        self.canvas = Canvas(center_panel, self)
        self.canvas.pack(fill=tk.BOTH, expand=True)
        
        # Right panel - Property Panel
        right_panel = ttk.Frame(main_frame, width=300)
        right_panel.pack(side=tk.RIGHT, fill=tk.Y, padx=(5, 0))
        right_panel.pack_propagate(False)
        
        self.property_panel = PropertyPanel(right_panel, self)
        self.property_panel.pack(fill=tk.BOTH, expand=True)
    
    def setup_status_bar(self):
        """Setup the status bar."""
        status_frame = ttk.Frame(self.root)
        status_frame.pack(fill=tk.X, side=tk.BOTTOM)
        
        self.status_label = ttk.Label(status_frame, text="Ready", relief=tk.SUNKEN)
        self.status_label.pack(fill=tk.X)
    
    def setup_event_bindings(self):
        """Setup keyboard shortcuts and other event bindings."""
        self.root.bind('<Control-n>', lambda e: self.new_project())
        self.root.bind('<Control-o>', lambda e: self.open_project())
        self.root.bind('<Control-s>', lambda e: self.save_project())
        self.root.bind('<Control-e>', lambda e: self.export_yaml())
        self.root.bind('<Delete>', lambda e: self.delete_selected())
        self.root.bind('c', lambda e: self.start_connection_mode())
    
    def load_example_data(self):
        """Load example data for demonstration."""
        try:
            # Create example actors
            source = Actor(
                name="data_source",
                type=ActorType.SOURCE,
                output_topic="raw_data",
                host="localhost",
                parameters={"frequency": "1000", "format": "binary"}
            )
            
            processor = Actor(
                name="data_processor",
                type=ActorType.PROCESSOR,
                input_topic="raw_data",
                output_topic="processed_data",
                host="localhost",
                parameters={"algorithm": "fft", "window_size": "1024"}
            )
            
            sink = Actor(
                name="data_sink",
                type=ActorType.SINK,
                input_topic="processed_data",
                host="localhost",
                parameters={"output_file": "results.dat"}
            )
            
            # Add actors to graph
            self.graph.add_actor(source)
            self.graph.add_actor(processor)
            self.graph.add_actor(sink)
            
            # Add connections
            self.graph.add_connection("data_source", "data_processor")
            self.graph.add_connection("data_processor", "data_sink")
            
            # Update UI
            self.update_ui()
            self.set_status("Example data loaded")
            
        except Exception as e:
            self.set_status(f"Error loading example data: {str(e)}")
    
    def new_project(self):
        """Create a new project."""
        if messagebox.askyesno("New Project", "Create a new project? This will clear all current data."):
            self.clear_project()
            self.current_file = None
            self.set_status("New project created")
    
    def clear_project(self):
        """Clear all data from the current project."""
        self.graph = ActorGraph()
        self.selected_actor = None
        self.update_ui()
        self.set_status("Project cleared")
    
    def open_project(self):
        """Open a project file."""
        file_path = filedialog.askopenfilename(
            title="Open Project",
            filetypes=[("ERSAP Project files", "*.ersap"), ("All files", "*.*")]
        )
        
        if file_path:
            try:
                project_data = self.yaml_writer.read_project_file(file_path)
                self.graph = project_data
                self.current_file = file_path
                self.update_ui()
                self.set_status(f"Project loaded: {os.path.basename(file_path)}")
            except Exception as e:
                messagebox.showerror("Error", f"Failed to open project:\n{str(e)}")
    
    def save_project(self):
        """Save the current project."""
        if self.current_file:
            self._save_project_to_file(self.current_file)
        else:
            self.save_project_as()
    
    def save_project_as(self):
        """Save the current project with a new name."""
        file_path = filedialog.asksaveasfilename(
            title="Save Project As",
            defaultextension=".ersap",
            filetypes=[("ERSAP Project files", "*.ersap"), ("All files", "*.*")]
        )
        
        if file_path:
            self._save_project_to_file(file_path)
    
    def _save_project_to_file(self, file_path: str):
        """Save project data to a file."""
        try:
            errors = self.yaml_writer.write_project_file(file_path, self.graph)
            if errors:
                messagebox.showerror("Error", f"Failed to save project:\n{chr(10).join(errors)}")
            else:
                self.current_file = file_path
                self.set_status(f"Project saved: {os.path.basename(file_path)}")
        except Exception as e:
            messagebox.showerror("Error", f"Failed to save project:\n{str(e)}")
    
    def export_yaml(self):
        """Export the current graph to YAML format."""
        file_path = filedialog.asksaveasfilename(
            title="Export YAML",
            defaultextension=".yml",
            filetypes=[("YAML files", "*.yml"), ("All files", "*.*")]
        )
        if file_path:
            try:
                errors = self.yaml_writer.write_services_yaml(self.graph, file_path)
                if errors:
                    messagebox.showerror("Error", f"Failed to export YAML:\n{chr(10).join(errors)}")
                else:
                    self.set_status(f"YAML exported: {os.path.basename(file_path)}")
            except Exception as e:
                messagebox.showerror("Error", f"Failed to export YAML:\n{str(e)}")
    
    def add_actor_dialog(self):
        """Show dialog to add a new actor."""
        dialog = AddActorDialog(self.root, self)
        self.root.wait_window(dialog.dialog)
    
    def delete_selected(self):
        """Delete the selected actor."""
        if self.selected_actor:
            self.property_panel.delete_actor()
        else:
            messagebox.showwarning("No Selection", "Please select an actor to delete")
    
    def validate_graph(self):
        """Validate the current graph."""
        errors = self.graph.get_validation_errors()
        if errors:
            error_msg = "\n".join(errors)
            messagebox.showerror("Validation Errors", f"Graph has validation errors:\n\n{error_msg}")
        else:
            messagebox.showinfo("Validation", "Graph is valid!")
    
    def zoom_in(self):
        """Zoom in on the canvas."""
        self.canvas.zoom_in()
    
    def zoom_out(self):
        """Zoom out on the canvas."""
        self.canvas.zoom_out()
    
    def reset_zoom(self):
        """Reset zoom on the canvas."""
        self.canvas.reset_zoom()
    
    def start_connection_mode(self):
        """Start connection mode for linking actors."""
        self.canvas.start_connection_mode()
    
    def show_about(self):
        """Show about dialog."""
        about_text = """ERSAP Actor Editor v1.0

A graphical tool for creating and configuring ERSAP actor workflows.

Features:
• Drag and drop actor creation
• Visual graph editing
• YAML export
• Project save/load
• Real-time validation

Built with Python and Tkinter."""
        
        messagebox.showinfo("About", about_text)
    
    def show_example_yaml(self):
        """Show example YAML output."""
        example_yaml = self.yaml_writer.generate_example_yaml()
        
        dialog = tk.Toplevel(self.root)
        dialog.title("Example YAML Output")
        dialog.geometry("600x400")
        
        text_widget = scrolledtext.ScrolledText(dialog, wrap=tk.WORD)
        text_widget.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        text_widget.insert(tk.END, example_yaml)
        text_widget.config(state=tk.DISABLED)
    
    def update_ui(self):
        """Update all UI components."""
        self.canvas.update_display()
        self.property_panel.update_display()
        self.actor_count_label.config(text=f"Actors: {len(self.graph.actors)}")
    
    def set_status(self, message: str):
        """Set status bar message."""
        self.status_label.config(text=message)
    
    def select_actor(self, actor_name: str):
        """Select an actor in the UI."""
        self.selected_actor = actor_name
        self.property_panel.select_actor(actor_name)
        self.canvas.select_actor(actor_name)
    
    def open_yaml(self):
        """Open a YAML file and display it on the graph."""
        file_path = filedialog.askopenfilename(
            title="Open YAML",
            filetypes=[("YAML files", "*.yml"), ("YAML files", "*.yaml"), ("All files", "*.*")]
        )
        
        if file_path:
            try:
                # Clear current graph
                self.graph = ActorGraph()
                self.selected_actor = None
                
                # Load YAML and create graph
                self.graph = self.yaml_reader.read_yaml_file(file_path)
                
                # Update UI
                self.update_ui()
                self.set_status(f"YAML loaded: {os.path.basename(file_path)}")
                
            except Exception as e:
                messagebox.showerror("Error", f"Failed to open YAML file:\n{str(e)}")


class AddActorDialog:
    """Dialog for adding a new actor."""
    
    def __init__(self, parent: tk.Tk, app: ERSAPEditorApp):
        self.parent = parent
        self.app = app
        
        self.dialog = tk.Toplevel(parent)
        self.dialog.title("Add Actor")
        self.dialog.geometry("400x500")
        self.dialog.transient(parent)
        self.dialog.grab_set()
        
        self.setup_ui()
        self.center_dialog()
    
    def setup_ui(self):
        """Setup the dialog UI."""
        main_frame = ttk.Frame(self.dialog, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Actor type
        ttk.Label(main_frame, text="Actor Type:").grid(row=0, column=0, sticky=tk.W, pady=5)
        self.type_var = tk.StringVar(value="source")
        type_combo = ttk.Combobox(main_frame, textvariable=self.type_var, 
                                 values=["source", "processor", "sink"], state="readonly")
        type_combo.grid(row=0, column=1, sticky=tk.EW, pady=5)
        
        # Name
        ttk.Label(main_frame, text="Name:").grid(row=1, column=0, sticky=tk.W, pady=5)
        self.name_var = tk.StringVar()
        name_entry = ttk.Entry(main_frame, textvariable=self.name_var)
        name_entry.grid(row=1, column=1, sticky=tk.EW, pady=5)
        name_entry.bind('<KeyRelease>', self.on_name_change)
        
        # Topic format help
        topic_help = ttk.Label(main_frame, text="Topic format: domain:subject:type (e.g., data:events:raw)", 
                              font=("Arial", 8), foreground="gray")
        topic_help.grid(row=2, column=0, columnspan=2, sticky=tk.W, pady=(5, 0))
        
        # Input topic
        ttk.Label(main_frame, text="Input Topic:").grid(row=3, column=0, sticky=tk.W, pady=5)
        self.input_var = tk.StringVar()
        self.input_entry = ttk.Entry(main_frame, textvariable=self.input_var)
        self.input_entry.grid(row=3, column=1, sticky=tk.EW, pady=5)
        
        # Output topic
        ttk.Label(main_frame, text="Output Topic:").grid(row=4, column=0, sticky=tk.W, pady=5)
        self.output_var = tk.StringVar()
        self.output_entry = ttk.Entry(main_frame, textvariable=self.output_var)
        self.output_entry.grid(row=4, column=1, sticky=tk.EW, pady=5)
        
        # Host
        ttk.Label(main_frame, text="Host:").grid(row=5, column=0, sticky=tk.W, pady=5)
        self.host_var = tk.StringVar(value="localhost")
        ttk.Entry(main_frame, textvariable=self.host_var).grid(row=5, column=1, sticky=tk.EW, pady=5)
        
        # Parameters
        ttk.Label(main_frame, text="Parameters:").grid(row=6, column=0, sticky=tk.W, pady=5)
        self.params_text = scrolledtext.ScrolledText(main_frame, height=6, width=30)
        self.params_text.grid(row=6, column=1, sticky=tk.EW, pady=5)
        
        # Buttons
        button_frame = ttk.Frame(main_frame)
        button_frame.grid(row=7, column=0, columnspan=2, pady=20)
        
        ttk.Button(button_frame, text="Add", command=self.add_actor).pack(side=tk.LEFT, padx=5)
        ttk.Button(button_frame, text="Cancel", command=self.dialog.destroy).pack(side=tk.LEFT, padx=5)
        
        # Configure grid weights
        main_frame.columnconfigure(1, weight=1)
        
        # Bind type change to update UI
        type_combo.bind("<<ComboboxSelected>>", self.on_type_change)
        self.on_type_change()
    
    def center_dialog(self):
        """Center the dialog on the parent window."""
        self.dialog.update_idletasks()
        x = (self.dialog.winfo_screenwidth() // 2) - (400 // 2)
        y = (self.dialog.winfo_screenheight() // 2) - (500 // 2)
        self.dialog.geometry(f"400x500+{x}+{y}")
    
    def on_type_change(self, event=None):
        """Handle actor type change."""
        actor_type = self.type_var.get()
        name = self.name_var.get().strip()
        
        if actor_type == "source":
            self.input_var.set("")
            self.input_entry.config(state="disabled")
            self.output_entry.config(state="normal")
            if name:
                self.output_var.set(f"data:events:{name}_output")
        elif actor_type == "processor":
            self.input_entry.config(state="normal")
            self.output_entry.config(state="normal")
            if name:
                self.input_var.set(f"data:events:{name}_input")
                self.output_var.set(f"data:events:{name}_output")
        elif actor_type == "sink":
            self.input_entry.config(state="normal")
            self.output_var.set("")
            self.output_entry.config(state="disabled")
            if name:
                self.input_var.set(f"data:events:{name}_input")
    
    def on_name_change(self, event):
        """Handle name change."""
        name = self.name_var.get().strip()
        if self.type_var.get() == "source":
            self.output_var.set(f"data:events:{name}_output")
        elif self.type_var.get() == "processor":
            self.input_var.set(f"data:events:{name}_input")
            self.output_var.set(f"data:events:{name}_output")
        elif self.type_var.get() == "sink":
            self.input_var.set(f"data:events:{name}_input")
    
    def add_actor(self):
        """Add the actor to the graph."""
        try:
            # Parse parameters
            params_text = self.params_text.get("1.0", tk.END).strip()
            parameters = {}
            if params_text:
                for line in params_text.split('\n'):
                    if ':' in line:
                        key, value = line.split(':', 1)
                        parameters[key.strip()] = value.strip()
            
            # Create actor
            actor = Actor(
                name=self.name_var.get(),
                type=ActorType(self.type_var.get()),
                input_topic=self.input_var.get() if self.input_var.get() else None,
                output_topic=self.output_var.get() if self.output_var.get() else None,
                host=self.host_var.get() if self.host_var.get() else None,
                parameters=parameters
            )
            
            # Add to graph
            errors = self.app.graph.add_actor(actor)
            
            if errors:
                error_msg = "\n".join(errors)
                messagebox.showerror("Validation Error", f"Cannot add actor:\n\n{error_msg}")
            else:
                # Update UI to assign position
                self.app.update_ui()
                self.dialog.destroy()
                self.app.set_status(f"Actor '{actor.name}' added")
                
        except Exception as e:
            messagebox.showerror("Error", f"Failed to add actor:\n{str(e)}")


def main():
    """Main entry point for the application."""
    root = tk.Tk()
    app = ERSAPEditorApp(root)
    root.mainloop()


if __name__ == "__main__":
    main() 