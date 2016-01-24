package com.mengcraft.playersql;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class SafeLoginEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled = false;
    private static final HandlerList handlers = new HandlerList();
    private String cancelReason = null;

    public SafeLoginEvent(Player who) {
        super(who);
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    public void setCancelReason(String reason) {
        cancelReason = reason;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
