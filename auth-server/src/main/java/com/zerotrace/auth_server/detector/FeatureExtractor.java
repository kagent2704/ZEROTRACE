package com.zerotrace.auth_server.detector;

public class FeatureExtractor {

    public static double[] extractFeatures(
            int msgCount,
            double avgTimeGap,
            int msgSize,
            int connections,
            int failedAttempts,
            int ipChanges
    ) {
        return new double[]{
                msgCount,
                avgTimeGap,
                msgSize,
                connections,
                failedAttempts,
                ipChanges
        };
    }
}
