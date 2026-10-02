# Askvocate AI service

This service powers two separate client experiences:

- `POST /legal-assistant/chat` provides a multi-turn, legal-only preliminary guidance conversation.
- `POST /recommend` classifies a completed case description and returns ranked advocates.

The legal assistant uses NVIDIA Nemotron through NVIDIA's OpenAI-compatible hosted endpoint. Add these values to `ai-service/.env` (copy `ai-service/.env.example`):

```env
NVIDIA_API_KEY=your_nvidia_api_key_here
NVIDIA_MODEL=nvidia/nemotron-3-super-120b-a12b
```

The API key stays in the Python service and must never be added to the Android app. Hosted free access and rate limits are controlled by NVIDIA and may change; the application does not present the service as unlimited or guaranteed legal advice.

The assistant mirrors the language and script of the client's latest message. English, Hindi (Devanagari), and Roman Hindi/Hinglish are explicitly prioritized, with best-effort same-language responses for other languages. All request and response paths preserve Unicode text.

Legal assistant provider requests allow 60 seconds. Connection/read timeouts return HTTP 504
with a retry message. Android allows 90 seconds for the service response and keeps a
Retry response button visible after failure, so the last turn can be retried without
duplicating the user's message or losing the conversation.

The Android app calls this service directly at `POST /recommend`. The multilingual local model is the primary classifier. Only high-confidence, well-separated local results are accepted directly. Uncertain queries fall back to NVIDIA Nemotron, then Gemini Flash-Lite, then Groq; lawyer filtering and reranking always stay local.

The LLM may return `needs_context` when a missing fact could change the legal domain, or `out_of_taxonomy` when no supported domain fits. In either case the API returns `needs_clarification: true`, a `clarification_question`, and no lawyer recommendations so the app does not save a misleading match.

## Required local assets

Place these files under `ai-service/datasets/`:

- `cases.csv`
- `advocate_details.csv`
- `domain_embeddings_v3.pkl` (optional; generated when absent)
- `lawyer_embeddings.pkl` (optional; generated when absent)

The two PKL caches are regenerated when missing, incompatible, or when their source CSV content changes.

## LLM fallback configuration

Set `NVIDIA_API_KEY`, `GEMINI_API_KEY`, and/or `GROQ_API_KEY` in `ai-service/.env`. Nemotron is tried first for uncertain classifications, followed by Gemini Flash-Lite and Groq. If no external provider is available, the service remains functional in local-only mode and marks uncertain results with `needs_clarification: true`.

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

- `POST /legal-assistant/chat` conducts a scoped, multi-turn legal guidance conversation and creates a recommendation-ready case summary.
  When the user asks to find or recommend advocates for the current case, it returns
  `lawyer_matching_requested: true` and a `recommendation_query` based on the conversation,
  even while gathering details. Android shows "Find matching lawyers" and uses the same
  matching service and lawyer cards as the matching page to show results directly in chat.
  Loading, retry, and clarification controls remain in the chat. Selecting a lawyer opens
  their profile, and successful matches are saved in Saved Advocates as on the matching page.
  If the provider omits action fields or returns plain text promising a matching button,
  the service and Android recover the action using the available case context. The chat's
  persistent notice covers its informational scope; replies do not append a repeated disclaimer.
- `POST /recommend` classifies a case and returns ranked advocates.
- `GET /lawyers/{record_id}` returns the complete synthetic dataset record used by the advocate detail screen.
- `GET /health` reports dataset and LLM-provider readiness.
