#!/bin/bash

# Set the PCAP file path
PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"
PCAP_SOCKETS_FILE="input/pcap_sockets.txt"

# Check if PCAP file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "Error: PCAP file not found at $PCAP_FILE"
    exit 1
fi

# Check if pcap_sockets.txt exists
if [ ! -f "$PCAP_SOCKETS_FILE" ]; then
    echo "Error: pcap_sockets.txt not found at $PCAP_SOCKETS_FILE"
    exit 1
fi

# Create output directory if it doesn't exist
OUTPUT_DIR="pcap_analysis"
mkdir -p "$OUTPUT_DIR"

# Extract unique IP addresses and their packet counts (stripping port numbers)
echo "Analyzing IP addresses in PCAP file..."
tcpdump -r "$PCAP_FILE" -n | awk '{
    split($3, src, ".");  # Split source IP.port
    split($5, dst, ".");  # Split destination IP.port
    printf "%s.%s.%s.%s %s.%s.%s.%s\n", 
        src[1], src[2], src[3], src[4],
        dst[1], dst[2], dst[3], dst[4]
}' | sort | uniq -c | sort -nr > "$OUTPUT_DIR/ip_analysis.txt"

# Extract detailed packet information for verification
echo "Extracting detailed packet information..."
tcpdump -r "$PCAP_FILE" -n -vvv > "$OUTPUT_DIR/packet_details.txt"

# Compare with pcap_sockets.txt
echo "Comparing with pcap_sockets.txt..."
cat "$PCAP_SOCKETS_FILE" | awk '{print $1}' | sort > "$OUTPUT_DIR/expected_ips.txt"

# Extract unique IPs from analysis (both source and destination)
awk '{print $2; print $3}' "$OUTPUT_DIR/ip_analysis.txt" | sort -u > "$OUTPUT_DIR/found_ips.txt"

echo "Analysis complete. Results are in $OUTPUT_DIR/"
echo "----------------------------------------"
echo "IP Analysis Summary:"
cat "$OUTPUT_DIR/ip_analysis.txt"
echo "----------------------------------------"
echo "IPs in pcap_sockets.txt but not in PCAP:"
comm -23 "$OUTPUT_DIR/expected_ips.txt" "$OUTPUT_DIR/found_ips.txt"
echo "----------------------------------------"
echo "IPs in PCAP but not in pcap_sockets.txt:"
comm -13 "$OUTPUT_DIR/expected_ips.txt" "$OUTPUT_DIR/found_ips.txt" 