package com.landclaim.bedrock;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Soft-dependency detection for Floodgate.
 *
 * <p>This class never holds direct references to Floodgate classes so it can be
 * loaded safely when the plugin is not installed.</p>
 */
public final class BedrockSupport {

    private BedrockSupport() {
    }

    public static boolean isFloodgateInstalled() {
        return Bukkit.getPluginManager().getPlugin("floodgate") != null;
    }

    public static boolean isFloodgatePlayer(UUID playerId) {
        if (!isFloodgateInstalled()) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            return (Boolean) isFloodgatePlayer.invoke(api, playerId);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
