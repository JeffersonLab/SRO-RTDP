#!/bin/bash

# Test different data sizes and verify CPU emulator behavior
echo "Testing different data sizes..."

# Start receiver in background
./start_receiver.sh -p 50080 -o output.bin -v > receiver.log 2>&1 &
RECEIVER_PID=$!

# Wait for receiver to start
sleep 2

# Start CPU emulator in background with verbose mode
./start_cpu_emu.sh -t 4 -b 50 -m 0.2 -o 0.001 -v > emulator.log 2>&1 &
EMULATOR_PID=$!

# Wait for emulator to start
sleep 2

# Test different sizes
SIZES=("1K" "10K" "100K" "1M" "10M" "100M")

for size in "${SIZES[@]}"; do
    echo "Testing with size: $size"
    ./send_data.sh -s "$size" -v 2>&1 | tee "send_${size}.log"
    echo "Waiting for processing..."
    sleep 5
    echo "----------------------------------------"
done

# Clean up
kill $RECEIVER_PID $EMULATOR_PID
wait

# Show results
echo "Results:"
for size in "${SIZES[@]}"; do
    echo "Size: $size"
    echo "Sent data size:"
    grep "Actual data size:" "send_${size}.log"
    echo "CPU Emulator response:"
    grep "Received request" "emulator.log" | tail -n 1
    echo "----------------------------------------"
done

# Clean up logs
rm -f send_*.log receiver.log emulator.log 