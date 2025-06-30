"""Resource management module for RTDP workflows."""

from typing import Dict, List, Optional
import logging
import subprocess

logger = logging.getLogger(__name__)

class ResourceManager:
    """Manages resource allocation and validation for RTDP workflows."""
    
    def __init__(self):
        """Initialize the resource manager."""
        self.allocated_resources = {
            'gpu': {},  # GPU device allocations
            'cpu': {},  # CPU core allocations
            'memory': {},  # Memory allocations
            'ports': set()  # Port allocations
        }
    
    def validate_gpu_resources(self, config: Dict) -> bool:
        """Validate GPU resource configuration.
        
        Args:
            config: GPU proxy configuration dictionary
            
        Returns:
            bool: True if resources are valid, False otherwise
        """
        try:
            # Validate GPU device
            if 'device' not in config:
                logger.error("Missing GPU device specification")
                return False
            
            # Validate GPU memory
            if 'mem' not in config:
                logger.error("Missing GPU memory specification")
                return False
            
            # Validate GPU cores
            if 'cpus' not in config or not isinstance(config['cpus'], int) or config['cpus'] < 1:
                logger.error("Invalid GPU CPU count")
                return False
            
            # Validate GPU GRES
            if 'gres' not in config:
                logger.error("Missing GPU GRES specification")
                return False
            
            # Validate nodelist if provided
            if 'nodelist' in config:
                if not isinstance(config['nodelist'], str) or not config['nodelist'].strip():
                    logger.error("Invalid nodelist specification - must be a non-empty string")
                    return False
            
            # Validate per-proxy processing parameters if provided
            if 'matrix_width' in config:
                if not isinstance(config['matrix_width'], int) or config['matrix_width'] < 1:
                    logger.error("Invalid matrix_width - must be a positive integer")
                    return False
            
            if 'proxy_rate' in config:
                if not isinstance(config['proxy_rate'], (int, float)) or config['proxy_rate'] <= 0:
                    logger.error("Invalid proxy_rate - must be a positive number")
                    return False
            
            if 'socket_hwm' in config:
                if not isinstance(config['socket_hwm'], int) or config['socket_hwm'] < 1:
                    logger.error("Invalid socket_hwm - must be a positive integer")
                    return False
            
            return True
        except Exception as e:
            logger.error(f"Error validating GPU resources: {e}")
            return False
    
    def validate_cpu_resources(self, config: Dict) -> bool:
        """Validate CPU resource configuration.
        
        Args:
            config: CPU emulator configuration dictionary
            
        Returns:
            bool: True if resources are valid, False otherwise
        """
        try:
            # Validate CPU cores
            if 'cpus' not in config or not isinstance(config['cpus'], int) or config['cpus'] < 1:
                logger.error("Invalid CPU core count")
                return False
            
            # Validate memory
            if 'mem' not in config:
                logger.error("Missing memory specification")
                return False
            
            # Validate threads
            if 'threads' not in config or not isinstance(config['threads'], int) or config['threads'] < 1:
                logger.error("Invalid thread count")
                return False
            
            return True
        except Exception as e:
            logger.error(f"Error validating CPU resources: {e}")
            return False
    
    def validate_network_resources(self, config: Dict) -> bool:
        """Validate network resource configuration.
        
        Args:
            config: Network configuration dictionary
            
        Returns:
            bool: True if resources are valid, False otherwise
        """
        try:
            # Validate ports
            if 'in_port' not in config or 'out_port' not in config:
                logger.error("Missing port specifications")
                return False
            
            # Check port ranges
            if not (1024 <= config['in_port'] <= 65535):
                logger.error("Invalid input port range")
                return False
            
            if not (1024 <= config['out_port'] <= 65535):
                logger.error("Invalid output port range")
                return False
            
            # Check for port conflicts
            if config['in_port'] in self.allocated_resources['ports']:
                logger.error(f"Input port {config['in_port']} already allocated")
                return False
            
            if config['out_port'] in self.allocated_resources['ports']:
                logger.error(f"Output port {config['out_port']} already allocated")
                return False
            
            # Validate NIC configuration if specified
            if 'nic' in config:
                try:
                    subprocess.run(['ip', 'link', 'show', config['nic']], 
                                 check=True, capture_output=True)
                except subprocess.CalledProcessError:
                    logger.error(f"Invalid NIC: {config['nic']}")
                    return False
            
            return True
        except Exception as e:
            logger.error(f"Error validating network resources: {e}")
            return False
    
    def validate_sender_resources(self, config: Dict) -> bool:
        """Validate sender resource configuration.
        
        Args:
            config: Sender configuration dictionary
            
        Returns:
            bool: True if resources are valid, False otherwise
        """
        try:
            # Validate target port
            if 'target_port' not in config:
                logger.error("Missing sender target port")
                return False
            
            # Validate send rate if provided
            if 'send_rate' in config:
                if not isinstance(config['send_rate'], (int, float)) or config['send_rate'] <= 0:
                    logger.error("Invalid send_rate - must be a positive number")
                    return False
            
            # Validate group size if provided
            if 'group_size' in config:
                if not isinstance(config['group_size'], int) or config['group_size'] <= 0:
                    logger.error("Invalid group_size - must be a positive integer")
                    return False
            
            # Validate send_all_ones if provided
            if 'send_all_ones' in config:
                if not isinstance(config['send_all_ones'], int) or config['send_all_ones'] not in [0, 1]:
                    logger.error("Invalid send_all_ones - must be 0 or 1")
                    return False
            
            # Validate socket_hwm if provided
            if 'socket_hwm' in config:
                if not isinstance(config['socket_hwm'], int) or config['socket_hwm'] < 1:
                    logger.error("Invalid socket_hwm - must be a positive integer")
                    return False
            
            return True
        except Exception as e:
            logger.error(f"Error validating sender resources: {e}")
            return False
    
    def allocate_resources(self, component_type: str, config: Dict) -> bool:
        """Allocate resources for a component.
        
        Args:
            component_type: Type of component ('gpu_proxy' or 'cpu_emu')
            config: Component configuration dictionary
            
        Returns:
            bool: True if resources were allocated successfully, False otherwise
        """
        try:
            # Validate resources first
            if component_type == 'gpu_proxy':
                if not self.validate_gpu_resources(config):
                    return False
            elif component_type == 'cpu_emu':
                if not self.validate_cpu_resources(config):
                    return False
            else:
                logger.error(f"Unknown component type: {component_type}")
                return False
            
            # Validate network resources
            if not self.validate_network_resources(config):
                return False
            
            # Allocate resources
            if component_type == 'gpu_proxy':
                self.allocated_resources['gpu'][config['device']] = config
            elif component_type == 'cpu_emu':
                self.allocated_resources['cpu'][config['id']] = config
            
            # Allocate ports
            self.allocated_resources['ports'].add(config['in_port'])
            self.allocated_resources['ports'].add(config['out_port'])
            
            return True
        except Exception as e:
            logger.error(f"Error allocating resources: {e}")
            return False
    
    def release_resources(self, component_type: str, config: Dict) -> None:
        """Release allocated resources for a component.
        
        Args:
            component_type: Type of component ('gpu_proxy' or 'cpu_emu')
            config: Component configuration dictionary
        """
        try:
            # Release component-specific resources
            if component_type == 'gpu_proxy':
                if config['device'] in self.allocated_resources['gpu']:
                    del self.allocated_resources['gpu'][config['device']]
            elif component_type == 'cpu_emu':
                if config['id'] in self.allocated_resources['cpu']:
                    del self.allocated_resources['cpu'][config['id']]
            
            # Release ports
            if 'in_port' in config:
                self.allocated_resources['ports'].discard(config['in_port'])
            if 'out_port' in config:
                self.allocated_resources['ports'].discard(config['out_port'])
        except Exception as e:
            logger.error(f"Error releasing resources: {e}")
    
    def get_available_ports(self, start_port: int = 5000, end_port: int = 6000) -> List[int]:
        """Get list of available ports in the specified range.
        
        Args:
            start_port: Start of port range
            end_port: End of port range
            
        Returns:
            List[int]: List of available ports
        """
        return [port for port in range(start_port, end_port + 1) 
                if port not in self.allocated_resources['ports']]
    
    def get_resource_summary(self) -> Dict:
        """Get summary of allocated resources.
        
        Returns:
            Dict: Summary of allocated resources
        """
        return {
            'gpu_devices': list(self.allocated_resources['gpu'].keys()),
            'cpu_components': list(self.allocated_resources['cpu'].keys()),
            'allocated_ports': list(self.allocated_resources['ports'])
        } 