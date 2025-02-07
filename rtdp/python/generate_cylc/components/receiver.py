"""Receiver component implementation."""
import os
import time
import zlib
from typing import Dict, Any, Optional
from .base import Component


class Receiver(Component):
    """Component that receives and processes data."""

    def __init__(self, config: Dict[str, Any]):
        """Initialize the receiver component.

        Args:
            config: Component configuration dictionary
        """
        super().__init__(config)
        self.receiver_config = config.get('receiver_config', {})

        # Output configuration
        self.output_dir = self.receiver_config.get(
            'output_dir', 'received_data')
        os.makedirs(self.output_dir, exist_ok=True)

        # Processing configuration
        self.data_validation = self.receiver_config.get(
            'data_validation', True)
        self.buffer_size = self._parse_size(
            self.receiver_config.get('buffer_size', '64M')
        )
        self.compression = self.receiver_config.get('compression', False)

        # Statistics
        self.bytes_received = 0
        self.chunks_received = 0
        self.current_file: Optional[str] = None
        self.current_file_handle = None

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
        """Initialize the receiver component."""
        self.logger.info(
            f"Initializing receiver component, output dir: {self.output_dir}"
        )
        self._open_output_file()

    def _open_output_file(self) -> None:
        """Open a new output file."""
        timestamp = int(time.time())
        self.current_file = os.path.join(
            self.output_dir, f"data_{timestamp}.bin"
        )
        self.current_file_handle = open(self.current_file, 'wb')
        self.logger.info(f"Opened output file: {self.current_file}")

    def _validate_data(self, data: bytes) -> bool:
        """Validate received data.

        Args:
            data: Data to validate

        Returns:
            True if data is valid, False otherwise
        """
        # Basic validation - check if data is not empty and is bytes
        if not data or not isinstance(data, bytes):
            return False

        # Add checksum validation if needed
        # checksum = zlib.crc32(data)
        return True

    def _process_data(self, data: bytes) -> None:
        """Process and store received data.

        Args:
            data: Data to process and store
        """
        # Validate data if enabled
        if self.data_validation and not self._validate_data(data):
            self.logger.warning("Received invalid data, skipping")
            return

        # Compress if enabled
        if self.compression:
            data = zlib.compress(data)

        # Write to file
        if self.current_file_handle:
            self.current_file_handle.write(data)
            self.current_file_handle.flush()

            # Update statistics
            self.bytes_received += len(data)
            self.chunks_received += 1

            # Rotate file if it exceeds buffer size
            if self.bytes_received >= self.buffer_size:
                self.current_file_handle.close()
                self._open_output_file()
                self.bytes_received = 0

    def process(self) -> None:
        """Main processing loop."""
        if self.receiver:
            try:
                data = self.receiver.recv()
                self._process_data(data)

                # Log progress periodically
                if self.chunks_received % 100 == 0:
                    self.logger.info(
                        f"Received {self.chunks_received} chunks, "
                        f"total bytes: {self.bytes_received}"
                    )
            except Exception as e:
                self.logger.error(f"Error processing data: {e}")
        else:
            time.sleep(0.1)  # Avoid busy waiting

    def cleanup(self) -> None:
        """Clean up resources."""
        self.logger.info(
            f"Shutting down receiver, received {self.chunks_received} chunks"
        )
        if self.current_file_handle:
            self.current_file_handle.close()
        super().cleanup()
