"""Component implementations for the workflow system."""
from typing import Dict, Any, Type

from .base import Component
from .sender import Sender
from .receiver import Receiver
from .load_balancer import LoadBalancer
from .aggregator import Aggregator

# Component type registry
COMPONENT_TYPES: Dict[str, Type[Component]] = {
    'sender': Sender,
    'receiver': Receiver,
    'load_balancer': LoadBalancer,
    'aggregator': Aggregator
}


def create_component(component_type: str, config: Dict[str, Any]) -> Component:
    """Create a component instance.

    Args:
        component_type: Type of component to create
        config: Component configuration

    Returns:
        Component instance

    Raises:
        ValueError: If component type is not recognized
    """
    if component_type not in COMPONENT_TYPES:
        raise ValueError(
            f"Unknown component type: {component_type}. "
            f"Valid types: {list(COMPONENT_TYPES.keys())}"
        )
    return COMPONENT_TYPES[component_type](config)
