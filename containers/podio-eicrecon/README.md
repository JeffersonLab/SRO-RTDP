How to run this:

1. Run the container interactively. Run `docker run -it jlabtsai/eic bash`.
2. For the sender, run `./run-sender.bash`. The port is 44524.
3. For the receiver, run `./run-EICrecon.bash`.

Run the sender with:
```bash
docker run --privileged --net host --rm <cont_id> bash -c "source /etc/profile && ./run-sender.bash"
```

Run the `podiotcp` receiver with:
```bash
docker run --privileged --net host --rm <cont_id> bash -c "source /etc/profile && ./podio2tcp.build/tcp2podio"
```
