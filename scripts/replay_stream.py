#!/usr/bin/env python3
"""Publish generated JSONL to Kafka and measure until all unique events complete."""

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path
from urllib.request import urlopen


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stream", default="build/stream/payments.jsonl")
    parser.add_argument("--manifest", default="build/stream/manifest.json")
    parser.add_argument("--url", default="http://localhost:8080")
    args = parser.parse_args()

    stream_path = Path(args.stream)
    with open(args.manifest, encoding="utf-8") as manifest_file:
        manifest = json.load(manifest_file)
    run_id = manifest["runId"]
    expected = manifest["validUniqueEvents"]

    command = [
        "docker", "compose", "exec", "-T", "kafka",
        "/opt/kafka/bin/kafka-console-producer.sh",
        "--bootstrap-server", "localhost:9092",
        "--topic", "payments.incoming",
    ]
    started = time.perf_counter()
    try:
        with stream_path.open("rb") as input_stream:
            result = subprocess.run(command, stdin=input_stream, check=False)
    except (OSError, subprocess.SubprocessError) as error:
        raise SystemExit(f"Could not publish stream; start Kafka with docker compose up -d: {error}") from error
    if result.returncode:
        raise SystemExit(f"Kafka console producer exited with code {result.returncode}")

    status_url = f"{args.url.rstrip('/')}/api/alerts/stream-status?runId={run_id}"
    deadline = time.monotonic() + 600
    completed = 0
    while time.monotonic() < deadline:
        try:
            with urlopen(status_url, timeout=5) as response:
                status = json.load(response)
            completed = status["completedEvents"]
            if completed >= expected:
                elapsed = time.perf_counter() - started
                print(f"Completed events: {completed}")
                print(f"End-to-end elapsed: {elapsed:.2f} seconds")
                print(f"Throughput: {completed / elapsed:.2f} events/second")
                return
        except (OSError, ValueError, KeyError):
            pass
        time.sleep(0.5)

    print(f"Timed out waiting for {expected} unique events; observed {completed}", file=sys.stderr)
    raise SystemExit(1)


if __name__ == "__main__":
    main()
