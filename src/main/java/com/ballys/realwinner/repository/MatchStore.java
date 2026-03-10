package com.ballys.realwinner.repository;

import com.ballys.realwinner.model.Match;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class MatchStore {
    private final Map<String, Match> matches = new ConcurrentHashMap<>();
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    public long getNextSequence() { return sequenceCounter.incrementAndGet(); }
    public void save(Match match) { matches.put(match.eventId(), match); }
    public Match findById(String eventId) { return matches.get(eventId); }
    public Collection<Match> findAll() { return matches.values(); }
}