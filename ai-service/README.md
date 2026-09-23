# Askvocate AI service

The Android app calls this service directly at `POST /recommend`. The multilingual local model is the primary classifier. Low-confidence queries fall back to Gemini, then Groq; lawyer filtering and reranking always stay local.

## Required local assets

Place these files under `ai-service/datasets/`:

- `cases.csv`
- `advocate_details.csv`
- `domain_embeddings_v3.pkl` (optional; generated when absent)
- `lawyer_embeddings.pkl` (optional; generated when absent)

The two PKL caches are regenerated when missing, incompatible, or when their source CSV content changes.

## LLM fallback configuration

Set `GEMINI_API_KEY` and/or `GROQ_API_KEY` in `ai-service/.env`. Gemini is tried first and Groq is used if Gemini is unavailable or rejects the request. If neither provider is configured, the service remains functional in local-only mode and marks uncertain results with `needs_clarification: true`.

Optional model overrides are `GEMINI_MODEL` and `GROQ_MODEL`. Defaults are `gemini-3.8-flash` and `openai/gpt-oss-120b`.

## Run locally

```powershell
cd ai-service
python -m pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

For a physical Android device connected over USB:

```powershell
adb reverse tcp:8000 tcp:8000
```

The Spring API still uses port 8080, so reverse that separately as before.

## Endpoints

- `POST /recommend` classifies a case and returns ranked advocates.
- `GET /lawyers/{record_id}` returns the complete synthetic dataset record used by the advocate detail screen.
- `GET /health` reports dataset and LLM-provider readiness.
