podman compose down
podman rmi -f $(podman images | grep none | tr -s ' ' | cut -d ' ' -f 3)
podman rmi -f $(podman images | grep localhost | tr -s ' ' | cut -d ' ' -f 3)
