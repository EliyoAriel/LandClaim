package com.landclaim.gui;

import java.util.Map;

public final class FlagDescriptions {

    public static final Map<String, String> CLAIM = Map.ofEntries(
            Map.entry("pvp", "Allows PvP combat inside the claim"),
            Map.entry("explosions", "Allows explosions to destroy blocks"),
            Map.entry("mobs", "Allows mobs to spawn and attack"),
            Map.entry("firespread", "Allows fire to spread and burn blocks"),
            Map.entry("public-use", "Allows non-members to use blocks (chests, doors, redstone, vehicles, animals)"),
            Map.entry("fluidflow", "Allows water and lava to flow in"),
            Map.entry("public-build", "Allows non-members to build and break blocks"),
            Map.entry("public-items", "Allows non-members to pick up and drop items"),
            Map.entry("teleport", "Allows teleporting into the claim"),
            Map.entry("crops", "Allows farmland to be trampled"),
            Map.entry("decay", "Allows blocks to decay"),
            Map.entry("pistons", "Allows pistons to move blocks"),
            Map.entry("gravity", "Allows falling blocks to land")
    );

    public static final Map<String, String> MEMBER = Map.ofEntries(
            Map.entry("build", "Can place and break blocks"),
            Map.entry("use", "Can open containers and interact"),
            Map.entry("redstone", "Can use redstone and levers"),
            Map.entry("doors", "Can open doors, gates, and beds"),
            Map.entry("vehicles", "Can use boats and minecarts"),
            Map.entry("animals", "Can interact with and attack animals"),
            Map.entry("items", "Can pick up and drop items"),
            Map.entry("pvp", "Can engage in PvP here"),
            Map.entry("teleport", "Can teleport into the claim")
    );

    private FlagDescriptions() {
    }
}
