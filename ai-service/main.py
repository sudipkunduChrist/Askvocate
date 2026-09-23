from __future__ import annotations

from functools import lru_cache

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from src.llm_router import provider_configuration
from src.recommender import CASES_FILE, LAWYERS_FILE, RecommendationEngine


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
    }


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
