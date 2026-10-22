import hashlib
import hmac
import os
import re
import sqlite3
from contextlib import closing

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

SECRET = os.environ.get("HMAC_SECRET")
if not SECRET:
    raise RuntimeError("Set the HMAC_SECRET environment variable first")

DB_PATH = "reports.db"
app = FastAPI()


def init_db():
    with closing(sqlite3.connect(DB_PATH)) as db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS reports (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                number_hash TEXT NOT NULL,
                scam_type   TEXT NOT NULL,
                reported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """)
        db.commit()


init_db()


def normalize(number: str) -> str:
    # Keep digits only, then turn 097... or 260... into one format
    digits = re.sub(r"\D", "", number)
    if digits.startswith("0"):
        digits = "260" + digits[1:]
    return digits


def hash_number(number: str) -> str:
    return hmac.new(SECRET.encode(), normalize(number).encode(), hashlib.sha256).hexdigest()


class ReportIn(BaseModel):
    number: str = Field(min_length=7, max_length=20)
    scam_type: str = "otp_prize"


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/reports", status_code=201)
def create_report(report: ReportIn):
    if len(normalize(report.number)) < 9:
        raise HTTPException(status_code=422, detail="Not a valid phone number")

    with closing(sqlite3.connect(DB_PATH)) as db:
        db.execute(
            "INSERT INTO reports (number_hash, scam_type) VALUES (?, ?)",
            (hash_number(report.number), report.scam_type),
        )
        db.commit()
    return {"status": "recorded"}

@app.get("/lookup")
def lookup(number: str):
    if len(normalize(number)) < 9:
        raise HTTPException(status_code=422, detail="Not a valid phone number")

    with closing(sqlite3.connect(DB_PATH)) as db:
        count = db.execute(
            "SELECT COUNT(*) FROM reports WHERE number_hash = ?",
            (hash_number(number),),
        ).fetchone()[0]

    if count == 0:
        status = "unknown"
    elif count < 3:
        status = "reported"
    else:
        status = "suspected"
    return {"status": status, "reports": count}