package com.ballys.realwinner.util;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages concurrency locks for individual matches.
 * This prevents "ghost bets" by ensuring we don't try to place a bet
 * and finish a match at the exact same millisecond.
 */
@Component
public class EventLockManager {
    
    private final Map<String, ReadWriteLock> eventLocks = new ConcurrentHashMap<>();

    /**
     * Gets or creates a lock specifically for one match event.
     * * @param eventId The unique ID of the match.
     * @return A ReadWriteLock that can be shared by betters (Read) or claimed by the system when finishing the match (Write).
     */
    public ReadWriteLock getLockForEvent(String eventId) {
        return eventLocks.computeIfAbsent(eventId, k -> new ReentrantReadWriteLock());
    }
}