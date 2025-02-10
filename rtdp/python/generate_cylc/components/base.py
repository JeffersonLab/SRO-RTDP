"""Base classes for workflow components."""
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
import logging
import zmq
import time


class Component(ABC):
    """Base class for all workflow components."""

    def __init__(self, config: Dict[str, Any]):
        """Initialize the component.

        Args:
            config: Component configuration dictionary
        """
        self.config = config
        self.logger = logging.getLogger(self.__class__.__name__)
        self.logger.setLevel(logging.DEBUG)
        self.logger.debug(f"Initializing component with config: {config}")

        self.context = zmq.Context()
        self.running = False

        # Network configuration
        self.network_config = config.get('network', {})
        self.listen_port = self.network_config.get('listen_port')
        self.bind_address = self.network_config.get('bind_address', '0.0.0.0')
        self.logger.debug(
            f"Network config - listen_port: {self.listen_port}, "
            f"bind_address: {self.bind_address}"
        )

        # Initialize socket variables with proper type hints
        self.receiver: Optional[zmq.Socket] = None
        self.senders: Dict[str, zmq.Socket] = {}

        # Initialize sockets
        self._init_sockets()

    def _init_sockets(self) -> None:
        """Initialize ZMQ sockets based on configuration."""
        try:
            if self.listen_port:
                self.receiver = self.context.socket(zmq.PULL)
                self.receiver.setsockopt(zmq.LINGER, 0)  # Don't wait on close
                bind_addr = f"tcp://{self.bind_address}:{self.listen_port}"
                self.receiver.bind(bind_addr)
                self.logger.info(f"Bound receiver to {bind_addr}")
            else:
                self.receiver = None

            # Connect to upstream components if specified
            self.senders = {}
            for conn in self.network_config.get('connect_to', []):
                socket = self.context.socket(zmq.PUSH)
                socket.setsockopt(zmq.LINGER, 0)  # Don't wait on close
                connect_addr = f"tcp://{conn['host']}:{conn['port']}"
                socket.connect(connect_addr)
                self.senders[f"{conn['host']}:{conn['port']}"] = socket
                self.logger.info(f"Connected sender to {connect_addr}")
        except Exception as e:
            self.logger.error(f"Error initializing sockets: {e}")
            raise

    @abstractmethod
    def initialize(self) -> None:
        """Initialize the component before starting."""
        pass

    @abstractmethod
    def process(self) -> None:
        """Main processing loop."""
        pass

    def start(self) -> None:
        """Start the component."""
        self.logger.info("Starting component")
        try:
            self.initialize()
            self.running = True
            self.logger.info("Component initialized, entering main loop")
            while self.running:
                try:
                    self.process()
                except Exception as e:
                    self.logger.error(
                        f"Error in process loop: {e}", exc_info=True)
                    time.sleep(1)  # Wait before retrying
        except KeyboardInterrupt:
            self.logger.info("Received shutdown signal")
        except Exception as e:
            self.logger.error(f"Component error: {e}", exc_info=True)
        finally:
            self.cleanup()

    def stop(self) -> None:
        """Stop the component."""
        self.running = False

    def cleanup(self) -> None:
        """Clean up resources."""
        self.logger.info("Cleaning up resources")
        if self.receiver:
            self.receiver.close()
        for socket in self.senders.values():
            socket.close()
        self.context.term()

    def send_data(self, data: bytes, target: Optional[str] = None) -> None:
        """Send data to connected components.

        Args:
            data: Data to send
            target: Optional target identifier (host:port)
        """
        if target and target in self.senders:
            self.senders[target].send(data)
        else:
            # Send to all connected components
            for socket in self.senders.values():
                socket.send(data)

    def send_data_multipart(
        self, message_parts: list, target: Optional[str] = None
    ) -> None:
        """Send multipart data to connected components.

        Args:
            message_parts: List of message parts to send
            target: Optional target identifier (host:port)
        """
        if target and target in self.senders:
            self.senders[target].send_multipart(message_parts)
        else:
            # Send to all connected components
            for socket in self.senders.values():
                socket.send_multipart(message_parts)
