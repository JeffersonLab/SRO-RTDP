#!/usr/bin/env python3

import sys
import pyshark
import pandas as pd

MAX_PACKETS = 1000

def process_pcap(file_name):
    # Load the pcap file
    cap = pyshark.FileCapture(file_name)

    # Initialize variables
    previous_packet_time = None
    data = []

    # Loop through packets and calculate time differences
    Npackets = 0
    for packet in cap:
    
        if Npackets == 0:
            print("Packet Top Level Information:")
            print(f"    Sniff Timestamp: {packet.sniff_timestamp}")
            print(f"    Length: {packet.length}")
            print(f"    Highest Layer: {packet.highest_layer}")
            print(f"    Interface Captured: {packet.interface_captured}")
            print("Packet layer info:")
            for layer in packet.layers:
                print(f"Layer: {layer.layer_name}")
                for field_name in layer.field_names:
                    print(f"    Field: {field_name} = {getattr(layer, field_name)}")

        if Npackets < 10:
            print(f"Packet timestamp: {packet.sniff_timestamp}")
    
        current_packet_time = float(packet.sniff_timestamp)
        if previous_packet_time is not None:
            time_diff = current_packet_time - previous_packet_time
            data.append({'Current_Packet_Time': current_packet_time, 'Time_Difference': time_diff})
        previous_packet_time = current_packet_time
        Npackets += 1
        if Npackets>= MAX_PACKETS:
            print("Maximum number of packets processed ({})".format(Npackets))
            break

    # Create a DataFrame
    df = pd.DataFrame(data)

    # Save the DataFrame to a file
    output_file = f'{file_name}_time_differences.csv'
    df.to_csv(output_file, index=False)
    print(f"Data saved to {output_file}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python script.py <pcap_file>")
        sys.exit(1)

    pcap_file = sys.argv[1]
    process_pcap(pcap_file)
