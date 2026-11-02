import hashlib
import hmac
import os
import re
import sqlite3
from contextlib import closing

from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel, Field

SECRET = os.environ.get("HMAC_SECRET")
if not SECRET:
    raise RuntimeError("Set the HMAC_SECRET environment variable first")

DB_PATH = "reports.db"
app = FastAPI()

# First two digits after 260 -> carrier
CARRIERS = {
    "95": "Zamtel", "75": "Zamtel",
    "96": "MTN", "76": "MTN",
    "97": "Airtel", "77": "Airtel",
}


def init_db():
    with closing(sqlite3.connect(DB_PATH)) as db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS reports (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                number_hash   TEXT NOT NULL,
                number_masked TEXT NOT NULL,
                carrier       TEXT NOT NULL,
                device_hash   TEXT NOT NULL,
                scam_type     TEXT NOT NULL,
                reported_day  TEXT NOT NULL DEFAULT (date('now')),
                reported_at   TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (number_hash, device_hash, reported_day)
            )
        """)
        db.commit()


init_db()


def normalize(number: str) -> str:
    digits = re.sub(r"\D", "", number)
    if digits.startswith("0"):
        digits = "260" + digits[1:]
    return digits


def keyed_hash(prefix: str, value: str) -> str:
    return hmac.new(SECRET.encode(), f"{prefix}:{value}".encode(), hashlib.sha256).hexdigest()


def carrier_of(number: str) -> str:
    return CARRIERS.get(number[3:5], "Other")


def mask(number: str) -> str:
    # keep the first 5 and last 3 digits, hide the rest
    return number[:5] + "*" * (len(number) - 8) + number[-3:]


class ReportIn(BaseModel):
    number: str = Field(min_length=7, max_length=20)
    device_id: str = Field(min_length=16, max_length=64)
    scam_type: str = "otp_prize"


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/reports", status_code=201)
def create_report(report: ReportIn, response: Response):
    number = normalize(report.number)
    if len(number) < 9:
        raise HTTPException(status_code=422, detail="Not a valid phone number")

    with closing(sqlite3.connect(DB_PATH)) as db:
        cur = db.execute(
            """INSERT OR IGNORE INTO reports
               (number_hash, number_masked, carrier, device_hash, scam_type)
               VALUES (?, ?, ?, ?, ?)""",
            (
                keyed_hash("number", number),
                mask(number),
                carrier_of(number),
                keyed_hash("device", report.device_id),
                report.scam_type,
            ),
        )
        db.commit()

    if cur.rowcount == 0:
        response.status_code = 200
        return {"status": "already_reported_today"}
    return {"status": "recorded"}


@app.get("/lookup")
def lookup(number: str):
    n = normalize(number)
    if len(n) < 9:
        raise HTTPException(status_code=422, detail="Not a valid phone number")

    with closing(sqlite3.connect(DB_PATH)) as db:
        count = db.execute(
            "SELECT COUNT(DISTINCT device_hash) FROM reports WHERE number_hash = ?",
            (keyed_hash("number", n),),
        ).fetchone()[0]

    if count == 0:
        status = "unknown"
    elif count < 3:
        status = "reported"
    else:
        status = "suspected"
    return {"status": status, "reporting_devices": count}


@app.get("/stats")
def stats():
    with closing(sqlite3.connect(DB_PATH)) as db:
        total = db.execute("SELECT COUNT(*) FROM reports").fetchone()[0]
        numbers = db.execute("SELECT COUNT(DISTINCT number_hash) FROM reports").fetchone()[0]
        per_day = db.execute(
            """SELECT reported_day, COUNT(*) FROM reports
               GROUP BY reported_day ORDER BY reported_day DESC LIMIT 14"""
        ).fetchall()
        by_carrier = db.execute(
            "SELECT carrier, COUNT(*) FROM reports GROUP BY carrier ORDER BY COUNT(*) DESC"
        ).fetchall()
        top = db.execute(
            """SELECT number_masked, carrier, COUNT(DISTINCT device_hash) AS devices
               FROM reports GROUP BY number_hash
               ORDER BY devices DESC, MAX(reported_at) DESC LIMIT 10"""
        ).fetchall()

    return {
        "total_reports": total,
        "distinct_numbers": numbers,
        "per_day": [{"day": d, "reports": c} for d, c in reversed(per_day)],
        "by_carrier": [{"carrier": k, "reports": c} for k, c in by_carrier],
        "top_numbers": [{"number": n, "carrier": k, "devices": d} for n, k, d in top],
    }