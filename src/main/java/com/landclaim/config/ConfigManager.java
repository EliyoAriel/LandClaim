package com.landclaim.config;

import com.landclaim.LandClaimPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final LandClaimPlugin plugin;
    private List<TierConfig> tiers;
    private int maxClaimsPerPlayer;
    private double refundOnDelete;
    private List<String> enabledWorlds;
    private String listFormat;
    private String infoFormat;
    private String greetingTitleFormat;
    private String farewellTitleFormat;
    private String titleSubtitleFormat;
    private String actionbarFormat;
    private boolean preventClaimNearSpawn;
    private boolean taxEnabled;
    private double taxPerTier;
    private int taxPeriodDays;
    private int gracePeriodDays;
    private boolean guiEnabled;
    private boolean openGuiOnBareClaim;
    private boolean bedrockInventoryDescriptionsInNames;
    private Map<String, Boolean> flagDefaults = new HashMap<>();

    public static final List<String> CLAIM_FLAGS = List.of(
            "pvp", "explosions", "mobs", "firespread", "public-use", "fluidflow", "public-build",
            "public-items", "teleport", "crops", "decay", "pistons", "gravity");
    public static final List<String> MEMBER_FLAGS = List.of(
            "build", "use", "redstone", "doors", "vehicles", "animals", "items", "pvp", "teleport");

    public ConfigManager(LandClaimPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        load();
    }

    public void load() {
        tiers = new ArrayList<>();
        ConfigurationSection tiersSection = plugin.getConfig().getConfigurationSection("tiers");
        if (tiersSection != null) {
            for (String key : tiersSection.getKeys(false)) {
                ConfigurationSection sec = tiersSection.getConfigurationSection(key);
                if (sec != null) {
                    tiers.add(new TierConfig(
                            Integer.parseInt(key),
                            sec.getInt("radius"),
                            sec.getDouble("cost")
                    ));
                }
            }
        }
        tiers.sort((a, b) -> Integer.compare(a.getTier(), b.getTier()));

        maxClaimsPerPlayer = plugin.getConfig().getInt("max-claims-per-player", 3);
        refundOnDelete = plugin.getConfig().getDouble("refund-on-delete", 0.0);
        enabledWorlds = plugin.getConfig().getStringList("enabled-worlds");
        preventClaimNearSpawn = plugin.getConfig().getBoolean("prevent-claim-near-spawn", true);
        listFormat = plugin.getConfig().getString("list-format", "&6{displayname} &7- &f({x}, {z}) &7Radius: &f{radius} &7Tier: &f{tier} &7[{status}]");
        infoFormat = plugin.getConfig().getString("info-format", "&6=== {displayname} ===\n&7Owner: &f{owner}\n&7Location: &f({x}, {z}) in {world}\n&7Radius: &f{radius}\n&7Tier: &f{tier}\n&7Status: &f{status}\n&7Members: &f{members}");
        greetingTitleFormat = plugin.getConfig().getString("greeting-title-format", "&aWelcome to &f{displayname}");
        farewellTitleFormat = plugin.getConfig().getString("farewell-title-format", "&eLeaving &f{displayname}");
        titleSubtitleFormat = plugin.getConfig().getString("title-subtitle-format", "&7Owned by &f{owner}");
        actionbarFormat = plugin.getConfig().getString("actionbar-format", "&f{owner} &7owns &f{displayname}");
        taxEnabled = plugin.getConfig().getBoolean("tax.enabled", false);
        taxPerTier = plugin.getConfig().getDouble("tax.amount-per-tier", 50.0);
        taxPeriodDays = plugin.getConfig().getInt("tax.period-days", 7);
        gracePeriodDays = plugin.getConfig().getInt("tax.grace-period-days", 7);
        guiEnabled = plugin.getConfig().getBoolean("gui.enabled", true);
        openGuiOnBareClaim = plugin.getConfig().getBoolean("gui.open-on-bare-claim", true);
        bedrockInventoryDescriptionsInNames = plugin.getConfig().getBoolean("gui.bedrock.inventory-descriptions-in-names", true);

        flagDefaults.clear();
        ConfigurationSection flagsSection = plugin.getConfig().getConfigurationSection("flags");
        if (flagsSection != null) {
            for (String key : flagsSection.getKeys(false)) {
                flagDefaults.put(key, flagsSection.getBoolean(key));
            }
        }
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }

    public List<TierConfig> getTiers() { return tiers; }
    public int getMaxClaimsPerPlayer() { return maxClaimsPerPlayer; }
    public double getRefundOnDelete() { return refundOnDelete; }
    public List<String> getEnabledWorlds() { return enabledWorlds; }
    public boolean isWorldEnabled(String worldName) { return enabledWorlds.contains(worldName); }
    public boolean isPreventClaimNearSpawn() { return preventClaimNearSpawn; }
    public String getListFormat() { return listFormat; }
    public String getInfoFormat() { return infoFormat; }
    public String getGreetingTitleFormat() { return greetingTitleFormat; }
    public String getFarewellTitleFormat() { return farewellTitleFormat; }
    public String getTitleSubtitleFormat() { return titleSubtitleFormat; }
    public String getActionbarFormat() { return actionbarFormat; }
    public boolean isTaxEnabled() { return taxEnabled; }
    public double getTaxPerTier() { return taxPerTier; }
    public int getTaxPeriodDays() { return taxPeriodDays; }
    public int getGracePeriodDays() { return gracePeriodDays; }
    public boolean isGuiEnabled() { return guiEnabled; }
    public boolean isOpenGuiOnBareClaim() { return openGuiOnBareClaim; }
    public boolean isBedrockInventoryDescriptionsInNames() { return bedrockInventoryDescriptionsInNames; }

    public int getEffectiveMaxClaims(Player player) {
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("landclaim.claims." + i)) {
                return i;
            }
        }
        return maxClaimsPerPlayer;
    }

    public boolean getFlagDefault(String flag) {
        Boolean value = flagDefaults.get(flag);
        if (value != null) return value;
        return flag.equals("mobs");
    }
}
