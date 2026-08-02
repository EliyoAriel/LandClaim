package com.landclaim.listeners;

import com.landclaim.api.LandClaimAPI;
import com.landclaim.integration.RewindHook;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

public class PluginEnableListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if (!name.equals("Rewind") && !name.equals("SupplyDrop")) return;

        boolean wasEnabled = RewindHook.isEnabled();
        RewindHook.init();

        // Rewind just came online (its exclusion table was wiped on disable):
        // rebuild every claim's exclusions from scratch.
        if (!wasEnabled && RewindHook.isEnabled() && name.equals("Rewind")) {
            RewindHook.rebuild(LandClaimAPI.getPlugin().getClaimRepository().getAllClaims());
        }
    }
}
