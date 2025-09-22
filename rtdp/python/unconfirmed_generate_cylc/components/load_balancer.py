"""Load balancer component implementation."""
import time
import threading
import queue
from typing import Dict, Any, List, Optional
import numpy as np
from collections import defaultdict
import hashlib
from .base import Component


class LoadBalancer(Component):
    """Component that distributes data across multiple emulators."""

    def __init__(self, config: Dict[str, Any]):
        """Initialize the load balancer component.

        Args:
            config: Component configuration dictionary
        """
        super().__init__(config)
        self.lb_config = config.get('load_balancer_config', {})

        # Load balancing strategy
        self.strategy = self.lb_config.get('strategy', 'round_robin')
        self.max_queue_size = self._parse_size(
            self.lb_config.get('max_queue_size', '100M')
        )
        self.health_check_interval = self.lb_config.get(
            'health_check_interval', 5
        )
        self.backpressure_threshold = self.lb_config.get(
            'backpressure_threshold', 0.8
        )
        self.rebalance_threshold = self.lb_config.get(
            'rebalance_threshold', 0.2
        )

        # State tracking
        self.current_target = 0  # For round-robin
        self.target_loads: Dict[str, float] = defaultdict(float)
        self.target_queues: Dict[str, queue.Queue] = {}
        self.target_health: Dict[str, bool] = {}
        self.lock = threading.Lock()

    def _parse_size(self, size_str: str) -> int:
        """Parse size string (e.g., '1M', '100K') to number of bytes."""
        units = {'K': 1024, 'M': 1024*1024, 'G': 1024*1024*1024}
        unit = size_str[-1].upper()
        if unit in units:
            return int(float(size_str[:-1]) * units[unit])
        return int(size_str)

    def initialize(self) -> None:
        """Initialize the load balancer component."""
        self.logger.info(
            f"Initializing load balancer with strategy: {self.strategy}"
        )
        # Initialize queues for each target
        for target in self.senders:
            self.target_queues[target] = queue.Queue(maxsize=100)
            self.target_health[target] = True

        # Start health check thread
        self.health_check_thread = threading.Thread(
            target=self._health_check_loop
        )
        self.health_check_thread.daemon = True
        self.health_check_thread.start()

    def _health_check_loop(self) -> None:
        """Periodically check health of target components."""
        while self.running:
            time.sleep(self.health_check_interval)
            self._check_target_health()

    def _check_target_health(self) -> None:
        """Check health of all target components."""
        with self.lock:
            for target, queue_size in self.target_loads.items():
                # Mark as unhealthy if queue is too full
                is_healthy = queue_size < self.backpressure_threshold
                if is_healthy != self.target_health[target]:
                    self.target_health[target] = is_healthy
                    status = "healthy" if is_healthy else "unhealthy"
                    self.logger.info(f"Target {target} is now {status}")

    def _select_target_round_robin(self) -> Optional[str]:
        """Select target using round-robin strategy."""
        with self.lock:
            healthy_targets = [
                t for t, h in self.target_health.items() if h
            ]
            if not healthy_targets:
                return None

            target = healthy_targets[self.current_target %
                                     len(healthy_targets)]
            self.current_target += 1
            return target

    def _select_target_least_loaded(self) -> Optional[str]:
        """Select target with lowest load."""
        with self.lock:
            healthy_targets = [
                t for t, h in self.target_health.items() if h
            ]
            if not healthy_targets:
                return None

            return min(
                healthy_targets,
                key=lambda t: self.target_loads[t]
            )

    def _select_target_consistent_hash(self, data: bytes) -> Optional[str]:
        """Select target using consistent hashing."""
        with self.lock:
            healthy_targets = [
                t for t, h in self.target_health.items() if h
            ]
            if not healthy_targets:
                return None

            # Use first 8 bytes of data for hashing
            hash_value = int.from_bytes(
                hashlib.md5(data[:8]).digest()[:8],
                byteorder='big'
            )
            return healthy_targets[hash_value % len(healthy_targets)]

    def _select_target(self, data: bytes) -> Optional[str]:
        """Select target based on configured strategy."""
        if self.strategy == 'round_robin':
            return self._select_target_round_robin()
        elif self.strategy == 'least_loaded':
            return self._select_target_least_loaded()
        else:  # consistent_hash
            return self._select_target_consistent_hash(data)

    def _update_target_load(self, target: str, data_size: int) -> None:
        """Update load statistics for a target."""
        with self.lock:
            # Update exponential moving average of load
            alpha = 0.1  # Smoothing factor
            current_load = self.target_loads[target]
            new_load = data_size / self.max_queue_size
            self.target_loads[target] = (
                alpha * new_load + (1 - alpha) * current_load
            )

    def process(self) -> None:
        """Main processing loop."""
        if not self.receiver:
            time.sleep(0.1)  # Avoid busy waiting
            return

        try:
            # Receive data
            data = self.receiver.recv()

            # Select target
            target = self._select_target(data)
            if not target:
                self.logger.warning("No healthy targets available")
                return

            # Send data to selected target
            self.send_data(data, target=target)

            # Update load statistics
            self._update_target_load(target, len(data))

            # Check if rebalancing is needed
            self._check_rebalancing_needed()

        except Exception as e:
            self.logger.error(f"Error processing data: {e}")

    def _check_rebalancing_needed(self) -> None:
        """Check if load rebalancing is needed."""
        with self.lock:
            loads = [load for target, load in self.target_loads.items()
                     if self.target_health[target]]
            if not loads:
                return

            load_std = np.std(loads)
            if load_std > self.rebalance_threshold:
                self.logger.warning(
                    f"High load variance detected: {load_std:.3f}, "
                    "consider rebalancing"
                )

    def cleanup(self) -> None:
        """Clean up resources."""
        self.logger.info("Shutting down load balancer")
        super().cleanup()
