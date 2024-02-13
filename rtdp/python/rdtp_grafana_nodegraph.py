"""Generate data for frontend Grafana node-graph UI using Flask framework."""

import logging
import random

# For node-graph UI example, check \
# https://github.com/hoptical/nodegraph-api-plugin/blob/main/example/api/python/run.py.
# This is at port 5000.
from flask import Flask, jsonify

# This module assumes there is a Prometheus backend exist on port 9090 (can be tuned).
# Requires the prometheus-api-client by "pip install prometheus-api-client".
from prometheus_api_client import PrometheusConnect


logger = logging.getLogger(__name__)

app = Flask(__name__)

PROMETHEUS_URL = 'http://127.0.0.1:9090'  # the default local Prometheus port
prometheus = PrometheusConnect(url=PROMETHEUS_URL, disable_ssl=True)


def get_proxy_nodes_mainstat(n):
    """
    A proxy to genertate some data for all nodes' mainStat based on the local Prometheus metrics.
    """
    ### NOTE: the proxy data is generated based on the daosfs0[2-4] servers.
    ### It CAN BE BROKEN in the future!!!
    ### It must be replaced with real data when in production!!!
    stats = []
    for _ in range(n):
        rand_idx = random.randint(2, 4)  # to match the DAOS server names
        # Use the same query command as at the Grafana side, except replace {} with {{}}
        query_cmd = f'sum by (instance) \
            (engine_net_req_timeout{{instance="daosfs0{rand_idx}:9191"}})'

        query_res = prometheus.custom_query(query=query_cmd)
        # A sample return:
        # [{'metric': {'instance': 'daosfs02:9191'}, 'value': [1707506553.457, '1431']}]
        # The return is a list of dictionaries, and the "value" is a string.
        logger.debug("Return query result: [[ %s ]]", query_res)
        stats.append(int(query_res[0]['value'][1]))

    # print(stats)
    return stats


def get_proxy_edges_mainstat(n):
    """
    A proxy to genertate some data for all edge' mainStat based on the local Prometheus metrics.
    """
    ### NOTE: the proxy data is generated based on the daosfs0[2-4] servers.
    ### It CAN BE BROKEN in the future!!!
    ### It must be replaced with real data when in production!!!
    stats = []
    for _ in range(n):
        rand_idx = random.randint(2, 4)  # to match the DAOS server names
        query_cmd = f'engine_nvme_temp_current{{instance="daosfs0{rand_idx}:9191",\
            device="0000:9c:00.0"}} - 273.15'

        query_res = prometheus.custom_query(query=query_cmd)
        logger.debug("Return query result: [[ %s ]]", query_res)
        # print(query_res)
        # A sample return:
        # [{'metric':\
        # {'device': '0000:9c:00.0', 'instance': 'daosfs03:9191', 'job': 'daos', 'rank': '5'},\
        # 'value': [1707509012.26, '21.850000000000023']}]
        # The return is a list of dictionaries, and the "value" is a string.
        stats.append(int(float(query_res[0]['value'][1])))
    # print(stats)
    return stats

def get_nodegraph_app(flowchart_nodes):
    """The Flask backend to fetch metrics from Prometheus DB and construct
    Grafana nodegraph-api data based on the parsed flowchart nodes.

    Args:
        - flowchart_nodes: The node list parsed from the YAML configuration file.

    Returns:
        - app: The created Flask application at port 5000.
    """

    @app.route('/api/graph/fields')
    def fetch_graph_fields():
        """Definition of the fields."""

        # Detailed rules/definitions are at \
        # https://grafana.com/docs/grafana/latest/panels-visualizations/visualizations/node-graph/#data-api.
        nodes_fields = [{"field_name": "id", "type": "string"},
                        {"field_name": "title", "type": "string"},
                        {"field_name": "mainstat", "type": "number"},
                        {"field_name": "arc__failed",\
                         "type": "number", "color": "red", "displayName": "Failed"},
                        {"field_name": "arc__passed",\
                         "type": "number", "color": "green", "displayName": "Passed"},
                        {"field_name": "detail__class",\
                         "type": "string", "displayName": "Class"}]
        edges_fields = [
            {"field_name": "id", "type": "string"},
            {"field_name": "source", "type": "string"},
            {"field_name": "target", "type": "string"},
            {"field_name": "mainstat", "type": "number"},
            {"field_name": "thickness", "type": "number"},
        ]
        result = {"nodes_fields": nodes_fields,
                "edges_fields": edges_fields}
        return jsonify(result)


    @app.route('/api/graph/data')
    def fetch_graph_data():
        """Generate data which go to the Grafana end."""

        n = len(flowchart_nodes)  # total number of nodes

        data_nodes = []
        data_edges = []

        # NOTE: they are all fake data for demonstration now.
        nodes_mainstat = get_proxy_nodes_mainstat(n)
        edges_mainstat = get_proxy_edges_mainstat(n - 1)

        # Construct the nodes fields
        for i in range(n):
            # All values in the "arc__" fields must add up to 1.
            data_nodes.append({
                "id": str(i + 1),
                "title": flowchart_nodes[i].name,
                "mainstat": nodes_mainstat[i],
                "arc__failed": 0.7,
                "arc__passed": 0.3,
                "detail__class": flowchart_nodes[i].cls
                })

        # Though the "id" fields are of string type, we still need to set them to look like numbers.
        # Otherwise the frontend will fail to show the graphs.
        for i in range(1, n):
            data_edges.append({
                "id": str(i),
                "source": str(i),
                "target": str(i + 1),
                "mainstat": edges_mainstat[i - 1],
                "thickness": edges_mainstat[i - 1] // 10
            })

        result = {"nodes": data_nodes, "edges": data_edges}
        return jsonify(result)


    @app.route('/api/health')
    def check_health():
        """Show the status of the API."""
        return "Success!"

    return app
