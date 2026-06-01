#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <vss-jar>" >&2
  exit 2
fi

jar_path="$(realpath "$1")"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d /tmp/vss-12110-smoke.XXXXXX)"
rcon_port="$((25000 + RANDOM % 10000))"
rcon_password="vss-smoke-${RANDOM}-${RANDOM}"

mkdir -p "$test_dir/mods"
printf 'eula=true\n' > "$test_dir/eula.txt"
cat > "$test_dir/server.properties" <<PROPERTIES
online-mode=false
server-port=0
enable-rcon=true
rcon.port=$rcon_port
rcon.password=$rcon_password
level-name=world
PROPERTIES

fabric_server="$root/.cache/fabric-server-mc.1.21.10-loader.0.18.4.jar"
fabric_api="$root/.cache/fabric-api-0.138.4+1.21.10.jar"

if [[ ! -f "$fabric_server" ]]; then
  mkdir -p "$(dirname "$fabric_server")"
  echo "downloading Fabric server launcher"
  curl -L -sS \
    "https://meta.fabricmc.net/v2/versions/loader/1.21.10/0.18.4/1.1.0/server/jar" \
    -o "$fabric_server"
fi

if [[ ! -f "$fabric_api" ]]; then
  mkdir -p "$(dirname "$fabric_api")"
  echo "downloading Fabric API"
  curl -L -sS \
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.138.4+1.21.10/fabric-api-0.138.4+1.21.10.jar" \
    -o "$fabric_api"
fi

cp "$fabric_api" "$test_dir/mods/"
cp "$jar_path" "$test_dir/mods/"

echo "smoke_test_dir=$test_dir"
(
  cd "$test_dir"
  coproc SERVER_PROCESS { java -Xmx1G -jar "$fabric_server" nogui > server.log 2>&1; }
  server_pid=$SERVER_PROCESS_PID
  stop_server() {
    if [[ -n "${server_pid:-}" ]] && kill -0 "$server_pid" 2>/dev/null; then
      printf 'stop\n' >&"${SERVER_PROCESS[1]}" 2>/dev/null || kill "$server_pid" 2>/dev/null || true
      for _ in $(seq 1 20); do
        if ! kill -0 "$server_pid" 2>/dev/null; then
          wait "$server_pid" || true
          return
        fi
        sleep 0.5
      done
      kill "$server_pid" 2>/dev/null || true
      wait "$server_pid" || true
    fi
  }
  trap stop_server EXIT

  for _ in $(seq 1 90); do
    if grep -q 'Done (.*)! For help, type "help"' server.log; then
      rcon_output="$(python3 - "$rcon_port" "$rcon_password" <<'PY'
import socket
import struct
import sys

port = int(sys.argv[1])
password = sys.argv[2]


def packet(request_id: int, kind: int, body: str) -> bytes:
    payload = struct.pack("<ii", request_id, kind) + body.encode() + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def recv_packet(sock: socket.socket) -> tuple[int, int, str]:
    header = sock.recv(4)
    if len(header) < 4:
        raise RuntimeError("short rcon header")
    size = struct.unpack("<i", header)[0]
    data = b""
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise RuntimeError("short rcon body")
        data += chunk
    request_id, kind = struct.unpack("<ii", data[:8])
    return request_id, kind, data[8:-2].decode(errors="replace")


with socket.create_connection(("127.0.0.1", port), timeout=5) as sock:
    sock.sendall(packet(1, 3, password))
    auth_id, _, _ = recv_packet(sock)
    if auth_id == -1:
        raise SystemExit("RCON auth failed")
    for request_id, command in enumerate(["vsslod stats", "vsslod diag"], start=2):
        sock.sendall(packet(request_id, 2, command))
        _, _, body = recv_packet(sock)
        print(f"$ {command}")
        print(body)
        if "Error executing" in body or "Unknown or incomplete command" in body:
            raise SystemExit(1)
        if command == "vsslod stats" and "No players connected with VSS" not in body and "VSS LOD Request Stats" not in body:
            raise SystemExit("missing VSS stats output")
        if command == "vsslod diag" and "VSS LOD Diagnostics" not in body:
            raise SystemExit("missing VSS diagnostics output")
PY
)"
      printf '%s\n' "$rcon_output"
      grep -E 'vss 0\.2\.4\+mc1\.21\.10|Starting VSS LOD request processing service|Done \(' server.log
      exit 0
    fi

    if ! kill -0 "$server_pid" 2>/dev/null; then
      cat server.log
      exit 1
    fi
    sleep 1
  done

  kill "$server_pid" 2>/dev/null || true
  cat server.log
  exit 1
)
