# Use the superfacility API to interact with NERSC

## Token authentication
- To get a token, please check the readme file in the main page. 
- When using the API, you need to authenticate yourself. The API uses token authentication. The IP address of the client is used to determine the user. For testing I am using `JIRIAF2302` whose IP address is `129.57.178.20`.
- The token is not uploaded to github. Manually copy the files to the `sf-api/key/red` folder.`

## Basic usage
### Check the status of NERSC systems (GET)
To check the status of NERSC systems, please check the function `check_systems` in `sf-api/example.py`. The example output is shown below:
```json
{ 'description': 'System is active',
  'full_name': 'Perlmutter',
  'name': 'perlmutter',
  'notes': [],
  'status': 'active',
  'system_type': 'compute',
  'updated_at': '2023-11-17T07:42:00-08:00'}
```

### List the directories (GET)
To list the directories, please check the function `list_directories` in `sf-api/example.py`. The example output is shown below:
```json
{ 'entries': [ { 'date': '2023-11-26T17:14:54',
                 'group': 'jlabtsai',
                 'hardlinks': 2,
                 'name': '.',
                 'perms': 'drwxrwx---',
                 'size': 4096.0,
                 'user': 'jlabtsai'},
               { 'date': '2023-11-22T09:53:06',
                 'group': 'jlabtsai',
                 'hardlinks': 5,
                 'name': '..',
                 'perms': 'drwxrwx---',
                 'size': 4096.0,
                 'user': 'jlabtsai'},
               { 'date': '2023-11-26T17:12:46',
                 'group': 'jlabtsai',
                 'hardlinks': 1,
                 'name': 'ersap1.slurm',
                 'perms': '-rwxrwx---',
                 'size': 376.0,
                 'user': 'jlabtsai'},
               { 'date': '2023-11-17T06:42:16',
                 'group': 'jlabtsai',
                 'hardlinks': 1,
                 'name': 'ersap2.slurm',
                 'perms': '-rwxrwx---',
                 'size': 373.0,
                 'user': 'jlabtsai'}],
  'error': None,
  'status': 'OK'}
```
### Read the API result (GET)
To check the status of `POST` jobs, insert the `task_id` to the function `read_api_result`. Notice that there will be a delay between the execution and the return of the results. 

### Execute commands (POST)
Please check the functions `run_cmd` in `sf-api/example.py`. The example command and
```json
{'error': None, 'status': 'OK', 'task_id': '212261'}
{ 'id': '212261',
  'result': '{"status": "ok", "output": "#!/bin/bash\\n\\n#echo \\"hello '
            'world!\\" > /global/homes/j/jlabtsai/swif/output.txt\\n#\\n#\\n", '
            '"error": null}',
  'status': 'completed'}
```


### Submit Slurm jobs (POST)
To submit a slurm job, please check the function `submit_slurm_job` in `sf-api/example.py`. The job script should be prepared in advance. To check the status of the job, insert the `task_id` to the function `read_api_result`. The example output is shown below:
```json
{'error': None, 'status': 'OK', 'task_id': '212262'}
{ 'id': '212262',
  'result': '{"status": "ok", "jobid": "18690536", "error": null}',
  'status': 'completed'}
``` 






