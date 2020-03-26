package com.vicr123.bnbshops;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class PlayerState {
    public enum State {
        PLAYER_STATE_IDLE,
        PLAYER_STATE_CHEST,
        PLAYER_STATE_SIGN
    }

    public static class SignMetadata {
        public ItemStack item;
    }

    HashMap<String, State> playerStates;
    HashMap<String, SignMetadata> playerSignState;

    PlayerState() {
        playerStates = new HashMap<>();
        playerSignState = new HashMap<>();
    }

    public void setUserState(Player player, State state) {
        playerStates.put(player.getUniqueId().toString(), state);
    }

    public void setSignModifyState(Player player, SignMetadata metadata) {
        setUserState(player, State.PLAYER_STATE_SIGN);
        playerSignState.put(player.getUniqueId().toString(), metadata);
    }

    public SignMetadata getSignModifyState(Player player) {
        if (!playerStates.containsKey(player.getUniqueId().toString())) return null;
        return playerSignState.get(player.getUniqueId().toString());
    }

    public void clearUserState(Player player) {
        playerStates.remove(player.getUniqueId().toString());
        playerSignState.remove(player.getUniqueId().toString());
    }

    public State playerState(Player player) {
        return playerStates.getOrDefault(player.getUniqueId().toString(), State.PLAYER_STATE_IDLE);
    }
}
