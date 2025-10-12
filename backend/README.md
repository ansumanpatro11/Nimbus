# Backend (FastAPI + SQLAlchemy)
## Quick start
```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.api.main:app --reload --host 0.0.0.0 --port 8000
```
Endpoints:
- GET  /health
- POST /ingest?api_key=dev-secret-key
- GET  /latest?team=default
- GET  /history?uid=user_01
- WS   /live/default
