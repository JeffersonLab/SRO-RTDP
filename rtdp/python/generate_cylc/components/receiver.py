"""Receiver component implementation."""
import os
import time
import zlib
import argparse
from typing import Dict, Any, Optional, BinaryIO
import yaml
from base import Component
import zmq
import logging


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
        self.logger.info(f"Using output directory: {self.output_dir}")
        try:
            os.makedirs(self.output_dir, exist_ok=True)
            self.logger.info(
                f"Created/verified output directory: {self.output_dir}"
            )
            # Test write permissions
            test_file = os.path.join(self.output_dir, 'test.txt')
            with open(test_file, 'w') as f:
                f.write('test')
            os.remove(test_file)
            self.logger.info("Successfully tested write permissions")
        except Exception as e:
            self.logger.error(
                f"Error setting up output directory {self.output_dir}: {e}",
                exc_info=True
            )
            raise

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
        self.current_file_handle: Optional[BinaryIO] = None
        self.current_source: Optional[str] = None

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
        self.logger.info(f"Listening on port {self.listen_port}")

    def _open_output_file(self, source_info: str, routing_info: str) -> None:
        """Open a new output file.

        Args:
            source_info: Source identifier for the data
            routing_info: Routing information (e.g., emulator ID)
        """
        timestamp = int(time.time())
        # Include source and routing info in filename
        source_id = source_info.replace(':', '_')
        route_id = routing_info.replace(':', '_')
        self.current_file = os.path.join(
            self.output_dir,
            f"data_{source_id}_via_{route_id}_{timestamp}.bin"
        )
        self.logger.debug(f"Opening file: {self.current_file}")
        try:
            self.current_file_handle = open(self.current_file, 'wb')
            self.current_source = f"{source_info}_{routing_info}"
            self.logger.info(
                f"Successfully opened output file: {self.current_file}")
        except Exception as e:
            self.logger.error(
                f"Error opening file {self.current_file}: {e}",
                exc_info=True
            )
            raise

    def _validate_data(self, data: bytes) -> bool:
        """Validate received data.

        Args:
            data: Data to validate

        Returns:
            True if data is valid, False otherwise
        """
        # Basic validation - check if data is not empty and is bytes
        if not data or not isinstance(data, bytes):
            self.logger.warning(
                f"Invalid data: empty={not data}, type={type(data)}"
            )
            return False
        return True

    def _process_data(self, data: bytes, source_info: str, routing_info: str) -> None:
        """Process and store received data.

        Args:
            data: Data to process and store
            source_info: Source identifier for the data
            routing_info: Routing information (e.g., emulator ID)
        """
        combined_source = f"{source_info}_{routing_info}"
        self.logger.debug(
            f"Processing data from {source_info} via {routing_info}, "
            f"size: {len(data)} bytes"
        )

        # Validate data if enabled
        if self.data_validation and not self._validate_data(data):
            self.logger.warning("Received invalid data, skipping")
            return

        # Create new file if needed
        if not self.current_file_handle or self.current_source != combined_source:
            if self.current_file_handle:
                self.current_file_handle.close()
            self._open_output_file(source_info, routing_info)

        # Compress if enabled
        if self.compression:
            data = zlib.compress(data)

        # Write to file
        if self.current_file_handle:
            try:
                self.current_file_handle.write(data)
                self.current_file_handle.flush()
                # Force write to disk
                os.fsync(self.current_file_handle.fileno())
                self.logger.debug(
                    f"Wrote and flushed {len(data)} bytes to {self.current_file}"
                )

                # Update statistics
                self.bytes_received += len(data)
                self.chunks_received += 1

                # Rotate file if it exceeds buffer size
                if self.bytes_received >= self.buffer_size:
                    self.logger.info(
                        f"Rotating file after {self.bytes_received} bytes"
                    )
                    self.current_file_handle.close()
                    self._open_output_file(source_info, routing_info)
                    self.bytes_received = 0
            except Exception as e:
                self.logger.error(
                    f"Error writing data to {self.current_file}: {e}",
                    exc_info=True
                )
        else:
            self.logger.error("No file handle available for writing")

    def process(self) -> None:
        """Main processing loop."""
        try:
            if not self.receiver:
                self.logger.error("No receiver socket initialized")
                time.sleep(1)
                return

            try:
                # Use non-blocking receive with routing info
                self.logger.debug("Waiting for data...")
                try:
                    multipart_msg = self.receiver.recv_multipart(
                        flags=zmq.NOBLOCK)
                    self.logger.debug("Received data from socket")
                except zmq.Again:
                    self.logger.debug("No data available")
                    time.sleep(0.1)
                    return
                except Exception as e:
                    self.logger.error(
                        f"Error receiving data: {e}", exc_info=True)
                    time.sleep(0.1)
                    return

                if multipart_msg:
                    self.logger.debug(
                        f"Received multipart message with {len(multipart_msg)} parts"
                    )
                    # Extract source info and data
                    if len(multipart_msg) >= 3:  # Now expecting 3 parts
                        source_info = multipart_msg[0].decode()
                        # New routing info
                        routing_info = multipart_msg[1].decode()
                        data = multipart_msg[-1]
                        self.logger.debug(
                            f"Processing message from {source_info} "
                            f"via {routing_info}, "
                            f"data size: {len(data)} bytes"
                        )
                        self._process_data(data, source_info, routing_info)

                        # Log progress periodically
                        if self.chunks_received % 100 == 0:
                            self.logger.info(
                                f"Received {self.chunks_received} chunks "
                                f"({self.bytes_received} bytes) "
                                f"from {source_info} via {routing_info}"
                            )
                    else:
                        self.logger.warning(
                            "Received incomplete multipart message, skipping"
                        )
            except Exception as e:
                self.logger.error(
                    f"Error processing data: {e}",
                    exc_info=True
                )
                time.sleep(0.1)  # Wait before retrying
        except Exception as e:
            self.logger.error(
                f"Process error: {e}",
                exc_info=True
            )
            time.sleep(1)  # Wait before retrying

    def cleanup(self) -> None:
        """Clean up resources."""
        self.logger.info(
            f"Shutting down receiver, received {self.chunks_received} chunks"
        )
        if self.current_file_handle:
            try:
                self.current_file_handle.flush()
                os.fsync(self.current_file_handle.fileno())
                self.current_file_handle.close()
                self.logger.info("Successfully closed output file")
            except Exception as e:
                self.logger.error(
                    f"Error closing file: {e}",
                    exc_info=True
                )
        super().cleanup()


def main() -> None:
    """Main entry point for the receiver component."""
    parser = argparse.ArgumentParser(description='RTDP Receiver Component')
    parser.add_argument('--config', required=True, help='Path to config file')
    args = parser.parse_args()

    # Configure logging
    logging.basicConfig(
        level=logging.DEBUG,  # Set to DEBUG for more verbose output
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )
    logger = logging.getLogger('receiver')
    logger.setLevel(logging.DEBUG)

    try:
        # Load configuration
        logger.info(f"Loading config from {args.config}")
        with open(args.config, 'r') as f:
            config = yaml.safe_load(f)
            logger.debug(f"Loaded config: {config}")

        # Create and start receiver
        logger.info("Creating receiver component")
        receiver = Receiver(config)
        receiver.start()
    except Exception as e:
        logger.error(f"Failed to start receiver: {e}", exc_info=True)
        raise


if __name__ == '__main__':
    main()
