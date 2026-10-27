package com.microloan.scoringservice.antifraud;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SlidingWindowRateLimiter {

    private final Map<String, Deque<Instant>> applicationsByPassport = new ConcurrentHashMap<>();
    private final int maxApplications;
    private final Duration window;
    private final Clock clock;

    public SlidingWindowRateLimiter(@Value("${scoring.antifraud.max-applications:3}") int maxApplications,
                                    @Value("${scoring.antifraud.window:24h}") Duration window,
                                    Clock clock) {
        if (maxApplications < 1) {
            throw new IllegalArgumentException("maxApplications должен быть положительным");
        }
        this.maxApplications = maxApplications;
        this.window = window;
        this.clock = clock;
    }

    public boolean tryAcquire(String passport) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(window);
        Deque<Instant> timestamps = applicationsByPassport.computeIfAbsent(passport, key -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(windowStart)) {
                timestamps.pollFirst();
            }
            timestamps.addLast(now);
            if (timestamps.size() > maxApplications + 1) {
                timestamps.pollFirst();
            }
            return timestamps.size() <= maxApplications;
        }
    }

    public int getMaxApplications() {
        return maxApplications;
    }

    public Duration getWindow() {
        return window;
    }
}
