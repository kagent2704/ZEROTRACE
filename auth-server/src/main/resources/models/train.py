import os
import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import fbeta_score


BASE_DIR = os.path.dirname(__file__)
DATASET_PATH = os.path.join(BASE_DIR, "zerotrace_training_dataset.csv")
SPREAD_MODEL_PATH = os.path.join(BASE_DIR, "anomaly_model.pkl")
PAYLOAD_MODEL_PATH = os.path.join(BASE_DIR, "payload_model.pkl")
RANDOM_STATE = 42


def engineer_features(frame: pd.DataFrame) -> pd.DataFrame:
    result = frame.copy()
    msg_count = np.maximum(result["msg_count"].to_numpy(dtype=float), 1.0)
    avg_gap = np.maximum(result["avg_time_gap"].to_numpy(dtype=float), 0.5)
    connections = result["connections"].to_numpy(dtype=float)

    result["fanout_ratio"] = connections / msg_count
    result["spread_pressure"] = connections / avg_gap
    result["burst_fanout"] = (msg_count * connections) / avg_gap
    return result


def build_mass_target_anomalies(sample_size: int) -> pd.DataFrame:
    rng = np.random.default_rng(RANDOM_STATE)
    anomalies = pd.DataFrame({
        "msg_count": rng.integers(8, 120, size=sample_size),
        "avg_time_gap": rng.uniform(0.2, 22.0, size=sample_size),
        "msg_size": rng.integers(80, 900, size=sample_size),
        "connections": rng.integers(4, 22, size=sample_size),
        "failed_attempts": rng.integers(0, 3, size=sample_size),
        "ip_changes": rng.integers(0, 2, size=sample_size),
        "label": np.ones(sample_size, dtype=int)
    })
    return anomalies


def build_payload_anomalies(sample_size: int) -> pd.DataFrame:
    rng = np.random.default_rng(RANDOM_STATE + 7)
    anomalies = pd.DataFrame({
        "msg_count": rng.integers(1, 18, size=sample_size),
        "avg_time_gap": rng.uniform(8.0, 120.0, size=sample_size),
        "msg_size": rng.integers(1800, 9000, size=sample_size),
        "connections": rng.integers(1, 4, size=sample_size),
        "failed_attempts": rng.integers(0, 2, size=sample_size),
        "ip_changes": rng.integers(0, 2, size=sample_size),
        "label": np.ones(sample_size, dtype=int)
    })
    return anomalies


def choose_threshold(probabilities: np.ndarray, labels: np.ndarray) -> float:
    thresholds = np.linspace(0.30, 0.90, 61)
    best_threshold = 0.50
    best_score = -1.0
    for threshold in thresholds:
        predictions = (probabilities >= threshold).astype(int)
        score = fbeta_score(labels, predictions, beta=2.0)
        if score > best_score:
            best_score = score
            best_threshold = float(threshold)
    return best_threshold


def train_detector(normal: pd.DataFrame, anomalies: pd.DataFrame, output_path: str) -> float:
    combined = pd.concat([normal, anomalies], ignore_index=True)
    combined = engineer_features(combined)

    feature_columns = [
        "msg_count",
        "avg_time_gap",
        "msg_size",
        "connections",
        "failed_attempts",
        "ip_changes",
        "fanout_ratio",
        "spread_pressure",
        "burst_fanout"
    ]

    x = combined[feature_columns]
    y = combined["label"]

    x_train, x_eval, y_train, y_eval = train_test_split(
        x,
        y,
        test_size=0.25,
        random_state=RANDOM_STATE,
        stratify=y
    )

    model = RandomForestClassifier(
        n_estimators=300,
        max_depth=12,
        min_samples_leaf=2,
        class_weight="balanced",
        random_state=RANDOM_STATE
    )
    model.fit(x_train, y_train)

    eval_probabilities = model.predict_proba(x_eval)[:, 1]
    threshold = choose_threshold(eval_probabilities, y_eval.to_numpy())

    artifact = {
        "model": model,
        "feature_columns": feature_columns,
        "threshold": threshold
    }
    joblib.dump(artifact, output_path)
    return threshold


def main() -> None:
    normal = pd.read_csv(DATASET_PATH)
    normal.columns = normal.columns.str.strip()
    normal["label"] = 0

    spread_threshold = train_detector(
        normal.copy(),
        build_mass_target_anomalies(sample_size=max(160, len(normal) // 2)),
        SPREAD_MODEL_PATH
    )
    payload_threshold = train_detector(
        normal.copy(),
        build_payload_anomalies(sample_size=max(140, len(normal) // 3)),
        PAYLOAD_MODEL_PATH
    )

    print(
        "Models trained successfully. "
        f"Spread threshold={spread_threshold:.3f}, "
        f"Payload threshold={payload_threshold:.3f}"
    )


if __name__ == "__main__":
    main()
