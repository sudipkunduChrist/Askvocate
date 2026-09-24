from __future__ import annotations

import math
import hashlib
import json
import pickle
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from sentence_transformers import SentenceTransformer

from .llm_router import classify_with_llm
from .preprocessing import preprocess_query


DATA_DIR = Path(__file__).resolve().parent.parent / "datasets"
CASES_FILE = DATA_DIR / "cases.csv"
LAWYERS_FILE = DATA_DIR / "advocate_details.csv"
DOMAIN_CACHE = DATA_DIR / "domain_embeddings_v3.pkl"
LAWYER_CACHE = DATA_DIR / "lawyer_embeddings.pkl"
MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
CACHE_VERSION = 2
LLM_CONFIDENCE_THRESHOLD = 0.65

LANGUAGE_THRESHOLDS = {
    "english": {"confidence": 0.75, "margin": 0.10},
    "hinglish": {"confidence": 0.70, "margin": 0.08},
    "hindi": {"confidence": 0.62, "margin": 0.06},
}

DOMAIN_TAXONOMY = {
    "Administrative Law": (["Administrative Law"], ["Constitutional Law", "Civil Litigation"]),
    "Arbitration": (["Arbitration and Mediation"], ["Commercial Law", "Corporate Law"]),
    "Banking & Finance": (["Banking and Finance"], ["Corporate Law", "Securities Law"]),
    "Child Protection": (["Child Protection Law"], ["Family Law", "Constitutional Law"]),
    "Constitutional Law": (["Constitutional Law"], ["Human Rights Law", "Public Interest Litigation"]),
    "Consumer Protection": (["Consumer Law"], ["Civil Litigation", "Commercial Law"]),
    "Contract & Agreement": (["Contract Law"], ["Commercial Law", "Civil Litigation"]),
    "Corporate & Commercial": (["Corporate Law"], ["Banking and Finance", "Securities Law"]),
    "Criminal Law": (["Criminal Law"], ["Constitutional Law"]),
    "Cybercrime & IT": (["Cyber Law"], ["Data Protection and Privacy", "Information Technology Law"]),
    "Data Privacy": (["Data Protection and Privacy"], ["Information Technology Law", "Cyber Law"]),
    "Divorce & Matrimonial": (["Matrimonial Law"], ["Family Law"]),
    "Education Law": (["Education Law"], ["Constitutional Law", "Administrative Law"]),
    "Employment & Labour": (["Labour and Employment Law"], ["Civil Litigation"]),
    "Environmental Law": (["Environmental Law"], ["Public Interest Litigation", "Constitutional Law"]),
    "Family & Succession": (["Family Law"], ["Matrimonial Law"]),
    "Human Rights & PIL": (["Human Rights Law"], ["Public Interest Litigation", "Constitutional Law"]),
    "Immigration & Citizenship": (["Immigration Law"], ["Constitutional Law", "Civil Litigation"]),
    "Insolvency & Bankruptcy": (["Insolvency and Bankruptcy"], ["Banking and Finance", "Corporate Law"]),
    "Intellectual Property": (["Intellectual Property"], ["Patent Law", "Trademark Law", "Copyright Law"]),
    "Media & Defamation": (["Media and Entertainment Law"], ["Civil Litigation", "Criminal Law"]),
    "Medical Negligence": (["Medical Negligence Law"], ["Consumer Law", "Civil Litigation"]),
    "Motor Accident Claims": (["Motor Accident Claims Law"], ["Personal Injury Law", "Civil Litigation"]),
    "Property & Land": (["Property Law"], ["Real Estate Law", "Civil Litigation"]),
    "Real Estate & Housing": (["Real Estate Law"], ["Property Law", "Civil Litigation"]),
    "Tax & GST": (["Tax Law"], ["GST Law", "Corporate Law"]),
    "White Collar Crime": (["White Collar Crime"], ["Criminal Law", "Banking and Finance"]),
}

DOMAIN_HINTS = {
    "Real Estate & Housing": "landlord tenant rent security deposit eviction builder flat rera मकान मालिक किरायेदार",
    "Property & Land": "property land ownership registry boundary encroachment zameen kabza जमीन कब्जा",
    "Employment & Labour": "job salary fired termination gratuity provident fund labour नौकरी वेतन",
    "Cybercrime & IT": "online fraud otp upi hacked phishing cyber crime ऑनलाइन फ्रॉड साइबर",
    "Divorce & Matrimonial": "divorce alimony dowry marriage spouse talaq तलाक दहेज",
    "Criminal Law": "fir bail arrest assault violence police theft murder vehicle impounded confiscated police seizure जमानत गिरफ्तारी पुलिस",
    "Family & Succession": "inheritance will probate partition ancestral family विरासत उत्तराधिकार",
    "Motor Accident Claims": "road car bike accident hit run compensation mact दुर्घटना मुआवजा",
    "Medical Negligence": "doctor hospital wrong treatment surgery negligence डॉक्टर अस्पताल",
    "Consumer Protection": "defective product warranty refund consumer service complaint unfair recovery practice unauthorized repossession उपभोक्ता रिफंड",
    "Banking & Finance": "bank loan vehicle loan emi cheque bounce debt sarfaesi account hypothecation repossession recovery agent financier बैंक लोन चेक",
    "Tax & GST": "income tax gst assessment audit refund आयकर जीएसटी टैक्स",
}

