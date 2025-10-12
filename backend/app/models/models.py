from sqlalchemy import DateTime
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy import Integer, String, Float, BigInteger, JSON, func, Boolean
from app.db import Base

class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    uid: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    name: Mapped[str] = mapped_column(String(128), default="")
    team: Mapped[str] = mapped_column(String(64), default="default")

class Device(Base):
    __tablename__ = "devices"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    did: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    kind: Mapped[str] = mapped_column(String(16)) # 'wrist' or 'chest'
    uid: Mapped[str] = mapped_column(String(64), default="")

class Telemetry(Base):
    __tablename__ = "telemetry"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    uid: Mapped[str] = mapped_column(String(64), index=True)
    team: Mapped[str] = mapped_column(String(64), index=True)
    did: Mapped[str] = mapped_column(String(64), index=True)
    src: Mapped[str] = mapped_column(String(16))  # wrist|chest
    ts: Mapped[int] = mapped_column(BigInteger, index=True)
    batt: Mapped[float] = mapped_column(Float, default=0)
    hr: Mapped[float] = mapped_column(Float, default=0)
    spo2: Mapped[float] = mapped_column(Float, default=0)
    temp: Mapped[float] = mapped_column(Float, default=0)
    imu: Mapped[dict] = mapped_column(JSON, default={})
    events: Mapped[dict] = mapped_column(JSON, default={})
    created_at: Mapped[DateTime] = mapped_column(DateTime(timezone=True), server_default=func.now())

class Snapshot(Base):
    __tablename__ = "snapshots"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    uid: Mapped[str] = mapped_column(String(64), index=True)
    team: Mapped[str] = mapped_column(String(64), index=True)
    ts: Mapped[int] = mapped_column(BigInteger, index=True)
    hr: Mapped[float] = mapped_column(Float, default=0)
    spo2: Mapped[float] = mapped_column(Float, default=0)
    temp: Mapped[float] = mapped_column(Float, default=0)
    batt_min: Mapped[float] = mapped_column(Float, default=0)
    risk: Mapped[str] = mapped_column(String(8), default="GREEN")
    score: Mapped[float] = mapped_column(Float, default=0)
    fall: Mapped[bool] = mapped_column(Boolean, default=False)
