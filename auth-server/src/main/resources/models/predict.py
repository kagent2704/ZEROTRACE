import sys
import joblib
import pandas as pd
import os

def engineered_features(msg_count, avg_time_gap, msg_size, connections, failed_attempts, ip_changes):
    msg_count_safe = max(msg_count, 1.0)
    avg_gap_safe = max(avg_time_gap, 0.5)
    fanout_ratio = connections / msg_count_safe
    spread_pressure = connections / avg_gap_safe
    burst_fanout = (msg_count_safe * connections) / avg_gap_safe
    return [[
        msg_count,
        avg_time_gap,
        msg_size,
        connections,
        failed_attempts,
        ip_changes,
        fanout_ratio,
        spread_pressure,
        burst_fanout
    ]]


try:
    base_dir = os.path.dirname(__file__)
    model_path = os.path.join(base_dir, "anomaly_model.pkl")
    payload_model_path = os.path.join(base_dir, "payload_model.pkl")
    spread_artifact = joblib.load(model_path)
    payload_artifact = joblib.load(payload_model_path)
    spread_feature_columns = spread_artifact["feature_columns"]
    payload_feature_columns = payload_artifact["feature_columns"]

    msg_count = float(sys.argv[1])
    avg_time_gap = float(sys.argv[2])
    msg_size = float(sys.argv[3])
    connections = float(sys.argv[4])
    failed_attempts = float(sys.argv[5])
    ip_changes = float(sys.argv[6])

    raw_features = engineered_features(
        msg_count,
        avg_time_gap,
        msg_size,
        connections,
        failed_attempts,
        ip_changes
    )[0]
    canonical_columns = [
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
    feature_map = dict(zip(canonical_columns, raw_features))
    spread_data = pd.DataFrame([[feature_map[column] for column in spread_feature_columns]], columns=spread_feature_columns)
    payload_data = pd.DataFrame([[feature_map[column] for column in payload_feature_columns]], columns=payload_feature_columns)

    spread_score = float(spread_artifact["model"].predict_proba(spread_data)[0][1])
    payload_score = float(payload_artifact["model"].predict_proba(payload_data)[0][1])
    spread_threshold = float(spread_artifact.get("threshold", 0.5))
    payload_threshold = float(payload_artifact.get("threshold", 0.5))

    spread_flag = spread_score >= spread_threshold
    payload_flag = payload_score >= payload_threshold

    if spread_flag and payload_flag:
        combined_score = max(spread_score, payload_score)
        print(
            "ANOMALY|"
            f"{combined_score:.4f}|"
            f"AI abnormal payload size pattern detected (payload score {payload_score:.4f}); "
            f"AI mass-target spread pattern detected (spread score {spread_score:.4f})"
        )
    elif payload_flag:
        print(f"ANOMALY|{payload_score:.4f}|AI abnormal payload size pattern detected")
    elif spread_flag:
        print(f"ANOMALY|{spread_score:.4f}|AI mass-target spread pattern detected")
    else:
        score = max(spread_score, payload_score)
        print(f"NORMAL|{score:.4f}|Network behaviour normal")

except Exception as e:
    print(f"ERROR|0.0000|{str(e)}")
