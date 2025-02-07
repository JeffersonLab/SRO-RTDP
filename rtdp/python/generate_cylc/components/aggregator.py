"""Aggregator component implementation."""
import time
import threading
import heapq
from typing import Dict, Any, List, Optional, Tuple
from collections import defaultdict
from .base import Component


class Aggregator(Component):
    """Component that aggregates data from multiple emulators."""

    def __init__(self, config: Dict[str, Any]):
        """Initialize the aggregator component.

        Args:
            config: Component configuration dictionary
        """
        super().__init__(config)
        self.agg_config = config.get('aggregator_config', {})

        # Aggregation strategy
        self.strategy = self.agg_config.get('strategy', 'ordered')
        self.buffer_size = self._parse_size(
            self.agg_config.get('buffer_size', '256M')
        )
        self.max_delay = self.agg_config.get('max_delay', 1000)  # ms
        self.batch_size = self.agg_config.get('batch_size', 100)
        self.window_size = self._parse_time(
            self.agg_config.get('window_size', '1s')
        )

        # State tracking
        self.sequence_counter = 0
        # heap for ordered mode
        self.ordered_buffer: List[Tuple[int, bytes]] = []
        self.unordered_buffer: List[bytes] = []  # list for unordered mode
        self.time_windows: Dict[int, List[bytes]] = defaultdict(
            list)  # for time window mode
        self.lock = threading.Lock()

        # Statistics
        self.chunks_received = 0
        self.chunks_sent = 0
        self.total_bytes = 0

    def _parse_size(self, size_str: str) -> int:
        """Parse size string (e.g., '1M', '100K') to number of bytes."""
        units = {'K': 1024, 'M': 1024*1024, 'G': 1024*1024*1024}
        unit = size_str[-1].upper()
        if unit in units:
            return int(float(size_str[:-1]) * units[unit])
        return int(size_str)

    def _parse_time(self, time_str: str) -> float:
        """Parse time string (e.g., '1s', '100ms') to seconds."""
        if time_str.endswith('ms'):
            return float(time_str[:-2]) / 1000
        elif time_str.endswith('s'):
            return float(time_str[:-1])
        return float(time_str)

    def initialize(self) -> None:
        """Initialize the aggregator component."""
        self.logger.info(
            f"Initializing aggregator with strategy: {self.strategy}"
        )
        # Start window processing thread if using time windows
        if self.strategy == 'time_window':
            self.window_thread = threading.Thread(
                target=self._process_time_windows
            )
            self.window_thread.daemon = True
            self.window_thread.start()

    def _process_ordered(self, seq: int, data: bytes) -> None:
        """Process data in ordered mode.

        Args:
            seq: Sequence number
            data: Data to process
        """
        with self.lock:
            heapq.heappush(self.ordered_buffer, (seq, data))

            # Process ordered data if we have enough or waited too long
            current_time = time.time()
            while self.ordered_buffer:
                next_seq, next_data = self.ordered_buffer[0]
                if (next_seq == self.sequence_counter or
                        len(self.ordered_buffer) >= self.batch_size):
                    heapq.heappop(self.ordered_buffer)
                    self.send_data(next_data)
                    self.sequence_counter += 1
                    self.chunks_sent += 1
                else:
                    break

    def _process_unordered(self, data: bytes) -> None:
        """Process data in unordered mode.

        Args:
            data: Data to process
        """
        with self.lock:
            self.unordered_buffer.append(data)

            # Send batch if buffer is full
            if len(self.unordered_buffer) >= self.batch_size:
                for chunk in self.unordered_buffer:
                    self.send_data(chunk)
                self.chunks_sent += len(self.unordered_buffer)
                self.unordered_buffer.clear()

    def _get_window_key(self, timestamp: float) -> int:
        """Get time window key for timestamp."""
        return int(timestamp / self.window_size)

    def _process_time_window(self, data: bytes) -> None:
        """Process data using time windows.

        Args:
            data: Data to process
        """
        current_time = time.time()
        window_key = self._get_window_key(current_time)

        with self.lock:
            self.time_windows[window_key].append(data)

    def _process_time_windows(self) -> None:
        """Process completed time windows."""
        while self.running:
            current_time = time.time()
            current_window = self._get_window_key(current_time)

            with self.lock:
                # Process all windows that are complete
                completed_windows = [
                    k for k in self.time_windows.keys()
                    if k < current_window - 1
                ]

                for window_key in completed_windows:
                    window_data = self.time_windows.pop(window_key)
                    for chunk in window_data:
                        self.send_data(chunk)
                    self.chunks_sent += len(window_data)

            time.sleep(self.window_size / 10)  # Check periodically

    def process(self) -> None:
        """Main processing loop."""
        if not self.receiver:
            time.sleep(0.1)  # Avoid busy waiting
            return

        try:
            # Receive data
            data = self.receiver.recv()
            self.chunks_received += 1
            self.total_bytes += len(data)

            # Process based on strategy
            if self.strategy == 'ordered':
                seq = self.chunks_received - 1  # Use receive order as sequence
                self._process_ordered(seq, data)
            elif self.strategy == 'unordered':
                self._process_unordered(data)
            else:  # time_window
                self._process_time_window(data)

            # Log progress periodically
            if self.chunks_received % 100 == 0:
                self.logger.info(
                    f"Processed {self.chunks_received} chunks, "
                    f"sent {self.chunks_sent} chunks, "
                    f"total bytes: {self.total_bytes}"
                )

        except Exception as e:
            self.logger.error(f"Error processing data: {e}")

    def cleanup(self) -> None:
        """Clean up resources."""
        self.logger.info(
            f"Shutting down aggregator, processed {self.chunks_received} chunks"
        )

        # Process remaining data
        with self.lock:
            if self.strategy == 'ordered':
                while self.ordered_buffer:
                    _, data = heapq.heappop(self.ordered_buffer)
                    self.send_data(data)
            elif self.strategy == 'unordered':
                for data in self.unordered_buffer:
                    self.send_data(data)
            else:  # time_window
                for window_data in self.time_windows.values():
                    for data in window_data:
                        self.send_data(data)

        super().cleanup()
