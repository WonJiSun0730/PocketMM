"""Offline QA accounting only. Never reads API keys or sends network requests."""
import argparse
import csv
import json
from collections import defaultdict
from datetime import datetime
from decimal import Decimal
from pathlib import Path


def audit(run, prices):
    rows = list(csv.DictReader((run / "calls.csv").open(encoding="utf-8-sig")))
    grouped = defaultdict(lambda: {"calls": 0, "usd": Decimal(0), "input": 0,
                                   "cached": 0, "cache_write": 0, "output": 0})
    for row in rows:
        group = grouped[row["purpose"]]
        group["calls"] += 1
        group["usd"] += Decimal(row["usd"])
        for key in ("input", "cached", "cache_write", "output"):
            group[key] += int(row[key])
    review = [r for r in rows if r["purpose"] == "SCENE_CONTINUITY"]
    estimates = {}
    for model in ("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna"):
        p = prices[model]
        cost = sum((Decimal(int(r["input"])-int(r["cached"])-int(r["cache_write"])) * Decimal(str(p["input"]))
                    + Decimal(r["cached"]) * Decimal(str(p["cached"]))
                    + Decimal(r["cache_write"]) * Decimal(str(p["written"]))
                    + Decimal(r["output"]) * Decimal(str(p["output"]))) / 1_000_000 for r in review)
        estimates[model] = {"reviewUsd": cost, "basis": "SAME_RECORDED_TOKENS_NOT_MODEL_QUALITY_OR_NEW_REQUEST_BILLING"}
    events = [json.loads(line) for line in (run / "ledger.jsonl").read_text(encoding="utf-8").splitlines()]
    starts = {e["callId"]: e for e in events if e["phase"] == "REQUEST_STARTED"}
    latency = []
    for e in events:
        if e["purpose"] == "SCENE_CONTINUITY" and e["phase"] == "RESPONSE_RECEIVED":
            start = starts[e["callId"]]
            latency.append(round((datetime.fromisoformat(e["at"].replace("Z", "+00:00"))
                                  - datetime.fromisoformat(start["at"].replace("Z", "+00:00"))).total_seconds(), 6))
    completed = list(csv.DictReader((run / "turns.csv").open(encoding="utf-8-sig")))
    complete_turns = {r["turn"] for r in completed}
    total = sum(Decimal(r["usd"]) for r in rows)
    complete_cost = sum(Decimal(r["usd"]) for r in rows if r["turn"] in complete_turns)
    service_cost = sum(Decimal(r["usd"]) for r in rows
                       if r["turn"] in complete_turns and r["role"] != "PERSONA")
    request_shapes = []
    for row in review:
        request = json.loads((run / "requests" / (row["call_id"] + ".json")).read_text(encoding="utf-8"))
        evidence = json.loads(request["input"][-1]["content"])
        request_shapes.append({"callId": row["call_id"], "turn": row["turn"],
                               "model": request["model"], "mode": evidence["mode"],
                               "factCount": len(evidence["facts"]),
                               "evidenceKeyOrder": list(evidence),
                               "explicitCacheOptions": request.get("prompt_cache_options")})
    g = grouped["SCENE_CONTINUITY"]
    return {"sourceRun": run.name, "callCount": len(rows), "totalUsd": total,
            "completedTurns": len(completed), "completedUsdIncludingPlayer": complete_cost,
            "completedServiceUsdExcludingPlayer": service_cost,
            "incompleteUsd": total-complete_cost, "groups": dict(grouped),
            "reviewCachedFraction": g["cached"]/g["input"],
            "reviewWriteUsdAtRecordedSolRate": Decimal(g["cache_write"])*Decimal("5")/1_000_000,
            "reviewLatencySeconds": latency, "modelCounterfactuals": estimates,
            "recordedRequestShapes": request_shapes,
            "limitations": ["No paid calls; semantic model quality is unmeasured.",
                            "Cache layout and smaller output savings are not added to model-only estimates.",
                            "Recorded QA USD is a calculated ledger amount, not an invoice reconciliation."]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("--prices", type=Path, default=Path(__file__).with_name("qa-ui-prices.example.json"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run, json.loads(args.prices.read_text(encoding="utf-8-sig")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, default=str, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, default=str, ensure_ascii=False, indent=2))
