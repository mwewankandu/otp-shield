import hashlib
import hmac
import os
import re
import secrets
from pathlib import Path

import psycopg
from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.responses import FileResponse
from fastapi.security import HTTPBasic, HTTPBasicCredentials
from pydantic import BaseModel, Field
import time
from collections import defaultdict, deque


def require_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Set the {name} environment variable first")
    return value


SECRET = require_env("HMAC_SECRET")
DATABASE_URL = require_env("DATABASE_URL")
ADMIN_PASSWORD = require_env("ADMIN_PASSWORD")

app = FastAPI()
security = HTTPBasic()

# First two digits after 260 -> carrier
CARRIERS = {
    "95": "Zamtel", "75": "Zamtel",
    "96": "MTN", "76": "MTN",
    "97": "Airtel", "77": "Airtel",
}

# --- Rate limiting: at most RATE_LIMIT requests per IP per hour ---
RATE_LIMIT = int(os.environ.get("RATE_LIMIT", "30"))
WINDOW_SECONDS = 3600
_hits: dict[str, deque] = defaultdict(deque)


def client_ip(request: Request) -> str:
    # Cloudflare sets these headers; a client cannot forge them.
    return (
        request.headers.get("cf-connecting-ip")
        or request.headers.get("true-client-ip")
        or (request.client.host if request.client else "unknown")
    )


def rate_limit(request: Request):
    ip = client_ip(request)
    now = time.monotonic()
    hits = _hits[ip]

    # forget requests older than the window
    while hits and now - hits[0] > WINDOW_SECONDS:
        hits.popleft()

    if len(hits) >= RATE_LIMIT:
        retry = int(WINDOW_SECONDS - (now - hits[0])) + 1
        raise HTTPException(
            status_code=429,
            detail="Too many requests. Try again later.",
            headers={"Retry-After": str(retry)},
        )
    hits.append(now)

    # keep memory small: drop IPs with no recent requests
    if len(_hits) > 10_000:
        for key in [k for k, v in _hits.items() if not v]:
            del _hits[key]


def require_admin(credentials: HTTPBasicCredentials = Depends(security)):
    # compare_digest takes the same time however many characters match,
    # so an attacker can't guess the password one letter at a time
    ok = secrets.compare_digest(
        credentials.password.encode(), ADMIN_PASSWORD.encode()
    )
    if not ok:
        raise HTTPException(
            status_code=401,
            detail="Wrong password",
            headers={"WWW-Authenticate": "Basic"},
        )


def init_db():
    with psycopg.connect(DATABASE_URL) as db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS reports (
                id            BIGSERIAL PRIMARY KEY,
                number_hash   TEXT NOT NULL,
                number_masked TEXT NOT NULL,
                carrier       TEXT NOT NULL,
                device_hash   TEXT NOT NULL,
                scam_type     TEXT NOT NULL,
                reported_day  DATE NOT NULL DEFAULT CURRENT_DATE,
                reported_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                UNIQUE (number_hash, device_hash, reported_day)
            )
        """)


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
    return number[:5] + "*" * (len(number) - 8) + number[-3:]


class ReportIn(BaseModel):
    number: str = Field(min_length=7, max_length=20)
    device_id: str = Field(min_length=16, max_length=64)
    scam_type: str = "otp_prize"


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/reports", status_code=201, dependencies=[Depends(rate_limit)])
def create_report(report: ReportIn, response: Response):
    number = normalize(report.number)
    if len(number) < 9:
        raise HTTPException(status_code=422, detail="Not a valid phone number")

    with psycopg.connect(DATABASE_URL) as db:
        cur = db.execute(
            """INSERT INTO reports
               (number_hash, number_masked, carrier, device_hash, scam_type)
               VALUES (%s, %s, %s, %s, %s)
               ON CONFLICT DO NOTHING""",
            (
                keyed_hash("number", number),
                mask(number),
                carrier_of(number),
                keyed_hash("device", report.device_id),
                report.scam_type,
            ),
        )
        inserted = cur.rowcount

    if inserted == 0:
        response.status_code = 200
        return {"status": "already_reported_today"}
    return {"status": "recorded"}


@app.get("/lookup", dependencies=[Depends(rate_limit)])
def lookup(number: str):
    n = normalize(number)
    if len(n) < 9:
        raise HTTPException(status_code=422, detail="Not a valid phone number")

    with psycopg.connect(DATABASE_URL) as db:
        count = db.execute(
            "SELECT COUNT(DISTINCT device_hash) FROM reports WHERE number_hash = %s",
            (keyed_hash("number", n),),
        ).fetchone()[0]

    if count == 0:
        status = "unknown"
    elif count < 3:
        status = "reported"
    else:
        status = "suspected"
    return {"status": status, "reporting_devices": count}


@app.get("/stats", dependencies=[Depends(require_admin)])
def stats():
    with psycopg.connect(DATABASE_URL) as db:
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
               FROM reports
               GROUP BY number_hash, number_masked, carrier
               ORDER BY devices DESC, MAX(reported_at) DESC LIMIT 10"""
        ).fetchall()

    return {
        "total_reports": total,
        "distinct_numbers": numbers,
        "per_day": [{"day": d.isoformat(), "reports": c} for d, c in reversed(per_day)],
        "by_carrier": [{"carrier": k, "reports": c} for k, c in by_carrier],
        "top_numbers": [{"number": n, "carrier": k, "devices": d} for n, k, d in top],
    }


@app.get("/dashboard", dependencies=[Depends(require_admin)])
def dashboard():
    return FileResponse(Path(__file__).parent / "dashboard.html")

@app.get("/whoami")
def whoami(request: Request):
    return {"ip_seen": client_ip(request)}