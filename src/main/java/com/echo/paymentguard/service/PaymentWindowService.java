package com.echo.paymentguard.service;

import com.echo.paymentguard.dto.PaymentRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@ConditionalOnProperty(name = "echo.stream.kafka.enabled", havingValue = "true")
public class PaymentWindowService {
    private static final DefaultRedisScript<List> WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local member = ARGV[3]
            local fingerprint = ARGV[4]
            local threshold = tonumber(ARGV[5])
            local ttl = tonumber(ARGV[6])

            redis.call('ZREM', KEYS[1], member)
            redis.call('ZREM', KEYS[2], member)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now - window)

            local earlier = redis.call('ZREVRANGEBYSCORE', KEYS[2], now - 1, now - window, 'LIMIT', 0, 1)
            local later = redis.call('ZRANGEBYSCORE', KEYS[2], now + 1, now + window, 'LIMIT', 0, 1)
            local near = earlier[1] or later[1] or ''
            local pairCount = redis.call('ZCOUNT', KEYS[1], now - window, now)
            local rapid = 0
            if pairCount + 1 >= threshold then
              rapid = redis.call('SET', KEYS[3], member, 'EX', ttl, 'NX') and 1 or 0
            end

            redis.call('ZADD', KEYS[1], now, member)
            redis.call('ZADD', KEYS[2], now, member)
            redis.call('EXPIRE', KEYS[1], ttl)
            redis.call('EXPIRE', KEYS[2], ttl)

            return {near, rapid, pairCount + 1}
            """, List.class);

    private final StringRedisTemplate redis;
    private final Duration window;
    private final int rapidRepeatThreshold;

    public PaymentWindowService(StringRedisTemplate redis,
                                @org.springframework.beans.factory.annotation.Value("${echo.stream.window:10m}") Duration window,
                                @org.springframework.beans.factory.annotation.Value("${echo.stream.rapid-repeat-threshold:5}") int rapidRepeatThreshold) {
        this.redis = redis;
        this.window = window;
        this.rapidRepeatThreshold = rapidRepeatThreshold;
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("echo.stream.window must be positive");
        }
        if (rapidRepeatThreshold < 2) {
            throw new IllegalArgumentException("echo.stream.rapid-repeat-threshold must be at least 2");
        }
    }

    public WindowResult record(String eventId, String paymentId, PaymentRequest payment) {
        String debtor = payment.debtorAccount();
        String creditor = payment.creditorAccount();
        String pairHash = hash(debtor + "\u0000" + creditor);
        String exactHash = hash(pairHash + "\u0000"
                + payment.amount().stripTrailingZeros().toPlainString() + "\u0000"
                + payment.currency().toUpperCase(Locale.ROOT));
        String member = eventId + "|" + paymentId;
        Instant eventTime = payment.initiatedAt().toInstant();
        long ttlSeconds = Math.max(1, window.toSeconds() * 2);

        List<?> result = redis.execute(
                WINDOW_SCRIPT,
                List.of("echo:window:pair:" + pairHash,
                        "echo:window:exact:" + exactHash,
                        "echo:window:rapid:" + pairHash),
                Long.toString(eventTime.toEpochMilli()),
                Long.toString(window.toMillis()),
                member,
                exactHash,
                Integer.toString(rapidRepeatThreshold),
                Long.toString(ttlSeconds)
        );
        if (result == null || result.size() < 3) {
            throw new IllegalStateException("Redis duplicate-window script returned an invalid result");
        }
        String near = result.get(0).toString();
        int rapid = Integer.parseInt(result.get(1).toString());
        int count = Integer.parseInt(result.get(2).toString());
        String mostRecentEarlierPaymentId = near.isBlank() ? null : near.substring(near.lastIndexOf('|') + 1);
        return new WindowResult(mostRecentEarlierPaymentId, rapid == 1, count);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record WindowResult(String mostRecentEarlierPaymentId, boolean rapidRepeat, int pairCount) {
    }
}
