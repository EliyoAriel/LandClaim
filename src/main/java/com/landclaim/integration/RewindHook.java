package com.landclaim.integration;

import com.landclaim.data.Claim;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reflection hook into Rewind's chunk-exclusion API (no compile dependency).
 * Excludes the chunks a claim covers so Rewind never restores over claimed land.
 *
 * Rewind's exclusion table is ref-counted, so every exclude must eventually be
 * matched by an unexclude. This class keeps its own bookkeeping (per claim,
 * per world) as the source of truth and only touches Rewind through it.
 */
public final class RewindHook {

    private static boolean enabled = false;
    private static Object api;
    private static Method excludeChunk;
    private static Method unexcludeChunk;
    private static final Map<Integer, ChunkSet> excludedByClaim = new HashMap<>();

    private static final class ChunkSet {
        final String world;
        final Set<String> keys = new HashSet<>();

        ChunkSet(String world) {
            this.world = world;
        }
    }

    private RewindHook() {}

    /** Resolve Rewind's API (no-op if absent). Does not touch the exclusion table. */
    public static void init() {
        enabled = false;
        api = null;
        excludeChunk = null;
        unexcludeChunk = null;
        try {
            Plugin rewind = Bukkit.getPluginManager().getPlugin("Rewind");
            if (rewind == null || !rewind.isEnabled()) return;

            Class<?> pluginClass = Class.forName("com.rewind.RewindPlugin");
            Method getAPI = pluginClass.getMethod("getAPI");
            api = getAPI.invoke(null);
            if (api == null) return;

            excludeChunk = api.getClass().getMethod("excludeChunk", String.class, int.class, int.class);
            unexcludeChunk = api.getClass().getMethod("unexcludeChunk", String.class, int.class, int.class);
            enabled = true;
        } catch (Exception e) {
            enabled = false;
            api = null;
            excludeChunk = null;
            unexcludeChunk = null;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void excludeClaim(Claim claim) {
        if (!enabled || claim == null) return;
        String world = worldName(claim);
        if (world == null) return;
        ChunkSet stored = excludedByClaim.computeIfAbsent(claim.getId(), k -> new ChunkSet(world));
        for (String key : chunkKeys(claim.getX(), claim.getZ(), claim.getRadius())) {
            if (stored.keys.add(key)) {
                exclude(world, key);
            }
        }
    }

    /**
     * Recompute exclusions after a claim's radius changes (tier upgrade).
     * Must be called before the claim's radius is updated.
     */
    public static void syncClaim(Claim claim, int oldRadius, int newRadius) {
        if (!enabled || claim == null || oldRadius == newRadius) return;
        String world = worldName(claim);
        if (world == null) return;

        Set<String> oldChunks = chunkKeys(claim.getX(), claim.getZ(), oldRadius);
        Set<String> newChunks = chunkKeys(claim.getX(), claim.getZ(), newRadius);
        ChunkSet stored = excludedByClaim.computeIfAbsent(claim.getId(), k -> new ChunkSet(world));

        for (String key : newChunks) {
            if (stored.keys.add(key)) {
                exclude(world, key);
            }
        }
        for (String key : oldChunks) {
            if (!newChunks.contains(key) && stored.keys.remove(key)) {
                unexclude(world, key);
            }
        }
    }

    public static void unexcludeClaim(Claim claim) {
        if (!enabled || claim == null) return;
        ChunkSet stored = excludedByClaim.remove(claim.getId());
        if (stored == null) return;
        for (String key : stored.keys) {
            unexclude(stored.world, key);
        }
    }

    /**
     * Full reconcile: releases every tracked exclusion in Rewind, clears local
     * bookkeeping, then re-excludes all active claims. Use after loading claims,
     * after a Rewind (re)enable whose exclusion table was wiped, or after a
     * config/claim reload.
     */
    public static void rebuild(Collection<Claim> claims) {
        if (!enabled) return;
        for (ChunkSet stored : excludedByClaim.values()) {
            for (String key : stored.keys) {
                unexclude(stored.world, key);
            }
        }
        excludedByClaim.clear();
        for (Claim claim : claims) {
            if (claim.isActive()) {
                excludeClaim(claim);
            }
        }
    }

    private static Set<String> chunkKeys(int centerX, int centerZ, int radius) {
        Set<String> keys = new HashSet<>();
        int minCX = (centerX - radius) >> 4;
        int maxCX = (centerX + radius) >> 4;
        int minCZ = (centerZ - radius) >> 4;
        int maxCZ = (centerZ + radius) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                keys.add(cx + ":" + cz);
            }
        }
        return keys;
    }

    private static void exclude(String world, String key) {
        try {
            String[] parts = key.split(":");
            excludeChunk.invoke(api, world, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception ignored) {}
    }

    private static void unexclude(String world, String key) {
        try {
            String[] parts = key.split(":");
            unexcludeChunk.invoke(api, world, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception ignored) {}
    }

    private static String worldName(Claim claim) {
        World world = Bukkit.getWorld(claim.getWorld());
        return world != null ? world.getName() : null;
    }
}
