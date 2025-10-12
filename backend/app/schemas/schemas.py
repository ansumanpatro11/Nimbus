from pydantic import BaseModel
from typing import List, Optional, Dict, Any

class Packet(BaseModel):
    did: str
    ts: int
    src: str
    batt: float | None = None
    vitals: Optional[Dict[str, Any]] = None
    temp: float | None = None
    imu: Optional[Dict[str, Any]] = None
    events: Optional[list] = None

class IngestRequest(BaseModel):
    uid: str
    team: str = "default"
    packets: List[Packet]

class SnapshotOut(BaseModel):
    uid: str
    team: str
    ts: int
    hr: float | None = 0
    spo2: float | None = 0
    temp: float | None = 0
    batt_min: float | None = 0
    risk: str = "GREEN"
    score: float = 0
    fall: bool = False
