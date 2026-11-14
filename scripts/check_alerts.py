#!/usr/bin/env python3
"""Compare alerts exposed by the service with a generated stream manifest."""

import argparse
import json
from collections import Counter
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import urlopen


def fetch_alerts(base_url):
    page_number = 0
    alerts = []
    while True:
        query = urlencode({"page": page_number, "size": 100})
        with urlopen(f"{base_url.rstrip('/')}/api/alerts?{query}", timeout=10) as response:
            result = json.load(response)
        alerts.extend(result.get("content", []))
        if (page_number + 1) * result["size"] >= result["totalElements"]:
            return alerts
        page_number += 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", default="build/stream/manifest.json")
    parser.add_argument("--url", default="http://localhost:8080")
    args = parser.parse_args()

    with open(args.manifest, encoding="utf-8") as manifest_file:
        manifest = json.load(manifest_file)
    expected = manifest["expectedAlerts"]
    try:
        detected = fetch_alerts(args.url)
    except (URLError, TimeoutError) as error:
        raise SystemExit(f"Could not read alerts from {args.url}: {error}") from error

    expected_keys = {(item["type"], item["eventId"]) for item in expected}
    detected_keys = set()
    counts = Counter()
    unexpected = 0
    for alert in detected:
        alert_type = alert["type"]
        if alert_type not in {"NEAR_DUPLICATE", "RAPID_REPEAT"}:
            continue
        counts[alert_type] += 1
        detail = alert.get("details", "")
        event_id = next((candidate for _, candidate in expected_keys if f"eventId={candidate}" in detail), None)
        if event_id is None or (alert_type, event_id) not in expected_keys:
            unexpected += 1
        else:
            detected_keys.add((alert_type, event_id))

    expected_counts = Counter(item["type"] for item in expected)
    print("Alert type       expected  detected")
    for alert_type in sorted(set(expected_counts) | set(counts)):
        print(f"{alert_type:<16} {expected_counts[alert_type]:>8}  {counts[alert_type]:>8}")
    missing = expected_keys - detected_keys
    print(f"Unexpected alerts: {unexpected}")
    print(f"Missing expected alerts: {len(missing)}")
    if missing or unexpected:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
