from collections import defaultdict, deque
from statistics import mean
from typing import Deque

from fastapi import FastAPI
from pydantic import BaseModel


class StepEvent(BaseModel):
    workflowId: str
    stepName: str
    stepType: str
    type: str
    durationMillis: int | None = None


app = FastAPI(title="Helios anomaly detector")
durations: dict[str, Deque[int]] = defaultdict(lambda: deque(maxlen=100))
retry_counter: dict[str, int] = defaultdict(int)
alerts: list[dict] = []


@app.post("/events/step")
def observe_step_event(event: StepEvent) -> dict:
    if event.type == "SUCCEEDED" and event.durationMillis is not None:
        baseline = mean(durations[event.stepType]) if durations[event.stepType] else event.durationMillis
        durations[event.stepType].append(event.durationMillis)
        if baseline > 0 and event.durationMillis > baseline * 3:
            alerts.append({
                "kind": "duration_anomaly",
                "workflowId": event.workflowId,
                "stepName": event.stepName,
                "durationMillis": event.durationMillis,
                "baselineMillis": baseline,
            })

    if event.type == "RETRY":
        retry_counter[event.workflowId] += 1
        if retry_counter[event.workflowId] > 3:
            alerts.append({
                "kind": "retry_spike",
                "workflowId": event.workflowId,
                "retries": retry_counter[event.workflowId],
            })

    return {"accepted": True, "alerts": len(alerts)}


@app.get("/alerts")
def list_alerts() -> list[dict]:
    return alerts
