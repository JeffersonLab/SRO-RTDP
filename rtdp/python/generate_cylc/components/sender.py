"""Sender component implementation."""
import os
import time
from typing import Dict, Any, Optional
import numpy as np
from .base import Component


class Sender(Component):
    """Component that sends data to downstream components."""

    def __init__(self, config: Dict[str, Any]):
        """Initialize the sender component.

        Args:
            config: Component configuration dictionary
        """
        super().__init__(config)
        self.sender_config = config.get('sender_config', {})
        self.data_source = self.sender_config.get('data_source')
        self.data_format = self.sender_config.get('data_format', 'raw')
        self.chunk_size = self._parse_size(
            self.sender_config.get('chunk_size', '1M')
        )

        # Test data configuration
        self.test_data = self.sender_config.get('test_data', {})
        self.test_data_size = self._parse_size(
            self.test_data.get('size', '100M')
        )
        self.test_data_pattern = self.test_data.get('pattern', 'random')

    def _parse_size(self, size_str: str) -> int:
        """Parse size string (e.g., '1M', '100K') to number of bytes.

        Args:
            size_str: Size string with unit suffix

        Returns:
            Size in bytes
        """
        units = {'K': 1024, 'M': 1024*1024, 'G': 1024*1024*1024}
        unit = size_str[-1].upper()
        if unit in units:
            return int(float(size_str[:-1]) * units[unit])
        return int(size_str)

    def initialize(self) -> None:
        """Initialize the sender component."""
        self.logger.info("Initializing sender component")
        self.bytes_sent = 0
        self.chunks_sent = 0

    def _generate_test_data(self, size: int) -> bytes:
        """Generate test data of specified size.

        Args:
            size: Size of data to generate in bytes

        Returns:
            Generated test data
        """
        if self.test_data_pattern == 'random':
            return np.random.bytes(size)
        elif self.test_data_pattern == 'sequential':
            return bytes(range(size % 256)) * (size // 256 + 1)
        else:  # custom pattern
            return b'X' * size

    def _read_source_data(self, size: int) -> Optional[bytes]:
        """Read data from the configured data source.

        Args:
            size: Number of bytes to read

        Returns:
            Data read from source or None if no more data
        """
        if not self.data_source or not os.path.exists(self.data_source):
            return None

        with open(self.data_source, 'rb') as f:
            data = f.read(size)
            return data if data else None

    def process(self) -> None:
        """Process and send data."""
        # Check if we've sent all test data
        if self.bytes_sent >= self.test_data_size:
            self.logger.info(
                f"Completed sending {self.bytes_sent} bytes "
                f"in {self.chunks_sent} chunks"
            )
            self.stop()
            return

        # Determine chunk size for this iteration
        remaining = self.test_data_size - self.bytes_sent
        chunk_size = min(self.chunk_size, remaining)

        # Get data to send
        data = self._read_source_data(chunk_size)
        if data is None:
            data = self._generate_test_data(chunk_size)

        # Send the data
        self.send_data(data)

        self.bytes_sent += len(data)
        self.chunks_sent += 1

        # Add some delay to control sending rate
        time.sleep(0.001)  # 1ms delay
