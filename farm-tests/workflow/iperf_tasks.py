from fireworks import FiretaskBase, ScriptTask, FWAction
from fireworks.utilities.fw_utilities import explicit_serialize

@explicit_serialize
class IperfServerTask(FiretaskBase):
    required_params = ["process_exporter_port"]
    
    def run_task(self, fw_spec):
        server_script = ScriptTask.from_str(
            f'bash ifarm_iperf3Server.sh {self["process_exporter_port"]}'
        )
        server_script.run_task(fw_spec)
        
        import socket
        return FWAction(update_spec={'server_hostname': socket.gethostname()})

@explicit_serialize
class IperfClientTask(FiretaskBase):
    required_params = ["process_exporter_port", "server_hostname"]
    
    def run_task(self, fw_spec):
        client_script = ScriptTask.from_str(
            f'bash ifarm_iperf3Client.sh {self["process_exporter_port"]} {self["server_hostname"]}'
        )
        client_script.run_task(fw_spec)

@explicit_serialize
class PrometheusTask(FiretaskBase):
    required_params = ["prometheus_port"]
    
    def run_task(self, fw_spec):
        prom_script = ScriptTask.from_str(
            f'bash prom_server.sh {self["prometheus_port"]}'
        )
        prom_script.run_task(fw_spec) 