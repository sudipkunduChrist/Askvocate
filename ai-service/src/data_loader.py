import pandas as pd
from pathlib import Path

# Resolve relative to this file, not the caller's working directory —
# so this works whether it's imported from a notebook in notebooks/
# or a script run from src/ later.
DATA_DIR = Path(__file__).resolve().parent.parent / "datasets"

CASES_FILE = DATA_DIR / "cases.csv"
ADVOCATES_FILE = DATA_DIR / "advocate_details.csv"
QUERIES_FILE = DATA_DIR / "test_queries.csv"


def load_cases() -> pd.DataFrame:
    return pd.read_csv(CASES_FILE)


def load_lawyers() -> pd.DataFrame:
    return pd.read_csv(ADVOCATES_FILE)


def load_test_queries() -> pd.DataFrame:
    return pd.read_csv(QUERIES_FILE)


def load_all():
    return load_cases(), load_lawyers(), load_test_queries()