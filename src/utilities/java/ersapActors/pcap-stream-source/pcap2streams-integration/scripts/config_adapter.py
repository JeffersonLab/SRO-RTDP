#!/usr/bin/env python3
import json
import sys
import os

def adapt_config(input_file, output_file):
    """
    Adapt the Pcap2Streams IP-based configuration to the format expected by SimpleMultiSocketTest
    """
    try:
        with open(input_file, 'r') as f:
            ip_config = json.load(f)
        
        # Create the multi-socket configuration format
        multi_socket_config = {"connections": []}
        
        # Convert each IP-based connection to the multi-socket format
        for conn in ip_config.get("connections", []):
            multi_socket_config["connections"].append({
                "host": conn.get("host", "localhost"),
                "port": conn.get("port", 9000),
                "buffer_size": conn.get("buffer_size", 8192),
                "read_timeout": conn.get("read_timeout", 1000),
                "connection_timeout": conn.get("connection_timeout", 1000)
            })
        
        # Write the adapted configuration
        with open(output_file, 'w') as f:
            json.dump(multi_socket_config, f, indent=2)
        
        print(f"Successfully adapted configuration from {input_file} to {output_file}")
        print(f"Created configuration with {len(multi_socket_config['connections'])} connections")
        return True
    
    except Exception as e:
        print(f"Error adapting configuration: {str(e)}")
        return False

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python config_adapter.py <input_config> <output_config>")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    if not os.path.exists(input_file):
        print(f"Error: Input file {input_file} not found")
        sys.exit(1)
    
    success = adapt_config(input_file, output_file)
    sys.exit(0 if success else 1)
