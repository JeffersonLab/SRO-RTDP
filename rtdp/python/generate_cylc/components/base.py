"""Base classes for workflow components."""
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
import logging
import zmq


class Component(ABC):
    """Base class for all workflow components."""

    def __init__(self, config: Dict[str, Any]):
        """Initialize the component.

        Args:
            config: Component configuration dictionary
        """
        self.config = config
        self.logger = logging.getLogger(self.__class__.__name__)
        self.context = zmq.Context()
        self.running = False

        # Network configuration
        self.network_config = config.get('network', {})
        self.listen_port = self.network_config.get('listen_port')
        self.bind_address = self.network_config.get('bind_address', '0.0.0.0')

        # Initialize sockets
        self._init_sockets()

    def _init_sockets(self) -> None:
        """Initialize ZMQ sockets based on configuration."""
        if self.listen_port:
            self.receiver = self.context.socket(zmq.PULL)
            self.receiver.bind(f"tcp://{self.bind_address}:{self.listen_port}")
        else:
            self.receiver = None

        # Connect to upstream components if specified
        self.senders = {}
        for conn in self.network_config.get('connect_to', []):
            socket = self.context.socket(zmq.PUSH)
            socket.connect(f"tcp://{conn['host']}:{conn['port']}")
            self.senders[f"{conn['host']}:{conn['port']}"] = socket

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
        self.initialize()
        self.running = True
        try:
            while self.running:
                self.process()
        except KeyboardInterrupt:
            self.logger.info("Received shutdown signal")
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
