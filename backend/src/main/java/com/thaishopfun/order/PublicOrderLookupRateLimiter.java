package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding window for the public order lookup: at most 5 failed attempts per client IP and per order
 * number in 15 minutes, then 429 TOO_MANY_LOOKUPS. Single-node only (resets on restart).
 */
@Component
public class PublicOrderLookupRateLimiter {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_KEYS = 20_000;

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public void check(String ip, String orderNumber) {
        long now = System.currentTimeMillis();
        if (blocked("ip:" + ip, now) || (orderNumber != null && blocked("no:" + orderNumber, now))) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_LOOKUPS", "Too many attempts, please try again later");
        }
    }

    public void fail(String ip, String orderNumber) {
        long now = System.currentTimeMillis();
        if (failures.size() > MAX_KEYS) prune(now);
        add("ip:" + ip, now);
        if (orderNumber != null) add("no:" + orderNumber, now);
    }

    /** Tests only. */
    public void reset() { failures.clear(); }

    private boolean blocked(String key, long now) {
        var list = failures.get(key);
        if (list == null) return false;
        synchronized (list) {
            trim(list, now);
            return list.size() >= MAX_FAILURES;
        }
    }

    private void add(String key, long now) {
        var list = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (list) {
            trim(list, now);
            list.addLast(now);
        }
    }

    private static void trim(Deque<Long> list, long now) {
        long from = now - WINDOW.toMillis();
        while (!list.isEmpty() && list.peekFirst() < from) list.pollFirst();
    }

    private void prune(long now) {
        failures.entrySet().removeIf(e -> { synchronized (e.getValue()) { trim(e.getValue(), now); return e.getValue().isEmpty(); } });
    }
}