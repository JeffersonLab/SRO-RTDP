from authlib.integrations.requests_client import OAuth2Session
from authlib.oauth2.rfc7523 import PrivateKeyJWT
from pprint import pprint
from time import sleep


token_url = "https://oidc.nersc.gov/c2id/token"
client_id = "eu2ajfe5zyqfs"
private_key = open("/workspaces/superfacility/sf-api/key/red/priv_key.pem").read()

session = OAuth2Session(
    client_id, 
    private_key, 
    PrivateKeyJWT(token_url),
    grant_type="client_credentials",
    token_endpoint=token_url
)
session.fetch_token()


def check_systems():
    verb = "status"
    system = "perlmutter"
    url = f"https://api.nersc.gov/api/v1.2/{verb}/{system}"
    resp = session.get(url)
    json = resp.json()
    # indent=2 for pretty print of json response 
    pprint(json, indent=2)

def list_directories(dir_path):
    verb = "utilities/ls"    
    system = "perlmutter"
    url = f"https://api.nersc.gov/api/v1.2/{verb}/{system}/{dir_path}"
    resp = session.get(url)
    json = resp.json()
    # indent=2 for pretty print of json response 
    pprint(json, indent=2)

def run_cmd():
    verb = "utilities/command"
    system = "perlmutter"
    url = f"https://api.nersc.gov/api/v1.2/{verb}/{system}"

    cmd = "cat /global/homes/j/jlabtsai/swif/cmd.sh"
    resp = session.post(url, data={"executable": cmd})
    json = resp.json()
    # indent=2 for pretty print of json response 
    pprint(json, indent=2)

    sleep(10)
    task_id = json["task_id"]
    read_api_result(task_id)



def read_api_result(task_id):
    # There is a delay between when the task is submitted and when the result is available
    url = f"https://api.nersc.gov/api/v1.2/tasks/{task_id}"
    resp = session.get(url)
    json = resp.json()
    pprint(json, indent=2)


def submit_slurm_job():
    verb = "compute/jobs"
    system = "perlmutter"
    url = f"https://api.nersc.gov/api/v1.2/{verb}/{system}"

    batch_job_script = "/global/homes/j/jlabtsai/run-vk/slurm/ersap1.slurm"
    resp = session.post(url, data={"job": batch_job_script, "isPath": True})
    json = resp.json()
    # indent=2 for pretty print of json response 
    pprint(json, indent=2)

    sleep(10)
    task_id = json["task_id"]
    read_api_result(task_id)


if __name__ == "__main__":
    # check_systems()
    # list_directories("/global/homes/j/jlabtsai/run-vk/slurm")
    run_cmd()
    # submit_slurm_job()

