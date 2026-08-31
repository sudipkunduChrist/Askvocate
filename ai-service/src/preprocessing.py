import re
import unicodedata

DEVANAGARI_RANGE = re.compile(r"[\u0900-\u097F]")

def detect_language(text: str) -> str:
    """
    Lightweight heuristic language tagger — not a proper classifier.
    Devanagari presence -> Hindi. Otherwise checks for common Hindi/Hinglish
    romanized words to distinguish Hinglish from pure English.
    """
    if DEVANAGARI_RANGE.search(text):
        return "Hindi"

    hinglish_markers = {
        "hai", "nahi", "nahin", "kya", "mera", "meri", "mujhe", "karna",
        "kiya", "gaya", "raha", "rahi", "hoga", "kaise", "kyunki", "bahut",
        "wala", "wali", "chahiye", "hua", "hui", "liye", "diya", "kar"
    }
    tokens = set(re.findall(r"[a-zA-Z]+", text.lower()))
    overlap = tokens & hinglish_markers

    return "Hinglish" if overlap else "English"


def normalize_text(text: str) -> str:
    """
    Minimal normalization — deliberately light. Transformer embedding models
    handle raw noisy text natively; heavy cleaning (stemming, stopword
    removal) actively hurts semantic embedding quality here.
    """
    text = unicodedata.normalize("NFC", text)   # standardize Devanagari matra encoding
    text = re.sub(r"\s+", " ", text).strip()     # collapse whitespace
    text = re.sub(r"[^\S\n]+([,.!?])", r"\1", text)  # remove space before punctuation
    return text


def preprocess_query(text: str) -> dict:
    """Single entry point: returns normalized text + detected language."""
    normalized = normalize_text(text)
    return {
        "original": text,
        "normalized": normalized,
        "detected_language": detect_language(normalized),
    }


if __name__ == "__main__":
    samples = [
        "m aj nhi aungi office late ho jaunga",
        "मेरे बैंक अकाउंट से पैसे किसी ने ऑनलाइन फ्रॉड करके निकाल लिए",
        "my landlord is refusing to return my security deposit",
    ]
    for s in samples:
        print(preprocess_query(s))