package com.landclaim.boundary;

import com.landclaim.LandClaimPlugin;
import com.landclaim.data.Claim;
import com.landclaim.util.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BoundaryManager implements Listener {

    private static final long REFRESH_INTERVAL_TICKS = 10L;

    private final LandClaimPlugin plugin;
    private final Map<UUID, Integer> active = new HashMap<>();
    private BukkitTask task;

    public BoundaryManager(LandClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::render, 0L, REFRESH_INTERVAL_TICKS);
    }

    public void toggle(Player player, Claim claim) {
        Integer current = active.get(player.getUniqueId());
        if (current != null && current == claim.getId()) {
            active.remove(player.getUniqueId());
        } else {
            active.put(player.getUniqueId(), claim.getId());
            ParticleUtil.showClaimBoundary(player, claim);
        }
    }

    public boolean isActive(Player player, Claim claim) {
        return active.get(player.getUniqueId()) != null
                && active.get(player.getUniqueId()) == claim.getId();
    }

    public void disable(Player player) {
        active.remove(player.getUniqueId());
    }

    private void render() {
        if (active.isEmpty()) return;
        active.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) return true;
            Claim claim = plugin.getClaimRepository().getClaimById(entry.getValue());
            if (claim == null || !claim.isActive()) return true;
            ParticleUtil.showClaimBoundary(player, claim);
            return false;
        });
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        active.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }
}
