"""Sender component implementation."""
import os
import time
import argparse
from typing import Dict, Any, Optional
import numpy as np
import yaml
from base import Component
import zmq
import logging


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

        # Component identification
        self.component_id = config.get('id', 'unknown_sender')
        self.emulator_id = config.get('emulator_id', 'unknown_emulator')

        # Completion flag
        self.data_sent = False

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
        if not self.senders:
            self.logger.error("No sender sockets initialized")
            return
        self.logger.info(f"Connected to {len(self.senders)} targets")

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
        try:
            if not self.senders:
                self.logger.error("No sender sockets initialized")
                time.sleep(1)
                return

            # Check if we've sent all test data
            if self.bytes_sent >= self.test_data_size:
                if not self.data_sent:
                    self.logger.info(
                        f"Completed sending {self.bytes_sent} bytes "
                        f"in {self.chunks_sent} chunks"
                    )
                    self.data_sent = True
                    # Wait for a bit to ensure data is processed
                    time.sleep(2)
                    self.stop()
                return

            # Determine chunk size for this iteration
            remaining = self.test_data_size - self.bytes_sent
            chunk_size = min(self.chunk_size, remaining)

            # Get data to send
            data = self._read_source_data(chunk_size)
            if data is None:
                data = self._generate_test_data(chunk_size)

            # Create multipart message with source and routing information
            source_info = f"sender:{self.component_id}"
            routing_info = f"emulator:{self.emulator_id}"
            message = [source_info.encode(), routing_info.encode(), data]

            # Send the data
            try:
                for socket in self.senders.values():
                    socket.send_multipart(message)
                self.bytes_sent += len(data)
                self.chunks_sent += 1

                # Log progress periodically
                if self.chunks_sent % 100 == 0:
                    self.logger.info(
                        f"Sent {self.chunks_sent} chunks "
                        f"({self.bytes_sent} bytes) "
                        f"via {routing_info}"
                    )

                # Add some delay to control sending rate
                time.sleep(0.001)  # 1ms delay
            except zmq.Again:
                # Socket would block, wait a bit
                time.sleep(0.1)
            except Exception as e:
                self.logger.error(f"Error sending data: {e}")
                time.sleep(0.1)  # Wait before retrying
        except Exception as e:
            self.logger.error(f"Process error: {e}")
            time.sleep(1)  # Wait before retrying


def main() -> None:
    """Main entry point for the sender component."""
    parser = argparse.ArgumentParser(description='RTDP Sender Component')
    parser.add_argument('--config', required=True, help='Path to config file')
    args = parser.parse_args()

    # Configure logging
    logging.basicConfig(
        level=logging.DEBUG,  # Set to DEBUG for more verbose output
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )
    logger = logging.getLogger('sender')
    logger.setLevel(logging.DEBUG)

    try:
        # Load configuration
        logger.info(f"Loading config from {args.config}")
        with open(args.config, 'r') as f:
            config = yaml.safe_load(f)
            logger.debug(f"Loaded config: {config}")

        # Create and start sender
        logger.info("Creating sender component")
        sender = Sender(config)
        sender.start()
    except Exception as e:
        logger.error(f"Failed to start sender: {e}", exc_info=True)
        raise


if __name__ == '__main__':
    main()
