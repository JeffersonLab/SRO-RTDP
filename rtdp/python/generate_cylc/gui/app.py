from flask import Flask, render_template, request, jsonify, send_file
from flask_bootstrap import Bootstrap5
import os
import yaml
from typing import Dict, Any

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
def update_workflow_metadata() -> Dict[str, Any]:
    """Update workflow metadata."""
    form = WorkflowMetadataForm()
    if form.validate_on_submit():
        workflow_manager.name = form.name.data
        workflow_manager.description = form.description.data
        return {"status": "success"}
    return {"status": "error", "errors": form.errors}, 400


@app.route('/api/workflow/platform', methods=['POST'])
def update_platform() -> Dict[str, Any]:
    """Update platform configuration."""
    form = PlatformConfigForm()
    if form.validate_on_submit():
        workflow_manager.platform.name = form.name.data
        workflow_manager.platform.job_runner = form.job_runner.data
        return {"status": "success"}
    return {"status": "error", "errors": form.errors}, 400


@app.route('/api/components', methods=['POST'])
def add_component() -> Dict[str, Any]:
    """Add a new component."""
    form = ComponentForm()
    if form.validate_on_submit():
        resources = {
            "partition": form.partition.data,
            "cpus_per_task": form.cpus_per_task.data,
            "mem": form.mem.data
        }
        try:
            workflow_manager.add_component(
                form.id.data, form.type.data, resources)

            # Add component-specific configuration
            config: Dict[str, Any] = {}

            if form.port.data:
                config["network"] = {
                    "port": form.port.data,
                    "bind_address": form.bind_address.data
                }

            if form.type.data == "emulator":
                config["configuration"] = {
                    "threads": form.threads.data,
                    "latency": form.latency.data,
                    "mem_footprint": form.mem_footprint.data,
                    "output_size": form.output_size.data
                }

            if form.type.data == "sender":
                config["test_data"] = {
                    "size": form.data_size.data
                }

            if config:
                workflow_manager.update_component_config(form.id.data, config)

            return {"status": "success"}
        except ValueError as e:
            return {"status": "error", "message": str(e)}, 400
    return {"status": "error", "errors": form.errors}, 400


@app.route('/api/components/<component_id>', methods=['DELETE'])
def remove_component(component_id: str) -> Dict[str, Any]:
    """Remove a component."""
    workflow_manager.remove_component(component_id)
    return {"status": "success"}


@app.route('/api/edges', methods=['POST'])
def add_edge() -> Dict[str, Any]:
    """Add a new edge."""
    form = EdgeForm()
    form.from_id.choices = [(id, id)
                            for id in workflow_manager.components.keys()]
    form.to_id.choices = [(id, id)
                          for id in workflow_manager.components.keys()]

    if form.validate_on_submit():
        try:
            workflow_manager.add_edge(
                form.from_id.data,
                form.to_id.data,
                form.type.data,
                form.condition.data if form.condition.data else None
            )
            return {"status": "success"}
        except ValueError as e:
            return {"status": "error", "message": str(e)}, 400
    return {"status": "error", "errors": form.errors}, 400


@app.route('/api/edges/<from_id>/<to_id>', methods=['DELETE'])
def remove_edge(from_id: str, to_id: str) -> Dict[str, Any]:
    """Remove an edge."""
    workflow_manager.remove_edge(from_id, to_id)
    return {"status": "success"}


@app.route('/api/workflow/container', methods=['POST'])
def update_container() -> Dict[str, Any]:
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
