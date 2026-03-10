package com.ballys.realwinner.repository;

import com.ballys.realwinner.model.Bet;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class BetStore {
    private final Map<String, Bet> bets = new ConcurrentHashMap<>();
    
    public void save(Bet bet) { bets.put(bet.betId(), bet); }
    public Collection<Bet> findAll() { return bets.values(); }
}