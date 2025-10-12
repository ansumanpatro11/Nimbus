# SIH Wearable Prototype Stack

Contains:
- backend/  (FastAPI + SQLAlchemy, async, WebSocket)
- dashboard/ (Streamlit)
- mobile/   (Android Kotlin prototype)

## Run Backend
```
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.api.main:app --reload --host 0.0.0.0 --port 8000
```

## Run Dashboard
```
cd dashboard
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export API_URL=http://localhost:8000
streamlit run app.py
```

## Android
- Open `mobile/` in Android Studio
- Devices must advertise names `ND-WRIST` and `ND-CHEST`
- Replace UUIDs in `BleForegroundService.kt` to match your firmware
- Ingest API key: `dev-secret-key` (change in backend .env)
