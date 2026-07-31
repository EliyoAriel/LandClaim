package com.landclaim.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class GuiInputHandler implements Listener {

    private final GuiManager manager;

    public GuiInputHandler(GuiManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        GuiSession session = manager.getSession(player.getUniqueId());
        if (session == null || session.getPendingInput() == null) return;

        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(manager.getPlugin(), () -> manager.handleInput(player, message));
    }
}
