package com.landclaim.listeners;

import com.landclaim.LandClaimPlugin;
import com.landclaim.config.ConfigManager;
import com.landclaim.data.Claim;
import com.landclaim.data.ClaimRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

public class ProtectionListener implements Listener {

    private final LandClaimPlugin plugin;
    private final ClaimRepository repository;
    private final ConfigManager configManager;

    public ProtectionListener(LandClaimPlugin plugin, ClaimRepository repository, ConfigManager configManager) {
        this.plugin = plugin;
        this.repository = repository;
        this.configManager = configManager;
    }

    private boolean isAllowed(Player player, Location loc) {
        UUID worldUuid = loc.getWorld().getUID();
        if (!configManager.isWorldEnabled(loc.getWorld().getName())) return true;
        Claim claim = repository.getClaimAt(worldUuid, loc.getBlockX(), loc.getBlockZ());
        if (claim == null) return true;
        if (!claim.isActive()) return true;
        if (claim.getOwner().equals(player.getUniqueId())) return true;
        return claim.getMembers().contains(player.getUniqueId());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isAllowed(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("This area is claimed.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isAllowed(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("This area is claimed.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (!isAllowed(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("This area is claimed.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!configManager.isWorldEnabled(victim.getWorld().getName())) return;
        Claim claim = repository.getClaimAt(victim.getWorld().getUID(), victim.getLocation().getBlockX(), victim.getLocation().getBlockZ());
        if (claim == null || !claim.isActive()) return;
        boolean attackerTrusted = claim.getOwner().equals(attacker.getUniqueId()) || claim.getMembers().contains(attacker.getUniqueId());
        boolean victimTrusted = claim.getOwner().equals(victim.getUniqueId()) || claim.getMembers().contains(victim.getUniqueId());
        if (!attackerTrusted || !victimTrusted) {
            event.setCancelled(true);
            attacker.sendActionBar(Component.text("PvP is disabled in this claim.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Claim claim = repository.getClaimAt(block.getWorld().getUID(), block.getX(), block.getZ());
            return claim != null && claim.isActive();
        });
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Claim claim = repository.getClaimAt(block.getWorld().getUID(), block.getX(), block.getZ());
            return claim != null && claim.isActive();
        });
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!isAllowed(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("This area is claimed.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!isAllowed(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("This area is claimed.", NamedTextColor.RED));
        }
    }
}
