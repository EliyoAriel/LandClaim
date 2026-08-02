package com.landclaim.api;

import com.landclaim.LandClaimPlugin;
import com.landclaim.protection.ClaimAccess;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LandClaimAPI {

    private static LandClaimPlugin plugin;

    private static final Set<String> PARACHUTE_SPAWNS = ConcurrentHashMap.newKeySet();

    private LandClaimAPI() {}

    public static void init(LandClaimPlugin instance) {
        plugin = instance;
    }

    public static boolean isAvailable() {
        return plugin != null && plugin.isEnabled();
    }

    public static LandClaimPlugin getPlugin() {
        return plugin;
    }

    public static boolean isInClaim(Location loc) {
        if (!isAvailable() || loc == null || loc.getWorld() == null) return false;
        return !plugin.getClaimAccess().isOutsideClaim(loc);
    }

    public static boolean canBuild(Player player, Location loc) {
        if (!isAvailable() || player == null || loc == null) return true;
        return plugin.getClaimAccess().hasAction(player, loc, ClaimAccess.Action.BUILD);
    }

    public static void registerParachuteSpawn(Location loc) {
        PARACHUTE_SPAWNS.add(blockKey(loc));
    }

    public static void clearParachuteSpawn(Location loc) {
        PARACHUTE_SPAWNS.remove(blockKey(loc));
    }

    public static boolean isParachuteSpawn(Location loc) {
        return PARACHUTE_SPAWNS.contains(blockKey(loc));
    }

    private static String blockKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
