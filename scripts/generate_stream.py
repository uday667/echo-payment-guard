#!/usr/bin/env python3
"""Write a deterministic 10k+ synthetic Kafka JSONL stream and alert manifest."""

import argparse
import json
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path


def event(event_id, debtor, creditor, amount, initiated_at):
    return {
        "eventId": event_id,
        "debtorAccount": debtor,
        "creditorAccount": creditor,
        "amount": amount,
        "currency": "USD",
        "channel": "SYNTHETIC",
        "reference": f"SYN-{event_id}",
        "initiatedAt": initiated_at.isoformat().replace("+00:00", "Z"),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=10_000)
    parser.add_argument("--output", type=Path, default=Path("build/stream"))
    args = parser.parse_args()
    if args.count < 10_000:
        parser.error("--count must be at least 10000")

    args.output.mkdir(parents=True, exist_ok=True)
    run_id = uuid.uuid4().hex[:12]
    base = datetime(2026, 11, 14, tzinfo=timezone.utc)
    events = []
    for index in range(args.count):
        events.append(event(
            f"{run_id}-base-{index:06d}",
            f"1000000000{index:06d}",
            f"2000000000{index:06d}",
            10 + (index % 9000) / 100,
            base + timedelta(seconds=index * 2),
        ))

    seeded = base + timedelta(days=2)
    near_first = event(f"{run_id}-near-001", "3100000000000001", "3200000000000001",
                       125.75, seeded)
    near_second = event(f"{run_id}-near-002", "3100000000000001", "3200000000000001",
                        125.75, seeded + timedelta(minutes=4))
    events.extend([near_first, near_second, dict(near_second)])

    rapid_events = []
    rapid_time = seeded + timedelta(days=1)
    for index in range(7):
        rapid_events.append(event(
            f"{run_id}-rapid-{index + 1:02d}",
            "4100000000000001",
            "4200000000000001",
            200 + index * 17,
            rapid_time + timedelta(seconds=index * 30),
        ))
    events.extend(rapid_events)

    malformed = ["{not-json", '{"eventId":"missing-payment-fields"}', "[]", '{"eventId":']
    stream_path = args.output / "payments.jsonl"
    with stream_path.open("w", encoding="utf-8") as stream:
        for item in events:
            stream.write(json.dumps(item, separators=(",", ":")) + "\n")
        for line in malformed:
            stream.write(line + "\n")

    manifest = {
        "runId": run_id,
        "validUniqueEvents": args.count + 2 + len(rapid_events),
        "exactEventIdReplays": 1,
        "malformedMessages": len(malformed),
        "expectedAlerts": [
            {"type": "NEAR_DUPLICATE", "eventId": f"{run_id}-near-002"},
            {"type": "RAPID_REPEAT", "eventId": f"{run_id}-rapid-05"},
        ],
    }
    manifest_path = args.output / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(events) + len(malformed)} lines to {stream_path}")
    print(f"Wrote expected alert manifest to {manifest_path}")


if __name__ == "__main__":
    main()
