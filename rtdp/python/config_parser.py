# Author: xmei@jlab.org

"""
Read the configurations from yaml file and visualize it as DAG.
"""

import yaml
import json
from graphviz import Digraph
from abc import ABC, abstractmethod

class ConfigReader(ABC):
    def __init__(self, filepath):
        self.config_data = self._get_config(filepath)
        if not self.config_data:
            print("Error: incorrect yaml file!")
            exit(ValueError)
        # self._pprint_config()

    def _get_config(self, filepath):
        """Parse the configuration file into a dictionary using the pyYAML module.
        """
        with open(filepath, 'r') as file:
            try:
                config_data = yaml.safe_load(file)
                return config_data
            except yaml.YAMLError as e:
                print("Error reading YAML file:", e)
                return None

    def _pprint_config(self):
        """Print the raw parsed dictionary in json format."""
        print(json.dumps(self.config_data, indent=4))

    @abstractmethod
    def get_flowchart_nodes(self):
        """Translate the input yml configuration file into a flowchart and extract the flowchart nodes.
        Details are defined in the children classes."""
        pass
    
    def graphviz_flowchart(self):
        """Visualize the configuration file that the name of each service is treated as a node in a DAG."""
        # TODO: this function is not called  by the main now.
        node_list = self.get_flowchart_nodes()

        # graphviz examples: https://graphviz.readthedocs.io/en/stable/examples.html
        g = Digraph('config',
                    node_attr={'color': 'lightblue2', 'style': 'filled'},
                    graph_attr={'rankdir': 'LR'})  # "rankdir" is for the direction
        g.attr(size='20,20')

        for i in range(len(node_list) - 1):
            g.edge(node_list[i].name, node_list[i + 1].name)
        
        # It will generate a pdf file named as config.gz.pdf in the same path. Also a config.gv in dot.
        # TODO: better handling of filename, saved path, etc. May take parameters from cli args.
        g.view()

    def get_cytoscape_elements(self):
        """
        Transfer the node list into a cytoscape-format dictionary array.
        """
        r = []
        node_list = self.get_flowchart_nodes()
        n = len(node_list)

        # Dash Cytoscape ref: https://dash.plotly.com/cytoscape/elements

        # The node elements
        for i in range(n):
            r.append({
                'data': {'id': node_list[i].name, 'label': node_list[i].name},
                'position': {'x': 20 + 50 * i, 'y': 50}
                })

        # The edge elements
        for i in range(n - 1):
            r.append({'data': {'source': node_list[i].name, 'target': node_list[i + 1].name}})

        return r
    
    def print_cytoscape_elements(self):
        print(json.dumps(self.get_cytoscape_elements(), indent=4))


class ERSAPFlowchartNode:
    def __init__(self, item):
        """Definition of an ERSAP flowchart node. Currently it's only for visulization, but it may support
        services launching in the future (needs to figure out parameter extracting first).

        Args:
            item: A dictionary entry parsed by pyYAML.
        """
        self._validate_required(item)
        self.name = item["name"]
        self.lan = item["language"] if "language" in item else "java" # language
        self.cls = item["class"]
        self.parameters = ""  # TODO: extract parameters for an ERSAP service

    def _validate_required(self, item):
        # "name" and "class" are the two required fields now
        for f in ["name", "class"]:
            if f not in item:
                print(f"Error: '{f}' is required in the service module {item}!")
                exit(KeyError)


class ERSAPReader(ConfigReader):
    def __init__(self, filepath):
        super().__init__(filepath)

    def _validate_ioservices(self):
        """Validate the config yaml file has "io-services" and its sub "reader" and "writer."""
        if "io-services" not in self.config_data:
            print(f"Error: 'io-services' is required in ERSAP configuration!")
            return False
        if "reader" not in self.config_data["io-services"]:
            print(f"Error: 'reader' is required in 'io-services'!")
            return False
        if "writer" not in self.config_data["io-services"]:
            print(f"Error: 'writer' is required in 'io-services'!")
            return False
        return True

    def get_flowchart_nodes(self):
        """Extract the io-services and services in the ERSAP configuration.
        
        Returns:
            node_list: A list where each element is an ERSAPFlowchartNode.
        """
        if not self._validate_ioservices():
            exit(KeyError)

        node_list = []

        # EARSAP io-services reader, the 1st node in the flowchart.
        node_list.append(ERSAPFlowchartNode(self.config_data["io-services"]["reader"]))
        
        for service in self.config_data["services"]:
            node_list.append(ERSAPFlowchartNode(service))

        # EARSAP io-services writer, the last node in the flowchart.
        node_list.append(ERSAPFlowchartNode(self.config_data["io-services"]["writer"]))

        return node_list

    def print_nodes(self):
        node_list = self.get_flowchart_nodes()

        print("\n\nThe ERSAP services:\n")
        for i in node_list:
            print(i.name)
            print(f'  class: {i.cls}\n  language: {i.lan}\n  parameters: {i.parameters}\n')