package com.leclowndu93150.guichess.game;

import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.util.TimeControl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a pending chess game invitation between two players.
 * <p>
 * A ChessChallenge encapsulates all the parameters and state of a game invitation,
 * including the challenger and challenged players, time controls, betting stakes,
 * and preferred piece colors. Challenges have a fixed expiry time and follow a
 * lifecycle from creation to acceptance/rejection/expiry.
 * </p>
 * <p>
 * Challenge Lifecycle:
 * <ol>
 *   <li>Created by challenger with game parameters</li>
 *   <li>Sent to challenged player for acceptance</li>
 *   <li>Either accepted (creates ChessGame), rejected, or expires after 60 seconds</li>
 * </ol>
 * </p>
 * 
 * @author leclowndu93150
 * @see ChessGame
 * @see TimeControl
 */
public class ChessChallenge {
    /** Unique identifier for this challenge instance */
    public final UUID challengeId;
    
    /** The player who initiated this challenge */
    public final ServerPlayer challenger;
    
    /** The player being challenged */
    public final ServerPlayer challenged;
    
    /** Time control settings for the proposed game */
    public final TimeControl timeControl;
    
    /** Timestamp when this challenge was created (milliseconds since epoch) */
    public final long challengeTime;
    
    /** Timestamp when this challenge expires (milliseconds since epoch) */
    public final long expiryTime;
    
    /** Number of hints allowed in the game (0 for human vs human) */
    public final int hintsAllowed;
    
    /** Items wagered by the challenger */
    private final List<ItemStack> challengerBet;
    
    /** Items wagered by the challenged player upon acceptance */
    private List<ItemStack> acceptedBet = new ArrayList<>();
    
    /** Challenger's preferred piece color (null for random assignment) */
    private final PieceColor challengerPreferredSide;

    /**
     * Creates a new chess challenge with default settings (no bet, random colors).
     * 
     * @param challenger the player initiating the challenge
     * @param challenged the player being challenged
     * @param timeControl the time control settings for the game
     */
    public ChessChallenge(ServerPlayer challenger, ServerPlayer challenged, TimeControl timeControl) {
        this(challenger, challenged, timeControl, new ArrayList<>(), null);
    }
    
    /**
     * Creates a new chess challenge with full customization options.
     * 
     * @param challenger the player initiating the challenge
     * @param challenged the player being challenged
     * @param timeControl the time control settings for the game
     * @param bet the items wagered by the challenger (copied defensively)
     * @param preferredSide the challenger's preferred piece color, or null for random
     */
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

    /**
     * Checks if this challenge has expired and should be automatically rejected.
     * Challenges expire 60 seconds after creation.
     * 
     * @return true if the current time exceeds the expiry time
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }
    
    /**
     * Checks if this challenge includes a wager from the challenger.
     * 
     * @return true if the challenger has wagered any items
     */
    public boolean hasBet() {
        return !challengerBet.isEmpty();
    }
    
    /**
     * Returns a defensive copy of the challenger's wagered items.
     * 
     * @return a new list containing copies of the challenger's bet items
     */
    public List<ItemStack> getChallengerBet() {
        return new ArrayList<>(challengerBet);
    }
    
    /**
     * Returns a defensive copy of the challenged player's wagered items.
     * This will be empty until the challenge is accepted with a counter-bet.
     * 
     * @return a new list containing copies of the accepted bet items
     */
    public List<ItemStack> getAcceptedBet() {
        return new ArrayList<>(acceptedBet);
    }
    
    /**
     * Sets the wagered items from the challenged player upon challenge acceptance.
     * Creates a defensive copy of the provided list.
     * 
     * @param bet the items wagered by the challenged player
     */
    public void setAcceptedBet(List<ItemStack> bet) {
        this.acceptedBet = new ArrayList<>(bet);
    }
    
    /**
     * Returns all wagered items from both players combined.
     * Useful for prize pool calculations and game setup.
     * 
     * @return a new list containing all bet items from both players
     */
    public List<ItemStack> getAllBetItems() {
        List<ItemStack> allItems = new ArrayList<>();
        allItems.addAll(challengerBet);
        allItems.addAll(acceptedBet);
        return allItems;
    }
    
    /**
     * Returns the challenger's preferred piece color.
     * 
     * @return the preferred color, or null if random assignment is requested
     */
    public PieceColor getChallengerPreferredSide() {
        return challengerPreferredSide;
    }
    
    /**
     * Returns the player who initiated this challenge.
     * 
     * @return the challenging player
     */
    public ServerPlayer getChallenger() {
        return challenger;
    }
    
    /**
     * Returns the player being challenged.
     * 
     * @return the challenged player
     */
    public ServerPlayer getChallenged() {
        return challenged;
    }
}
