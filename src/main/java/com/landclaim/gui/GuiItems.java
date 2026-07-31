package com.landclaim.gui;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.util.ArrayList;
import java.util.List;

public final class GuiItems {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private GuiItems() {
    }

    public static String color(String text) {
        return SECTION.serialize(AMPERSAND.deserialize(text));
    }

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(color(line));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack toggle(String flag, boolean on) {
        return toggle(flag, on, "");
    }

    public static ItemStack toggle(String flag, boolean on, String description) {
        String name = on ? "&a" + flag + ": ON" : "&7" + flag + ": OFF";
        String click = on ? "&7Click to toggle off" : "&7Click to toggle on";
        if (description.isEmpty()) {
            return item(on ? Material.LIME_DYE : Material.GRAY_DYE, name, click);
        }
        return item(on ? Material.LIME_DYE : Material.GRAY_DYE, name, "&7" + description, click);
    }

    public static ItemStack head(String playerName) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = Bukkit.createProfile(playerName);
            skullMeta.setOwnerProfile(profile);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    public static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static ItemStack back(String label) {
        return item(Material.ARROW, "&7Back to " + label);
    }

    public static ItemStack close() {
        return item(Material.BARRIER, "&cClose");
    }
}
