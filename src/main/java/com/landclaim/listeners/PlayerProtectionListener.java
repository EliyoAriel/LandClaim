package com.landclaim.listeners;

import com.landclaim.protection.ClaimAccess;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;

public class PlayerProtectionListener implements Listener {

    private final ClaimAccess guard;

    public PlayerProtectionListener(ClaimAccess guard) {
        this.guard = guard;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!guard.hasAction(event.getPlayer(), event.getBlock().getLocation(), ClaimAccess.Action.BUILD)) {
            event.setCancelled(true);
            guard.deny(event.getPlayer(), "This area is claimed.");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!guard.hasAction(event.getPlayer(), event.getBlock().getLocation(), ClaimAccess.Action.BUILD)) {
            event.setCancelled(true);
            guard.deny(event.getPlayer(), "This area is claimed.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Material material = event.getClickedBlock().getType();
        ClaimAccess.Action action = classifyInteract(material);
        if (action == null) return;
        if (!guard.hasAction(event.getPlayer(), event.getClickedBlock().getLocation(), action)) {
            event.setCancelled(true);
            guard.deny(event.getPlayer(), "This area is claimed.");
        }
    }

    private ClaimAccess.Action classifyInteract(Material material) {
        if (isContainer(material)) return ClaimAccess.Action.USE;
        if (isRedstone(material)) return ClaimAccess.Action.REDSTONE;
        if (isDoor(material)) return ClaimAccess.Action.DOORS;
        return null;
    }

    private boolean isContainer(Material material) {
        switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL, FURNACE, SMOKER, BLAST_FURNACE, HOPPER,
                 DISPENSER, DROPPER, CRAFTING_TABLE, ENCHANTING_TABLE, ANVIL,
                 CHIPPED_ANVIL, DAMAGED_ANVIL -> { return true; }
            default -> { return material.name().endsWith("_SHULKER_BOX"); }
        }
    }

    private boolean isRedstone(Material material) {
        if (material == Material.LEVER || material == Material.REPEATER || material == Material.COMPARATOR
                || material == Material.NOTE_BLOCK || material == Material.DAYLIGHT_DETECTOR
                || material == Material.TARGET) return true;
        String name = material.name();
        return name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE");
    }

    private boolean isDoor(Material material) {
        String name = material.name();
        return name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR")
                || name.endsWith("_FENCE_GATE") || name.endsWith("_BED");
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        ClaimAccess.Action action = classifyEntityInteract(event.getRightClicked());
        if (action == null) return;
        Player player = event.getPlayer();
        if (!guard.hasAction(player, event.getRightClicked().getLocation(), action)) {
            event.setCancelled(true);
            guard.deny(player, "You can't interact with that here.");
        }
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand)) return;
        Player player = event.getPlayer();
        if (!guard.hasAction(player, event.getRightClicked().getLocation(), ClaimAccess.Action.USE)) {
            event.setCancelled(true);
            guard.deny(player, "You can't interact with that here.");
        }
    }

    private ClaimAccess.Action classifyEntityInteract(Entity entity) {
        if (entity instanceof Animals) return ClaimAccess.Action.ANIMALS;
        if (entity instanceof ItemFrame || entity instanceof ArmorStand || entity instanceof LeashHitch) {
            return ClaimAccess.Action.USE;
        }
        if (entity instanceof Boat || entity instanceof Minecart) return ClaimAccess.Action.VEHICLES;
        return null;
    }

    @EventHandler
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (!guard.hasAction(player, event.getEntity().getLocation(), ClaimAccess.Action.BUILD)) {
            event.setCancelled(true);
            guard.deny(player, "This area is claimed.");
        }
    }

    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player player)) return;
        if (!guard.hasAction(player, event.getEntity().getLocation(), ClaimAccess.Action.BUILD)) {
            event.setCancelled(true);
            guard.deny(player, "This area is claimed.");
        }
    }

    @EventHandler
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getAttacker() instanceof Player player)) return;
        if (!guard.hasAction(player, event.getVehicle().getLocation(), ClaimAccess.Action.VEHICLES)) {
            event.setCancelled(true);
            guard.deny(player, "You can't damage that here.");
        }
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!guard.hasAction(player, event.getItem().getLocation(), ClaimAccess.Action.ITEMS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!guard.hasAction(player, player.getLocation(), ClaimAccess.Action.ITEMS)) {
            event.setCancelled(true);
            guard.deny(player, "You can't drop items here.");
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!guard.hasAction(event.getPlayer(), event.getBlock().getLocation(), ClaimAccess.Action.BUILD)) {
            event.setCancelled(true);
            guard.deny(event.getPlayer(), "This area is claimed.");
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!guard.hasAction(event.getPlayer(), event.getBlock().getLocation(), ClaimAccess.Action.BUILD)) {
            event.setCancelled(true);
            guard.deny(event.getPlayer(), "This area is claimed.");
        }
    }
}
