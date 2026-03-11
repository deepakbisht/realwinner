package com.ballys.realwinner.repository;

import com.ballys.realwinner.model.Bet;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class BetStore {

    
    // 1. lookups by User
    private final Map<String, Map<String, Bet>> betsByUserId = new ConcurrentHashMap<>();

    // 2. Fast lookups by Match for settlement
    private final Map<String, Map<String, Bet>> betsByEventId = new ConcurrentHashMap<>();

    // The concurrency control mechanism
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public void save(Bet bet) {
        lock.writeLock().lock();
        // update By Use
        betsByUserId.computeIfAbsent(bet.userId(), k -> new ConcurrentHashMap<>())
                    .put(bet.betId(), bet);

        // update By Event
        betsByEventId.computeIfAbsent(bet.eventId(), k -> new ConcurrentHashMap<>())
                     .put(bet.betId(), bet);
        lock.writeLock().unlock();
    }

    // By user query
    public Collection<Bet> findByUserId(String userId) {
        lock.readLock().lock(); // Allow concurrent reads, but wait if a write is happening
        try {
            Map<String, Bet> userBets = betsByUserId.get(userId);
            return userBets != null ? userBets.values() : Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    // Lookup by Match
    public Collection<Bet> findByEventId(String eventId) {
        lock.readLock().lock();
        try {
            Map<String, Bet> eventBets = betsByEventId.get(eventId);
            return eventBets != null ? eventBets.values() : Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }
}