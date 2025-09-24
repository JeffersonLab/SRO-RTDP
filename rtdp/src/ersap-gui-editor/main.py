#!/usr/bin/env python3
"""
ERSAP Actor Editor - Main Entry Point

A graphical tool for creating and configuring ERSAP actor workflows.
"""

import sys
import tkinter as tk
from tkinter import messagebox
import traceback

# Add the current directory to the Python path
sys.path.insert(0, '.')

try:
    from gui.App import ERSAPEditorApp
except ImportError as e:
    print(f"Error importing GUI components: {e}")
    print("Make sure all required files are present.")
    sys.exit(1)


def main():
    """Main entry point for the application."""
    try:
        # Create the main window
        root = tk.Tk()
        
        # Set application icon and title
        root.title("ERSAP Actor Editor")
        root.geometry("1200x800")
        
        # Center the window
        root.update_idletasks()
        x = (root.winfo_screenwidth() // 2) - (1200 // 2)
        y = (root.winfo_screenheight() // 2) - (800 // 2)
        root.geometry(f"1200x800+{x}+{y}")
        
        # Create the application
        app = ERSAPEditorApp(root)
        
        # Handle window close
        def on_closing():
            if messagebox.askokcancel("Quit", "Do you want to quit?"):
                root.destroy()
        
        root.protocol("WM_DELETE_WINDOW", on_closing)
        
        # Start the application
        root.mainloop()
        
    except Exception as e:
        # Show error dialog
        error_msg = f"Application Error:\n\n{str(e)}\n\n{traceback.format_exc()}"
        messagebox.showerror("Error", error_msg)
        sys.exit(1)


if __name__ == "__main__":
    main() 