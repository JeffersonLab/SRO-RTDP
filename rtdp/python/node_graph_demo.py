"""Generate data for frontend Grafana node-graph UI using Flask framework."""

# Check the example at \
# https://github.com/hoptical/nodegraph-api-plugin/blob/main/example/api/python/run.py.
# This is at port 5000.

from flask import Flask, jsonify

app = Flask(__name__)


@app.route('/api/graph/fields')
def fetch_graph_fields():
    """Definition of the fields."""

    # Detailed definition at \
    # https://grafana.com/docs/grafana/latest/panels-visualizations/visualizations/node-graph/#data-api.
    nodes_fields = [{"field_name": "id", "type": "string"},
                    {"field_name": "title", "type": "string",
                     },
                    {"field_name": "subTitle", "type": "string"},
                    {"field_name": "mainStat", "type": "string"},
                    {"field_name": "secondaryStat", "type": "number"},
                    {"field_name": "arc__failed",
                     "type": "number", "color": "red", "displayName": "Failed"},
                    {"field_name": "arc__passed",
                     "type": "number", "color": "green", "displayName": "Passed"},
                    {"field_name": "detail__class",
                     "type": "string", "displayName": "Class"}]
    edges_fields = [
        {"field_name": "id", "type": "string"},
        {"field_name": "source", "type": "string"},
        {"field_name": "target", "type": "string"},
        {"field_name": "mainStat", "type": "number"},
        {"field_name": "thickness", "type": "number"},
    ]
    result = {"nodes_fields": nodes_fields,
              "edges_fields": edges_fields}
    return jsonify(result)


@app.route('/api/graph/data')
def fetch_graph_data():
    """Generate data which go to the Grafana end."""

    nodes = [
        {"id": "1", "title": "Source", "subTitle": "",
         "detail__class": "org.jlab.ersap.actor.helloworld.engine.FileReaderEngine",
         "arc__failed": 0.7, "arc__passed": 0.3, "mainStat": "number1"},
        {"id": "2", "title": "HelloWorld", "subTitle": "",
         "detail__class": "org.jlab.ersap.actor.helloworld.engine.HelloWorldEngine",
         "arc__failed": 0.5, "arc__passed": 0.5, "mainStat": "number2"},
        {"id": "3", "title": "Sink", "subTitle": "",
         "detail__class": "org.jlab.ersap.actor.helloworld.engine.PrintStdIOEngine",
         "arc__failed": 0.3, "arc__passed": 0.7, "mainStat": "number3"},
    ]

    # Though the "id" fields are of string type, we still need to set them to look like numbers.
    # Otherwise the frontend will fail to show the graphs.
    edges = [
        {"id": "1", "source": "1", "target": "2", "mainStat": 53, "thickness": 5},
        {"id": "2", "source": "2", "target": "3", "mainStat": 13, "thickness": 1}
        ]
    result = {"nodes": nodes, "edges": edges}
    return jsonify(result)


@app.route('/api/health')
def check_health():
    """Show the status of the API."""
    return "Success!"


app.run(host='0.0.0.0', port=5000)
