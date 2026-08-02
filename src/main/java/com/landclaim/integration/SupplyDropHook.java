package com.landclaim.integration;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

public final class SupplyDropHook {

    public static final NamespacedKey CRATE_KEY = new NamespacedKey("supplydrop", "crate");
    public static final NamespacedKey PARACHUTE_KEY = new NamespacedKey("supplydrop", "parachute");
    public static final NamespacedKey CRATE_LOOT_KEY = new NamespacedKey("supplydrop", "crate-loot");

    private SupplyDropHook() {}

    public static boolean isSupplyCrate(Block block) {
        if (block == null || block.getType() != Material.BARREL) return false;
        if (!(block.getState() instanceof TileState tile)) return false;
        return tile.getPersistentDataContainer().has(CRATE_KEY, PersistentDataType.STRING);
    }

    public static boolean isParachuteEntity(Entity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(PARACHUTE_KEY, PersistentDataType.BOOLEAN);
    }

    public static boolean isCrateLoot(org.bukkit.entity.Item item) {
        if (item == null) return false;
        return item.getPersistentDataContainer().has(CRATE_LOOT_KEY, PersistentDataType.BOOLEAN);
    }
}