SPECIAL_ROUTES = {
    "domestic violence": "Criminal Law", "घरेलू हिंसा": "Criminal Law",
    "husband beats me": "Criminal Law", "pati ne maara": "Criminal Law",
    "मारपीट": "Criminal Law", "498a": "Criminal Law", "dowry": "Criminal Law",
    "दहेज": "Criminal Law", "stridhan": "Family & Succession", "स्त्रीधन": "Family & Succession",
}


def _is_ambiguous_vehicle_seizure(query: str) -> bool:
    lowered = query.lower()
    vehicle_terms = ("bike", "motorcycle", "scooter", "car", "vehicle", "गाड़ी", "बाइक")
    seizure_terms = ("seiz", "reposses", "impound", "confiscat", "taken", "ले गए", "जब्त")
    actor_terms = (
        "police", "traffic police", "government", "authority", "bank", "lender", "finance",
        "financier", "recovery agent", "repo agent", "private person", "friend", "relative",
        "पुलिस", "बैंक", "फाइनेंस", "रिकवरी एजेंट",
    )
    return (
        any(term in lowered for term in vehicle_terms)
        and any(term in lowered for term in seizure_terms)
        and not any(term in lowered for term in actor_terms)
    )


def _clarification_question(query: str) -> str:
    if _is_ambiguous_vehicle_seizure(query):
        return "Who seized the vehicle: the police, a bank or recovery agent, or another private person—and was it financed?"
    return "Please add who acted, why it happened, and what outcome you want so we can identify the right legal domain."


def _clean(value: Any, default: Any = None) -> Any:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return default
    try:
        if bool(pd.isna(value)):
            return default
    except (TypeError, ValueError):
        pass
    return value.item() if isinstance(value, np.generic) else value


def _file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _load_embedding_model() -> SentenceTransformer:
    """Use the local Hugging Face cache first, downloading only on the first-ever run."""
    try:
        from huggingface_hub import snapshot_download

        local_snapshot = snapshot_download(MODEL_NAME, local_files_only=True)
        return SentenceTransformer(local_snapshot, local_files_only=True)
    except (OSError, ValueError):
        return SentenceTransformer(MODEL_NAME)


