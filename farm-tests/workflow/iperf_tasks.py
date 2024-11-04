from fireworks import FiretaskBase, ScriptTask, FWAction
from fireworks.utilities.fw_utilities import explicit_serialize
import socket
from monty.serialization import loadfn, dumpfn
import os
from datetime import datetime

@explicit_serialize
class IperfServerTask(FiretaskBase):
    required_params = [
        "process_exporter_port",
        "app_port",
        "workdir_prefix",
        "process_exporter_sif",
        "iperf3_path"
    ]
    
    optional_params = [
        "config_dir"
    ]
    
    def run_task(self, fw_spec):
        server_script = ScriptTask.from_str(
            f'bash ifarm_iperf3Server.sh '
            f'{self["process_exporter_port"]} '
            f'{self["app_port"]} '
            f'{self["workdir_prefix"]} '
            f'{self["process_exporter_sif"]} '
            f'{self["iperf3_path"]} '
            f'{self.get("config_dir", "config")}'
        )
        server_script.run_task(fw_spec)
        
        import socket
        return FWAction(update_spec={'server_hostname': socket.gethostname()})

@explicit_serialize
class IperfClientTask(FiretaskBase):
    required_params = [
        "process_exporter_port",
        "server_hostname",
        "app_port",
        "workdir_prefix",
        "process_exporter_sif",
        "iperf3_path",
        "test_duration"
    ]
    
    optional_params = [
        "config_dir"
    ]
    
    def run_task(self, fw_spec):
        client_script = ScriptTask.from_str(
            f'bash ifarm_iperf3Client.sh '
            f'{self["process_exporter_port"]} '
            f'{self["server_hostname"]} '
            f'{self["app_port"]} '
            f'{self["workdir_prefix"]} '
            f'{self["process_exporter_sif"]} '
            f'{self["iperf3_path"]} '
            f'{self["test_duration"]} '
            f'{self.get("config_dir", "config")}'
        )
        client_script.run_task(fw_spec)
        
        return FWAction(update_spec={'client_hostname': socket.gethostname()})

@explicit_serialize
class PrometheusTask(FiretaskBase):
    required_params = [
        "prometheus_port",
        "workdir_prefix",
        "process_exporter_port",
        "prometheus_sif"
    ]
    
    optional_params = [
        "config_dir",
        "prom_data_dir"
    ]
    
    def generate_prometheus_config(self, worker_nodes, config_path):
        config = {
            'scrape_configs': [
                {
                    'job_name': 'process-exporter',
                    'static_configs': [{
                        'targets': [f'{node}:{self["process_exporter_port"]}' for node in worker_nodes] + 
                                  [f'localhost:{self["process_exporter_port"]}'],
                        'labels': {
                            'group': 'process-exporter',
                            'cluster': 'ifarm'
                        }
                    }]
                },
                {
                    'job_name': 'prometheus',
                    'static_configs': [{
                        'targets': [f'localhost:{self["prometheus_port"]}'],
                        'labels': {
                            'group': 'prometheus',
                            'cluster': 'ifarm'
                        }
                    }]
                }
            ]
        }
        
        dumpfn(config, config_path, fmt='yaml')

    def run_task(self, fw_spec):
        # Create timestamp for data directory
        timestamp = datetime.utcnow().strftime('%s')
        prom_data_dir = self.get("prom_data_dir", f"prom-data-{timestamp}")
        config_dir = self.get("config_dir", "config")
        
        # Ensure directories exist
        os.makedirs(os.path.join(self["workdir_prefix"], prom_data_dir), exist_ok=True)
        os.makedirs(os.path.join(self["workdir_prefix"], config_dir), exist_ok=True)
        
        # Generate Prometheus config
        config_path = os.path.join(self["workdir_prefix"], config_dir, "prometheus-config.yml")
        worker_nodes = [fw_spec['server_hostname'], fw_spec['client_hostname']]
        self.generate_prometheus_config(worker_nodes, config_path)
        
        # Run Prometheus server
        prom_script = ScriptTask.from_str(
            f'bash prom_server.sh '
            f'{self["prometheus_port"]} '
            f'{self["workdir_prefix"]} '
            f'{self["prometheus_sif"]} '
            f'{prom_data_dir} '
            f'{config_dir}'
        )
        prom_script.run_task(fw_spec)