from flask import Flask, render_template, send_file, request, jsonify
from flask_bootstrap import Bootstrap5
import os
from .components import WorkflowManager
from .graph_generator import generate_workflow_graph, validate_workflow

app = Flask(__name__, static_folder='static')
app.config['SECRET_KEY'] = os.environ.get(
    'SECRET_KEY', 'dev-key-please-change')
bootstrap = Bootstrap5(app)

# Create a workflow manager instance
workflow_manager = WorkflowManager()


@app.route('/', methods=['GET'])
def index():
    return render_template('index.html',
                           components=workflow_manager.components,
                           connections=workflow_manager.connections)


@app.route('/api/components', methods=['POST'])
def add_component():
    data = request.json
    name = data.get('name')
    component_type = data.get('type')

    if not name or not component_type:
        return jsonify({'error': 'Missing name or type'}), 400

    workflow_manager.add_component(name, component_type)
    return jsonify({'status': 'success'})


@app.route('/api/components/<name>', methods=['DELETE'])
def remove_component(name):
    workflow_manager.remove_component(name)
    return jsonify({'status': 'success'})


@app.route('/api/connections', methods=['POST'])
def add_connection():
    data = request.json
    source = data.get('source')
    target = data.get('target')

    if not source or not target:
        return jsonify({'error': 'Missing source or target'}), 400

    if workflow_manager.add_connection(source, target):
        return jsonify({'status': 'success'})
    return jsonify({'error': 'Invalid connection'}), 400


@app.route('/api/connections', methods=['DELETE'])
def remove_connection():
    data = request.json
    source = data.get('source')
    target = data.get('target')

    if not source or not target:
        return jsonify({'error': 'Missing source or target'}), 400

    workflow_manager.remove_connection(source, target)
    return jsonify({'status': 'success'})


@app.route('/api/component-config/<name>', methods=['PUT'])
def update_component_config(name):
    data = request.json
    workflow_manager.update_component_config(name, data)
    return jsonify({'status': 'success'})


@app.route('/api/platform-config', methods=['PUT'])
def update_platform_config():
    data = request.json
    workflow_manager.update_platform_config(data)
    return jsonify({'status': 'success'})


@app.route('/api/container-config', methods=['PUT'])
def update_container_config():
    data = request.json
    workflow_manager.update_container_config(data)
    return jsonify({'status': 'success'})


@app.route('/api/test-data', methods=['PUT'])
def update_test_data():
    data = request.json
    workflow_manager.update_test_data(data)
    return jsonify({'status': 'success'})


@app.route('/api/validate', methods=['GET'])
def validate():
    components = {
        name: {'type': comp.type}
        for name, comp in workflow_manager.components.items()
    }

    is_valid = validate_workflow(components, workflow_manager.connections)
    return jsonify({'valid': is_valid})


@app.route('/api/generate-config', methods=['POST'])
def generate_config():
    # Validate the workflow first
    components = {
        name: {'type': comp.type}
        for name, comp in workflow_manager.components.items()
    }

    if not validate_workflow(components, workflow_manager.connections):
        return jsonify({'error': 'Invalid workflow configuration'}), 400

    # Generate the graph visualization
    generate_workflow_graph(components, workflow_manager.connections)

    # Generate and save the configuration
    workflow_manager.save_config('config.yml')

    return send_file('config.yml', as_attachment=True)


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
