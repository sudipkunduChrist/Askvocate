from __future__ import annotations

from functools import lru_cache
import logging

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from src.llm_router import provider_configuration
from src.legal_assistant import chat as legal_assistant_chat
from src.legal_assistant import provider_configuration as assistant_provider_configuration
from src.recommender import CASES_FILE, LAWYERS_FILE, RecommendationEngine


logger = logging.getLogger(__name__)

app = FastAPI(title="Askvocate AI Matching API", version="1.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


class RecommendationRequest(BaseModel):
    query: str = Field(min_length=8, max_length=2000)
    top_k: int = Field(default=10, ge=1, le=50)


class AssistantMessage(BaseModel):
    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(min_length=1, max_length=4000)


class LegalAssistantRequest(BaseModel):
    messages: list[AssistantMessage] = Field(min_length=1, max_length=20)


@lru_cache(maxsize=1)
def get_engine() -> RecommendationEngine:
    return RecommendationEngine()


@app.get("/health")
def health() -> dict:
    missing = [str(path) for path in (CASES_FILE, LAWYERS_FILE) if not path.exists()]
    return {
        "status": "degraded" if missing else "ready",
        "missing_files": missing,
        "llm_providers": provider_configuration(),
        "legal_assistant": assistant_provider_configuration(),
    }


@app.post("/legal-assistant/chat")
def legal_assistant(request: LegalAssistantRequest) -> dict:
    try:
        return legal_assistant_chat([message.model_dump() for message in request.messages])
    except TimeoutError as error:
        raise HTTPException(status_code=504, detail=str(error)) from error
    except RuntimeError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except ValueError:
        logger.warning("Legal assistant returned malformed structured output")
        return {
            "status": "gathering",
            "reply": "I couldn't format that response correctly. Your conversation is still available, so please send your last answer once more.",
            "follow_up_question": "Could you briefly restate your last answer?",
            "case_summary": "",
            "lawyer_matching_requested": False,
            "recommendation_query": "",
            "suggested_actions": [],
            "disclaimer": "This is general legal information, not legal advice.",
            "provider": "fallback",
        }
    except Exception as error:
        logger.exception("Legal assistant failed")
        raise HTTPException(status_code=500, detail="Legal assistant failed. Please try again.") from error


@app.post("/recommend")
def recommend(request: RecommendationRequest) -> dict:
    try:
        return get_engine().recommend(request.query, request.top_k)
    except FileNotFoundError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(status_code=500, detail=f"Recommendation failed: {error}") from error


@app.get("/lawyers/{record_id}")
def lawyer_profile(record_id: str) -> dict:
    try:
        return get_engine().get_lawyer_profile(record_id)
    except KeyError as error:
        raise HTTPException(status_code=404, detail="Advocate not found") from error
    except FileNotFoundError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
