import sys
import joblib
import numpy as np
import os


def trigger_label(msg_count, avg_time_gap, msg_size, connections, failed_attempts, ip_changes):
    if msg_count > 50 and avg_time_gap < 1:
        return "Possible Spam Bot Activity"
    if failed_attempts > 5:
        return "Brute Force Authentication Attempt"
    if ip_changes > 3:
        return "Suspicious IP Switching Detected"
    if msg_size > 2000:
        return "Abnormally Large Message Payload"
    if connections > 5:
        return "Unusual Peer Connection Pattern"
    return "General Network Anomaly"


try:
    base_dir = os.path.dirname(__file__)
    model_path = os.path.join(base_dir, "anomaly_model.pkl")
    model = joblib.load(model_path)

    msg_count = float(sys.argv[1])
    avg_time_gap = float(sys.argv[2])
    msg_size = float(sys.argv[3])
    connections = float(sys.argv[4])
    failed_attempts = float(sys.argv[5])
    ip_changes = float(sys.argv[6])

    data = np.array([[
        msg_count,
        avg_time_gap,
        msg_size,
        connections,
        failed_attempts,
        ip_changes
    ]])

    prediction = model.predict(data)

    if hasattr(model, "score_samples"):
        score = float(-model.score_samples(data)[0])
    elif hasattr(model, "decision_function"):
        score = float(-model.decision_function(data)[0])
    else:
        score = 1.0 if prediction[0] == -1 else 0.0

    if prediction[0] == -1:
        print(f"ANOMALY|{score:.4f}|{trigger_label(msg_count, avg_time_gap, msg_size, connections, failed_attempts, ip_changes)}")
    else:
        print(f"NORMAL|{score:.4f}|Network behaviour normal")

except Exception as e:
    print(f"ERROR|0.0000|{str(e)}")
