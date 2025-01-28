from flask import Flask, render_template, request, jsonify, send_file
from flask_bootstrap import Bootstrap5
from forms import WorkflowConfigForm
import yaml
import os
from config_generator import generate_config

app = Flask(__name__)
app.config['SECRET_KEY'] = os.environ.get(
    'SECRET_KEY', 'dev-key-please-change')
bootstrap = Bootstrap5(app)


@app.route('/', methods=['GET', 'POST'])
def index():
    form = WorkflowConfigForm()
    if form.validate_on_submit():
        config_data = {
            'workflow': {
                'name': form.workflow_name.data,
                'description': form.workflow_description.data
            },
            'platform': {
                'name': form.platform_name.data,
                'cylc_path': form.cylc_path.data,
                'hosts': form.hosts.data,
                'job_runner': form.job_runner.data
            },
            'resources': {
                'receiver': {
                    'ntasks': form.receiver_ntasks.data,
                    'cpus_per_task': form.receiver_cpus.data,
                    'mem': form.receiver_mem.data,
                    'partition': form.receiver_partition.data,
                    'timeout': form.receiver_timeout.data
                },
                'emulator': {
                    'ntasks': form.emulator_ntasks.data,
                    'cpus_per_task': form.emulator_cpus.data,
                    'mem': form.emulator_mem.data,
                    'partition': form.emulator_partition.data,
                    'timeout': form.emulator_timeout.data
                },
                'sender': {
                    'ntasks': form.sender_ntasks.data,
                    'cpus_per_task': form.sender_cpus.data,
                    'mem': form.sender_mem.data,
                    'partition': form.sender_partition.data,
                    'timeout': form.sender_timeout.data
                }
            },
            'network': {
                'receiver_port': form.receiver_port.data,
                'emulator_port': form.emulator_port.data
            },
            'emulator': {
                'threads': form.emulator_threads.data,
                'latency': form.emulator_latency.data,
                'mem_footprint': form.emulator_mem_footprint.data,
                'output_size': form.emulator_output_size.data
            },
            'test_data': {
                'size': form.test_data_size.data
            },
            'containers': {
                'cpu_emulator': {
                    'image': form.container_image.data,
                    'docker_source': form.docker_source.data
                }
            }
        }

        # Generate the config file
        with open('config.yml', 'w') as f:
            yaml.dump(config_data, f, default_flow_style=False)

        return send_file('config.yml', as_attachment=True)

    return render_template('index.html', form=form)


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
