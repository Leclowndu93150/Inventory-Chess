package com.leclowndu93150.guichess.game;

import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.util.TimeControl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChessChallenge {
    public final UUID challengeId;
    public final ServerPlayer challenger;
    public final ServerPlayer challenged;
    public final TimeControl timeControl;
    public final long challengeTime;
    public final long expiryTime;
    public final int hintsAllowed;
    
    private final List<ItemStack> challengerBet;
    private List<ItemStack> acceptedBet = new ArrayList<>();
    private final PieceColor challengerPreferredSide; // null = random

    public ChessChallenge(ServerPlayer challenger, ServerPlayer challenged, TimeControl timeControl) {
        this(challenger, challenged, timeControl, new ArrayList<>(), null);
    }
    
    public ChessChallenge(ServerPlayer challenger, ServerPlayer challenged, TimeControl timeControl, List<ItemStack> bet, PieceColor preferredSide) {
        this.challengeId = UUID.randomUUID();
        this.challenger = challenger;
        this.challenged = challenged;
        this.timeControl = timeControl;
        this.challengerBet = new ArrayList<>(bet);
        this.challengerPreferredSide = preferredSide;
        this.hintsAllowed = 0; // Human vs human has no hints
        this.challengeTime = System.currentTimeMillis();
        this.expiryTime = challengeTime + 60000; // 60 second expiry
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }
    
    public boolean hasBet() {
        return !challengerBet.isEmpty();
    }
    
    public List<ItemStack> getChallengerBet() {
        return new ArrayList<>(challengerBet);
    }
    
    public List<ItemStack> getAcceptedBet() {
        return new ArrayList<>(acceptedBet);
    }
    
    public void setAcceptedBet(List<ItemStack> bet) {
        this.acceptedBet = new ArrayList<>(bet);
    }
    
    public List<ItemStack> getAllBetItems() {
        List<ItemStack> allItems = new ArrayList<>();
        allItems.addAll(challengerBet);
        allItems.addAll(acceptedBet);
        return allItems;
    }
    
    public PieceColor getChallengerPreferredSide() {
        return challengerPreferredSide;
    }
    
    public ServerPlayer getChallenger() {
        return challenger;
    }
    
    public ServerPlayer getChallenged() {
        return challenged;
    }
}
