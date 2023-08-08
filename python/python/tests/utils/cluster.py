import os
import subprocess
import sys

from pybushka.config import AddressInfo

SCRIPT_FILE = os.path.abspath(f"{__file__}/../../../../../utils/cluster_manager.py")


class RedisCluster:
    def __init__(self, tls) -> None:
        self.tls = tls
        args_list = [sys.executable, SCRIPT_FILE]
        if tls:
            args_list.append("--tls")
        args_list.append("start")
        p = subprocess.Popen(
            args_list,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        output, err = p.communicate(timeout=20)
        if p.returncode != 0:
            raise Exception(f"Failed to create a CME cluster. Executed: {p}:\n{err}")
        self.parse_cluster_script_start_output(output)

    def parse_cluster_script_start_output(self, output: str):
        assert "CLUSTER_FOLDER" in output and "CLUSTER_NODES" in output
        lines_output = output.splitlines()
        for line in lines_output:
            if "CLUSTER_FOLDER" in line:
                splitted_line = line.split("CLUSTER_FOLDER=")
                assert len(splitted_line) == 2
                self.cluster_folder = splitted_line[1]
            if "CLUSTER_NODES" in line:
                nodes_list = []
                splitted_line = line.split("CLUSTER_NODES=")
                assert len(splitted_line) == 2
                nodes_addresses = splitted_line[1].split(",")
                assert len(nodes_addresses) > 0
                for addr in nodes_addresses:
                    host, port = addr.split(":")
                    nodes_list.append(AddressInfo(host, int(port)))
                self.nodes_addr = nodes_list

    def __del__(self):
        args_list = [sys.executable, SCRIPT_FILE]
        if self.tls:
            args_list.append("--tls")
        args_list.extend(["stop", "--cluster-folder", self.cluster_folder])
        p = subprocess.Popen(
            args_list,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        output, err = p.communicate(timeout=20)
        if p.returncode != 0:
            raise Exception(
                f"Failed to stop CME cluster {self.cluster_folder}. Executed: {p}:\n{err}"
            )