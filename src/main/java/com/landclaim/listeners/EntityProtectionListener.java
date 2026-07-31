package com.landclaim.listeners;

import com.landclaim.protection.ClaimAccess;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class EntityProtectionListener implements Listener {

    private final ClaimAccess guard;

    public EntityProtectionListener(ClaimAccess guard) {
        this.guard = guard;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity source = event.getDamager();
        if (source instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            source = shooter;
        }

        if (source instanceof Player player) {
            if (victim instanceof Player target) {
                if (guard.isPvpDisallowed(target, target.getLocation())) {
                    event.setCancelled(true);
                    guard.deny(player, "PvP is disabled in this claim.");
                }
            } else if (victim instanceof Animals) {
                if (!guard.hasAction(player, victim.getLocation(), ClaimAccess.Action.ANIMALS)) {
                    event.setCancelled(true);
                    guard.deny(player, "You can't attack animals here.");
                }
            } else if (victim instanceof Boat || victim instanceof Minecart) {
                if (!guard.hasAction(player, victim.getLocation(), ClaimAccess.Action.VEHICLES)) {
                    event.setCancelled(true);
                    guard.deny(player, "You can't damage that here.");
                }
            } else if (victim instanceof ArmorStand || victim instanceof Hanging) {
                if (!guard.hasAction(player, victim.getLocation(), ClaimAccess.Action.BUILD)) {
                    event.setCancelled(true);
                    guard.deny(player, "This area is claimed.");
                }
            }
        } else if (source instanceof Mob) {
            if (guard.shouldCancelByClaimFlag(victim.getLocation(), "mobs")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            if (!guard.isTeleportAllowed(event.getPlayer(), event.getTo())) {
                event.setCancelled(true);
                guard.deny(event.getPlayer(), "You can't teleport into this claim.");
            }
        }
    }
}
