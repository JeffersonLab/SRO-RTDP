from fireworks import LaunchPad, Workflow
from monty.serialization import loadfn
from iperf_tasks import *  # Import the task definitions

# Load the workflow from yaml
wf_dict = loadfn("iperf_workflow.yaml")
wf = Workflow.from_dict(wf_dict)

# Initialize and add to LaunchPad
lpad = LaunchPad.auto_load()
lpad.add_wf(wf) 