package com.landclaim.listeners;

import com.landclaim.LandClaimPlugin;
import com.landclaim.config.ConfigManager;
import com.landclaim.data.Claim;
import com.landclaim.data.ClaimRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GreetingListener implements Listener {

    private static final Title.Times TITLE_TIMES = Title.Times.times(
            Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000));
    private static final long ACTIONBAR_DURATION_TICKS = 80L;

    private final LandClaimPlugin plugin;
    private final ClaimRepository repository;
    private final ConfigManager configManager;
    private final Map<UUID, Integer> currentClaim = new HashMap<>();
    private final Map<UUID, BukkitTask> actionBarClearTasks = new HashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public GreetingListener(LandClaimPlugin plugin, ClaimRepository repository, ConfigManager configManager) {
        this.plugin = plugin;
        this.repository = repository;
        this.configManager = configManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        handlePositionChange(event.getPlayer(), event.getTo().getWorld().getUID(),
                event.getTo().getBlockX(), event.getTo().getBlockZ());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        handlePositionChange(event.getPlayer(), event.getTo().getWorld().getUID(),
                event.getTo().getBlockX(), event.getTo().getBlockZ());
    }

    private void handlePositionChange(Player player, UUID worldUuid, int x, int z) {
        Claim claim = repository.getClaimAt(worldUuid, x, z);
        Integer newId = (claim != null && claim.isActive()) ? claim.getId() : null;
        Integer oldId = currentClaim.put(player.getUniqueId(), newId);
        if (newId == null && oldId == null) return;
        if (newId != null && newId.equals(oldId)) return;
        if (oldId != null) {
            Claim old = repository.getClaimById(oldId);
            if (old != null) {
                showTitle(player, configManager.getFarewellTitleFormat(), old);
            }
        }
        if (newId != null && claim != null) {
            showTitle(player, configManager.getGreetingTitleFormat(), claim);
            showActionBar(player, claim);
        }
    }

    private void showTitle(Player player, String titleFormat, Claim claim) {
        String ownerName = resolveOwnerName(claim.getOwner());
        Component title = legacy.deserialize(titleFormat
                .replace("{name}", claim.getName())
                .replace("{owner}", ownerName)
                .replace("{displayname}", claim.getDisplayName()));
        Component subtitle = legacy.deserialize(configManager.getTitleSubtitleFormat()
                .replace("{owner}", ownerName));
        player.showTitle(Title.title(title, subtitle, TITLE_TIMES));
    }

    private void showActionBar(Player player, Claim claim) {
        String format = configManager.getActionbarFormat()
                .replace("{owner}", resolveOwnerName(claim.getOwner()))
                .replace("{name}", claim.getName())
                .replace("{displayname}", claim.getDisplayName());        player.sendActionBar(legacy.deserialize(format));
        BukkitTask previous = actionBarClearTasks.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }
        BukkitTask clear = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            actionBarClearTasks.remove(player.getUniqueId());
            if (player.isOnline()) {
                player.sendActionBar(Component.empty());
            }
        }, ACTIONBAR_DURATION_TICKS);
        actionBarClearTasks.put(player.getUniqueId(), clear);
    }

    private String resolveOwnerName(UUID uuid) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(uuid);
        return owner.getName() != null ? owner.getName() : uuid.toString();
    }
}
