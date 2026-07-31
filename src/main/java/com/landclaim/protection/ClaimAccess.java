package com.landclaim.protection;

import com.landclaim.LandClaimPlugin;
import com.landclaim.config.ConfigManager;
import com.landclaim.data.Claim;
import com.landclaim.data.ClaimRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ClaimAccess {

    public enum Action {
        BUILD, USE, REDSTONE, DOORS, VEHICLES, ANIMALS, ITEMS, PVP, TELEPORT
    }

    private final LandClaimPlugin plugin;
    private final ClaimRepository repository;
    private final ConfigManager configManager;

    public ClaimAccess(LandClaimPlugin plugin, ClaimRepository repository, ConfigManager configManager) {
        this.plugin = plugin;
        this.repository = repository;
        this.configManager = configManager;
    }

    public boolean isOutsideClaim(Location loc) {
        if (!configManager.isWorldEnabled(loc.getWorld().getName())) return true;
        Claim claim = repository.getClaimAt(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockZ());
        return claim == null || !claim.isActive();
    }

    public boolean hasAction(Player player, Location loc, Action action) {
        if (isOutsideClaim(loc)) return true;
        Claim claim = repository.getClaimAt(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockZ());
        if (claim == null) return true;
        if (claim.getOwner().equals(player.getUniqueId())) return true;
        if (claim.getMembers().contains(player.getUniqueId())) {
            return repository.getMemberFlag(claim.getId(), player.getUniqueId(), action.name().toLowerCase());
        }
        return switch (action) {
            case BUILD -> repository.getClaimFlag(claim.getId(), "public-build");
            case USE, REDSTONE, DOORS, VEHICLES, ANIMALS -> repository.getClaimFlag(claim.getId(), "public-use");
            case ITEMS -> repository.getClaimFlag(claim.getId(), "public-items");
            default -> false;
        };
    }

    public boolean shouldCancelByClaimFlag(Location loc, String flag) {
        if (!configManager.isWorldEnabled(loc.getWorld().getName())) return false;
        Claim claim = repository.getClaimAt(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockZ());
        if (claim == null || !claim.isActive()) return false;
        return !repository.getClaimFlag(claim.getId(), flag);
    }

    public boolean isTeleportAllowed(Player player, Location loc) {
        if (isOutsideClaim(loc)) return true;
        Claim claim = repository.getClaimAt(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockZ());
        if (claim == null) return true;
        if (claim.getOwner().equals(player.getUniqueId())) return true;
        if (claim.getMembers().contains(player.getUniqueId())) {
            return repository.getMemberFlag(claim.getId(), player.getUniqueId(), "teleport");
        }
        return repository.getClaimFlag(claim.getId(), "teleport");
    }

    public boolean isPvpDisallowed(Player victim, Location loc) {
        if (isOutsideClaim(loc)) return false;
        Claim claim = repository.getClaimAt(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockZ());
        if (claim == null) return false;
        if (!repository.getClaimFlag(claim.getId(), "pvp")) return true;
        return !claim.getOwner().equals(victim.getUniqueId())
                && claim.getMembers().contains(victim.getUniqueId())
                && !repository.getMemberFlag(claim.getId(), victim.getUniqueId(), "pvp");
    }

    public void deny(Player player, String message) {
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }

    public String resolveOwnerName(UUID uuid) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(uuid);
        return owner.getName() != null ? owner.getName() : uuid.toString();
    }

    public LandClaimPlugin getPlugin() { return plugin; }
}
