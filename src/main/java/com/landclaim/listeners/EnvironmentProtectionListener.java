package com.landclaim.listeners;

import com.landclaim.protection.ClaimAccess;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

public class EnvironmentProtectionListener implements Listener {

    private final ClaimAccess guard;

    public EnvironmentProtectionListener(ClaimAccess guard) {
        this.guard = guard;
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> guard.shouldCancelByClaimFlag(block.getLocation(), "explosions"));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> guard.shouldCancelByClaimFlag(block.getLocation(), "explosions"));
    }

    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        if (guard.shouldCancelByClaimFlag(event.getSource().getLocation(), "firespread")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (guard.shouldCancelByClaimFlag(event.getBlock().getLocation(), "firespread")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        if (guard.shouldCancelByClaimFlag(event.getToBlock().getLocation(), "fluidflow")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (guard.shouldCancelByClaimFlag(event.getEntity().getLocation(), "mobs")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock) {
            if (guard.shouldCancelByClaimFlag(event.getBlock().getLocation(), "gravity")) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getBlock().getType() == Material.FARMLAND
                && event.getTo() == Material.DIRT
                && guard.shouldCancelByClaimFlag(event.getBlock().getLocation(), "crops")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockFade(BlockFadeEvent event) {
        if (guard.shouldCancelByClaimFlag(event.getBlock().getLocation(), "decay")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (shouldCancelPiston(event.getBlock().getLocation(), event.getBlocks(), event.getDirection(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (shouldCancelPiston(event.getBlock().getLocation(), event.getBlocks(), event.getDirection(), false)) {
            event.setCancelled(true);
        }
    }

    private boolean shouldCancelPiston(Location pistonLoc, List<Block> blocks, BlockFace direction, boolean extend) {
        if (guard.shouldCancelByClaimFlag(pistonLoc, "pistons")) return true;
        if (blocks == null) return false;
        for (Block block : blocks) {
            Location target = extend
                    ? block.getLocation().add(direction.getDirection())
                    : block.getLocation().subtract(direction.getDirection());
            if (guard.shouldCancelByClaimFlag(target, "pistons")) return true;
        }
        return false;
    }
}
