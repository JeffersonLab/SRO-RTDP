from flask import Flask, render_template, send_file, request
from flask_bootstrap import Bootstrap5
import os
import yaml
from typing import Dict, Any, Union

from .components import WorkflowManager
from .forms import (
    WorkflowMetadataForm, PlatformConfigForm, ComponentForm,
    EdgeForm, ContainerConfigForm
)

app = Flask(__name__)
app.config['SECRET_KEY'] = os.environ.get(
    'SECRET_KEY', 'dev-key-please-change')
bootstrap = Bootstrap5(app)

# Create a workflow manager instance
workflow_manager = WorkflowManager()


@app.route('/')
def index() -> str:
    """Render the main page."""
    return render_template(
        'index.html',
        workflow_metadata_form=WorkflowMetadataForm(obj=workflow_manager),
        platform_form=PlatformConfigForm(obj=workflow_manager.platform),
        component_form=ComponentForm(),
        edge_form=EdgeForm(),
        container_form=ContainerConfigForm(
            image_path=workflow_manager.container_image_path
        ),
        components=workflow_manager.components,
        edges=workflow_manager.edges
    )


@app.route('/api/workflow/metadata', methods=['POST'])
def update_workflow_metadata() -> Union[Dict[str, str], tuple[Dict[str, Any], int]]:
    """Update workflow metadata."""
    form = WorkflowMetadataForm()
    if form.validate_on_submit():
        workflow_manager.name = form.name.data
        workflow_manager.description = form.description.data
        return {"status": "success"}
    return {"status": "error", "errors": form.errors}, 400


@app.route('/api/workflow/platform', methods=['POST'])
def update_platform() -> Union[Dict[str, str], tuple[Dict[str, Any], int]]:
    """Update platform configuration."""
    form = PlatformConfigForm()
    if form.validate_on_submit():
        workflow_manager.platform.name = form.name.data
        workflow_manager.platform.job_runner = form.job_runner.data
        return {"status": "success"}
    return {"status": "error", "errors": form.errors}, 400


@app.route('/api/components', methods=['POST'])
def add_component() -> Union[Dict[str, str], tuple[Dict[str, Any], int]]:
    """Add a new component."""
    try:
        # Get data from form
        component_id = request.form.get('id')
        component_type = request.form.get('type')

        if not component_id or not component_type:
            return {"status": "error", "message": "Missing id or type"}, 400

        # Create resources dictionary
        resources = {
            "partition": request.form.get('partition', 'ifarm'),
            "cpus_per_task": int(request.form.get('cpus_per_task', '4')),
            "mem": request.form.get('mem', '8G')
        }

        workflow_manager.add_component(component_id, component_type, resources)
        return {"status": "success"}
    except ValueError as e:
        return {"status": "error", "message": str(e)}, 400
    except Exception as e:
        return {"status": "error", "message": f"Failed to add component: {str(e)}"}, 400


@app.route('/api/components/<component_id>', methods=['DELETE'])
def remove_component(component_id: str) -> Dict[str, str]:
    """Remove a component."""
    workflow_manager.remove_component(component_id)
    return {"status": "success"}


@app.route('/api/components/<component_id>', methods=['POST'])
def update_component(component_id: str) -> Union[Dict[str, str], tuple[Dict[str, Any], int]]:
    """Update a component's configuration."""
    try:
        # Get data from form
        config = {}

        # Resources
        resources = {
            "partition": request.form.get('partition'),
            "cpus_per_task": int(request.form.get('cpus_per_task')),
            "mem": request.form.get('mem')
        }
        config["resources"] = resources

        # Network (optional)
        port = request.form.get('port')
        if port:
            config["network"] = {
                "port": int(port),
                "bind_address": request.form.get('bind_address', '0.0.0.0')
            }

        # Type-specific configuration
        component_type = request.form.get('type')
        if component_type == 'emulator':
            config["configuration"] = {
                "threads": int(request.form.get('threads', 4)),
                "latency": int(request.form.get('latency', 50)),
                "mem_footprint": float(request.form.get('mem_footprint', 0.05)),
                "output_size": float(request.form.get('output_size', 0.001))
            }
        elif component_type == 'sender':
            data_size = request.form.get('data_size')
            if data_size:
                config["test_data"] = {
                    "size": data_size
                }

        workflow_manager.update_component_config(component_id, config)
        return {"status": "success"}
    except ValueError as e:
        return {"status": "error", "message": str(e)}, 400
    except Exception as e:
        return {"status": "error", "message": f"Failed to update component: {str(e)}"}, 400


@app.route('/api/edges', methods=['POST'])
def add_edge() -> Union[Dict[str, str], tuple[Dict[str, Any], int]]:
    """Add a new edge."""
    # Get data from form
    from_id = request.form.get('from_id')
    to_id = request.form.get('to_id')
    edge_type = request.form.get('type', 'ready')
    condition = request.form.get('condition')  # Get condition from form data

    if not from_id or not to_id:
        return {"status": "error", "message": "Missing from_id or to_id"}, 400

    try:
        workflow_manager.add_edge(from_id, to_id, edge_type, condition)
        return {"status": "success"}
    except ValueError as e:
        return {"status": "error", "message": str(e)}, 400


@app.route('/api/edges/<from_id>/<to_id>', methods=['DELETE'])
def remove_edge(from_id: str, to_id: str) -> Dict[str, str]:
    """Remove an edge."""
    workflow_manager.remove_edge(from_id, to_id)
    return {"status": "success"}


@app.route('/api/workflow/container', methods=['POST'])
def update_container() -> Union[Dict[str, str], tuple[Dict[str, Any], int]]:
    """Update container configuration."""
    form = ContainerConfigForm()
    if form.validate_on_submit():
        workflow_manager.container_image_path = form.image_path.data
        return {"status": "success"}
    return {"status": "error", "errors": form.errors}, 400


@app.route('/api/workflow/config', methods=['GET'])
def get_config() -> Dict[str, Any]:
    """Get the current workflow configuration."""
    return workflow_manager.to_dict()


@app.route('/api/workflow/config/download')
def download_config() -> Any:
    """Download the workflow configuration as YAML."""
    config = workflow_manager.to_dict()
    yaml_str = yaml.dump(config, default_flow_style=False)

    # Create a temporary file
    temp_path = "/tmp/workflow_config.yml"
    with open(temp_path, 'w') as f:
        f.write(yaml_str)

    return send_file(
        temp_path,
        as_attachment=True,
        download_name="workflow_config.yml",
        mimetype="application/x-yaml"
    )


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
