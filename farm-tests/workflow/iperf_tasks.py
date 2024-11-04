from fireworks import FiretaskBase, ScriptTask, FWAction
from fireworks.utilities.fw_utilities import explicit_serialize

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

@explicit_serialize
class PrometheusTask(FiretaskBase):
    required_params = [
        "prometheus_port",
        "workdir_prefix"
    ]
    
    optional_params = [
        "config_dir"
    ]
    
    def run_task(self, fw_spec):
        prom_script = ScriptTask.from_str(
            f'bash prom_server.sh '
            f'{self["prometheus_port"]} '
            f'{self["workdir_prefix"]} '
            f'{self.get("config_dir", "config")}'
        )
        prom_script.run_task(fw_spec)