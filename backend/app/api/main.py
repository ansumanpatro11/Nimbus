from fastapi import FastAPI, Depends, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Dict, List
from app.db import engine, Base, get_db
from app.core.config import get_settings
from app.models.models import Telemetry, Snapshot
from app.schemas.schemas import IngestRequest, SnapshotOut

settings = get_settings()
app = FastAPI(title="SIH Wearable Backend", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[settings.allowed_origins] if settings.allowed_origins != "*" else ["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Simple in-memory WebSocket manager by team
class WSManager:
    def __init__(self):
        self.active: Dict[str, List[WebSocket]] = {}

    async def connect(self, team: str, ws: WebSocket):
        await ws.accept()
        self.active.setdefault(team, []).append(ws)

    def remove(self, team: str, ws: WebSocket):
        if team in self.active and ws in self.active[team]:
            self.active[team].remove(ws)

    async def broadcast(self, team: str, message: dict):
        for ws in list(self.active.get(team, [])):
            try:
                await ws.send_json(message)
            except WebSocketDisconnect:
                self.remove(team, ws)

manager = WSManager()

@app.on_event("startup")
async def on_startup():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

@app.get("/health")
async def health(): 
    return {"ok": True}

def auth_or_401(api_key: str | None):
    if not api_key or api_key != settings.api_key:
        raise HTTPException(status_code=401, detail="Bad API key")

@app.post("/ingest")
async def ingest(data: IngestRequest, api_key: str = None, db: AsyncSession = Depends(get_db)):
    auth_or_401(api_key)

    # persist raw telemetry
    latest = {"uid": data.uid, "team": data.team, "ts": 0,
              "hr": 0, "spo2": 0, "temp": 0, "batt_min": 100, "risk": "GREEN", "score": 0.0, "fall": False}

    for p in data.packets:
        hr = p.vitals.get("hr") if p.vitals else None
        spo2 = p.vitals.get("spo2") if p.vitals else None
        batt = p.batt if p.batt is not None else 0
        temp = p.temp if p.temp is not None else 0

        row = Telemetry(
            uid=data.uid, team=data.team, did=p.did, src=p.src, ts=p.ts,
            batt=batt, hr=hr or 0, spo2=spo2 or 0, temp=temp, imu=p.imu or {}, events={"list": p.events or []}
        )
        db.add(row)

        # maintain latest snapshot heuristics
        latest["ts"] = max(latest["ts"], p.ts)
        if hr is not None: latest["hr"] = hr
        if spo2 is not None: latest["spo2"] = spo2
        if temp is not None: latest["temp"] = temp
        latest["batt_min"] = min(latest["batt_min"], batt if batt else latest["batt_min"])

        # simple rule-based status (placeholder until ML)
        risk = "GREEN"
        if hr and (hr < 45 or hr > 140): risk = "YELLOW"
        if spo2 and spo2 < 93: risk = "YELLOW"
        if spo2 and spo2 < 88: risk = "RED"
        latest["risk"] = max(latest["risk"], risk, key=lambda x: ["GREEN","YELLOW","RED"].index(x))

        # fall via event
        if p.events and ("fall" in p.events or "fall_flag_local" in p.events):
            latest["fall"] = True
            latest["risk"] = "RED"

    # upsert snapshot
    snap = Snapshot(uid=data.uid, team=data.team, ts=latest["ts"], hr=latest["hr"],
                    spo2=latest["spo2"], temp=latest["temp"], batt_min=latest["batt_min"],
                    risk=latest["risk"], score=0.0, fall=latest["fall"])
    # delete old snapshot for this user+team then add new
    await db.execute("DELETE FROM snapshots WHERE uid=:uid AND team=:team", {"uid": data.uid, "team": data.team})
    db.add(snap)
    await db.commit()

    await manager.broadcast(data.team, {"type":"snapshot", "data": latest})
    return {"ok": True}

@app.get("/latest", response_model=list[SnapshotOut])
async def latest(team: str = "default", db: AsyncSession = Depends(get_db)):
    res = await db.execute(
        "SELECT s.uid, s.team, s.ts, s.hr, s.spo2, s.temp, s.batt_min, s.risk, s.score, s.fall "
        "FROM snapshots s WHERE s.team = :team ORDER BY s.uid ASC",
        {"team": team}
    )
    rows = res.all()
    keys = ["uid","team","ts","hr","spo2","temp","batt_min","risk","score","fall"]
    return [SnapshotOut(**{k:v for k,v in zip(keys, r)}) for r in rows]

@app.get("/history")
async def history(uid: str, start_ts: int = 0, end_ts: int = 32503680000000, limit: int = 500,
                  db: AsyncSession = Depends(get_db)):
    q = await db.execute(
        "SELECT ts, hr, spo2, temp, batt, src FROM telemetry WHERE uid=:uid AND ts BETWEEN :a AND :b ORDER BY ts DESC LIMIT :l",
        {"uid":uid,"a":start_ts,"b":end_ts,"l":limit}
    )
    result = [{"ts":r[0], "hr":r[1], "spo2":r[2], "temp":r[3], "batt":r[4], "src":r[5]} for r in q.all()]
    return result

@app.websocket("/live/{team}")
async def ws_live(websocket: WebSocket, team: str):
    await manager.connect(team, websocket)
    try:
        while True:
            await websocket.receive_text()  # keepalive or ignore
    except WebSocketDisconnect:
        manager.remove(team, websocket)