class RecommendationEngine:
    def __init__(self) -> None:
        missing = [str(path) for path in (CASES_FILE, LAWYERS_FILE) if not path.exists()]
        if missing:
            raise FileNotFoundError("Missing AI dataset files: " + ", ".join(missing))

        self.cases = pd.read_csv(CASES_FILE)
        self.lawyers = pd.read_csv(LAWYERS_FILE).reset_index(drop=True)
        self.lawyer_lookup = {
            str(row["record_id"]): index
            for index, row in self.lawyers.iterrows()
            if _clean(row.get("record_id")) is not None
        }
        self.embedder = _load_embedding_model()
        self.domains = sorted(DOMAIN_TAXONOMY)
        taxonomy_json = json.dumps({"taxonomy": DOMAIN_TAXONOMY, "hints": DOMAIN_HINTS}, sort_keys=True, ensure_ascii=False)
        self.domain_data_hash = hashlib.sha256((taxonomy_json + _file_hash(CASES_FILE)).encode()).hexdigest()
        self.lawyer_data_hash = _file_hash(LAWYERS_FILE)
        self.domain_embeddings = self._load_or_build_domain_embeddings()
        self.lawyer_embeddings = self._load_or_build_lawyer_embeddings()

    def get_lawyer_profile(self, record_id: str) -> dict[str, Any]:
        index = self.lawyer_lookup.get(record_id)
        if index is None:
            raise KeyError(record_id)
        row = self.lawyers.iloc[index]
        return {column: _clean(row.get(column)) for column in self.lawyers.columns}

    def _domain_descriptions(self) -> list[str]:
        descriptions = []
        for domain in self.domains:
            case_keywords = ""
            if {"legal_domain", "search_keywords"}.issubset(self.cases.columns):
                rows = self.cases.loc[self.cases["legal_domain"] == domain, "search_keywords"].dropna().head(30)
                case_keywords = " ".join(rows.astype(str))
            primary, secondary = DOMAIN_TAXONOMY[domain]
            descriptions.append(" ".join([domain, *primary, *secondary, DOMAIN_HINTS.get(domain, ""), case_keywords]))
        return descriptions

    def _load_or_build_domain_embeddings(self) -> np.ndarray:
        if DOMAIN_CACHE.exists():
            try:
                cached = pickle.loads(DOMAIN_CACHE.read_bytes())
                embeddings = cached.get("embeddings")
                if (cached.get("model") == MODEL_NAME
                        and cached.get("service_cache_version") == CACHE_VERSION
                        and cached.get("data_hash") == self.domain_data_hash
                        and len(embeddings) == len(self.domains)):
                    return np.asarray(embeddings)
            except (OSError, pickle.PickleError, AttributeError, TypeError):
                pass
        embeddings = self.embedder.encode(self._domain_descriptions(), convert_to_numpy=True, normalize_embeddings=True)
        DOMAIN_CACHE.write_bytes(pickle.dumps({
            "model": MODEL_NAME,
            "service_cache_version": CACHE_VERSION,
            "data_hash": self.domain_data_hash,
            "embeddings": embeddings,
        }))
        return embeddings

    def _lawyer_texts(self) -> list[str]:
        columns = ["practice_area_primary", "practice_area_secondary", "city", "experience_level"]
        return [" | ".join(str(row.get(column, "")) for column in columns) for _, row in self.lawyers.iterrows()]

    def _load_or_build_lawyer_embeddings(self) -> np.ndarray:
        if LAWYER_CACHE.exists():
            try:
                cached = pickle.loads(LAWYER_CACHE.read_bytes())
                if (cached.get("model") == MODEL_NAME
                        and cached.get("service_cache_version") == CACHE_VERSION
                        and cached.get("data_hash") == self.lawyer_data_hash
                        and cached.get("n") == len(self.lawyers)):
                    return np.asarray(cached["embeddings"])
            except (OSError, pickle.PickleError, KeyError, TypeError):
                pass
        embeddings = self.embedder.encode(self._lawyer_texts(), convert_to_numpy=True, normalize_embeddings=True)
        LAWYER_CACHE.write_bytes(pickle.dumps({
            "model": MODEL_NAME,
            "service_cache_version": CACHE_VERSION,
            "data_hash": self.lawyer_data_hash,
            "n": len(self.lawyers),
            "embeddings": embeddings,
        }))
        return embeddings

    def _classify(self, query: str, query_vector: np.ndarray) -> dict[str, Any]:
        lowered = query.lower()
        for phrase, domain in SPECIAL_ROUTES.items():
            if phrase in lowered:
                return {
                    "primary_domain": domain,
                    "confidence": 0.99,
                    "raw_cosine": 1.0,
                    "margin": 1.0,
                    "top3_domains": [{"domain": domain, "score": 1.0}],
                    "ambiguous": False,
                    "source": "special_route",
                    "matched_trigger": phrase,
                }
        scores = self.domain_embeddings @ query_vector
        for index, domain in enumerate(self.domains):
            hints = DOMAIN_HINTS.get(domain, "").lower().split()
            scores[index] += min(0.18, sum(token in lowered for token in hints) * 0.025)

        top_indices = np.argsort(scores)[::-1][:3]
        top_scores = scores[top_indices]
        margin = float(top_scores[0] - top_scores[1])
        temperature = 0.05
        probabilities = np.exp((scores - scores.max()) / temperature)
        probabilities /= probabilities.sum()
        best = int(top_indices[0])
        return {
            "primary_domain": self.domains[best],
            "confidence": round(float(probabilities[best]), 4),
            "raw_cosine": round(float(scores[best]), 4),
            "margin": round(margin, 4),
            "top3_domains": [
                {"domain": self.domains[int(index)], "score": round(float(scores[index]), 4)}
                for index in top_indices
            ],
            "ambiguous": margin < 0.05,
            "source": "local_vector_embedding",
        }

    def recommend(self, query: str, top_k: int = 10) -> dict[str, Any]:
        processed = preprocess_query(query)
        normalized_query = processed["normalized"]
        query_vector = self.embedder.encode(
            [normalized_query], convert_to_numpy=True, normalize_embeddings=True
        )[0]
        local_intent = self._classify(normalized_query, query_vector)
        language_key = processed["detected_language"].lower()
        threshold = LANGUAGE_THRESHOLDS.get(language_key, LANGUAGE_THRESHOLDS["english"])
        context_sensitive = _is_ambiguous_vehicle_seizure(normalized_query)
        local_confident = local_intent["source"] == "special_route" or (
            local_intent["confidence"] >= threshold["confidence"]
            and local_intent["margin"] >= threshold["margin"]
            and not local_intent["ambiguous"]
            and not context_sensitive
        )

        llm_intent = None if local_confident else classify_with_llm(normalized_query, self.domains)
        llm_status = llm_intent.get("status", "matched") if llm_intent else None
        if (llm_intent and llm_status == "matched"
                and float(llm_intent.get("confidence", 0.0)) < LLM_CONFIDENCE_THRESHOLD):
            llm_status = "needs_context"
        if llm_intent and llm_status != "matched":
            return {
                "query": query,
                "detected_domain": "Needs clarification",
                "confidence": round(float(llm_intent.get("confidence", 0.0)), 4),
                "margin": local_intent["margin"],
                "ambiguous": True,
                "top3_domains": local_intent["top3_domains"],
                "language": processed["detected_language"],
                "intent_source": llm_intent["source"],
                "route_used": llm_status,
                "classification_reason": llm_intent.get("reason", ""),
                "needs_clarification": True,
                "clarification_question": llm_intent.get("clarification_question") or _clarification_question(query),
                "threshold_used": threshold,
                "recommended_lawyers": [],
                "total_lawyers": 0,
            }
        if not llm_intent and not local_confident:
            return {
                "query": query,
                "detected_domain": "Needs clarification",
                "confidence": local_intent["confidence"],
                "margin": local_intent["margin"],
                "ambiguous": True,
                "top3_domains": local_intent["top3_domains"],
                "language": processed["detected_language"],
                "intent_source": local_intent["source"],
                "route_used": "local_unsure",
                "classification_reason": "External classifiers were unavailable or could not confidently classify the query.",
                "needs_clarification": True,
                "clarification_question": _clarification_question(query),
                "threshold_used": threshold,
                "recommended_lawyers": [],
                "total_lawyers": 0,
            }
        if llm_intent:
            domain = llm_intent["primary_domain"]
            confidence = llm_intent["confidence"]
            source = llm_intent["source"]
            route_used = "gemini_fallback" if source.startswith("gemini:") else "groq_fallback"
            reason = llm_intent.get("reason", "")
        else:
            domain = local_intent["primary_domain"]
            confidence = local_intent["confidence"]
            source = local_intent["source"]
            route_used = "special_route" if source == "special_route" else (
                "local_confident" if local_confident else "local_unsure"
            )
            reason = ""

        primary, secondary = DOMAIN_TAXONOMY[domain]
        primary_set, secondary_set = set(primary), set(secondary)
        semantic_scores = self.lawyer_embeddings @ query_vector

        matches: list[tuple[float, int, str]] = []
        for index, lawyer in self.lawyers.iterrows():
            area = str(lawyer.get("practice_area_primary", "")).strip()
            other_areas = {part.strip() for part in str(lawyer.get("practice_area_secondary", "")).split(";")}
            if area in primary_set:
                tier, base = "primary", 1.0
            elif area in secondary_set:
                tier, base = "secondary_strong", 0.72
            elif other_areas & primary_set:
                tier, base = "secondary_weak", 0.45
            else:
                continue
            rating = float(_clean(lawyer.get("profile_rating"), 4.0) or 4.0)
            score = base + rating / 25.0 + float(semantic_scores[index]) * 0.35
            matches.append((score, index, tier))

        matches.sort(reverse=True)
        output = []
        for score, index, tier in matches[:top_k]:
            lawyer = self.lawyers.iloc[index]
            output.append({
                "id": str(_clean(lawyer.get("record_id"), f"synthetic-{index}")),
                "advocate_name": _clean(lawyer.get("advocate_name"), "Advocate"),
                "city": _clean(lawyer.get("city"), "Location not listed"),
                "practice_area_primary": _clean(lawyer.get("practice_area_primary"), "Legal practice"),
                "practice_area_secondary": _clean(lawyer.get("practice_area_secondary"), ""),
                "experience_level": _clean(lawyer.get("experience_level"), ""),
                "profile_rating": float(_clean(lawyer.get("profile_rating"), 0.0) or 0.0),
                "consultation_fee_inr": int(float(_clean(lawyer.get("consultation_fee_inr"), 0) or 0)),
                "match_type": tier,
                "score": round(score, 3),
            })

        return {
            "query": query,
            "detected_domain": domain,
            "confidence": round(confidence, 4),
            "margin": local_intent["margin"] if not llm_intent else None,
            "ambiguous": False if llm_intent else local_intent["ambiguous"],
            "top3_domains": local_intent["top3_domains"],
            "language": processed["detected_language"],
            "intent_source": source,
            "route_used": route_used,
            "classification_reason": reason,
            "needs_clarification": route_used == "local_unsure",
            "clarification_question": _clarification_question(query) if route_used == "local_unsure" else "",
            "threshold_used": threshold,
            "recommended_lawyers": output,
            "total_lawyers": len(output),
        }
