package com.zerotrace.auth_server.service;

import com.zerotrace.auth_server.detector.AIAnalyzer;
import com.zerotrace.auth_server.detector.FeatureExtractor;
import com.zerotrace.auth_server.model.ThreatAnalysisResult;
import com.zerotrace.auth_server.model.User;
import com.zerotrace.auth_server.repository.EncryptedMessageRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class ThreatDetectionService {

    private final EncryptedMessageRepository messageRepository;

    public ThreatDetectionService(EncryptedMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public ThreatAnalysisResult analyzeOutboundTraffic(User sender, int messageSize, int ipChanges) {
        Instant oneMinuteAgo = Instant.now().minusSeconds(60);
        long recentMessageCount = messageRepository.countBySenderAndCreatedAtAfter(sender.getUsername(), oneMinuteAgo);
        long recentUniqueReceivers = messageRepository.countDistinctReceiversBySenderAndCreatedAtAfter(
                sender.getUsername(),
                oneMinuteAgo
        );

        double avgGap = recentMessageCount <= 1
                ? 60.0
                : Duration.ofSeconds(60).toMillis() / (double) recentMessageCount / 1000.0;

        int projectedMessageCount = (int) recentMessageCount + 1;
        int projectedConnections = (int) recentUniqueReceivers + 1;

        double[] features = FeatureExtractor.extractFeatures(
                projectedMessageCount,
                avgGap,
                messageSize,
                projectedConnections,
                sender.getFailedLoginAttempts(),
                ipChanges
        );

        return AIAnalyzer.analyze(features);
    }
}
