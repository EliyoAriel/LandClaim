package com.landclaim.service;

import com.landclaim.LandClaimPlugin;
import com.landclaim.config.ConfigManager;
import com.landclaim.config.TierConfig;
import com.landclaim.data.Claim;
import com.landclaim.data.ClaimRepository;
import com.landclaim.data.TaxManager;
import com.landclaim.economy.EconomyManager;
import com.landclaim.protection.ClaimAccess;
import com.landclaim.util.ClaimFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class ClaimService {

    private final LandClaimPlugin plugin;
    private final ClaimRepository claimRepository;
    private final ConfigManager configManager;
    private final EconomyManager economyManager;
    private final TaxManager taxManager;
    private final ClaimAccess claimAccess;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public ClaimService(LandClaimPlugin plugin, ClaimRepository claimRepository, ConfigManager configManager,
                        EconomyManager economyManager, TaxManager taxManager, ClaimAccess claimAccess) {
        this.plugin = plugin;
        this.claimRepository = claimRepository;
        this.configManager = configManager;
        this.economyManager = economyManager;
        this.taxManager = taxManager;
        this.claimAccess = claimAccess;
    }

    public int getMaxTier(Player player) {
        int max = 0;
        for (TierConfig tier : configManager.getTiers()) {
            if (player.hasPermission("landclaim.tier." + tier.getTier())) {
                max = tier.getTier();
            }
        }
        return max;
    }

    public TierConfig getNextTier(Player player, Claim claim) {
        int next = claim.getTier() + 1;
        if (next > getMaxTier(player)) return null;
        return configManager.getTiers().stream()
                .filter(t -> t.getTier() == next)
                .findFirst().orElse(null);
    }

    public ClaimActionResult trust(Player player, Claim claim, Player target) {
        if (!player.hasPermission("landclaim.trust")) {
            return ClaimActionResult.fail(Component.text("You don't have permission.", NamedTextColor.RED));
        }
        if (!claim.getOwner().equals(player.getUniqueId())) {
            return ClaimActionResult.fail(Component.text("You don't own this claim.", NamedTextColor.RED));
        }
        if (target.equals(player)) {
            return ClaimActionResult.fail(Component.text("You're already the owner.", NamedTextColor.RED));
        }
        if (claim.getMembers().contains(target.getUniqueId())) {
            return ClaimActionResult.fail(Component.text(target.getName() + " is already trusted.", NamedTextColor.YELLOW));
        }
        claimRepository.addMember(claim.getId(), target.getUniqueId());
        target.sendMessage(Component.text("You've been trusted in " + player.getName() + "'s claim \"", NamedTextColor.GREEN)
                .append(renderDisplayName(claim))
                .append(Component.text("\".", NamedTextColor.GREEN)));
        return ClaimActionResult.ok(Component.text(target.getName() + " is now trusted.", NamedTextColor.GREEN));
    }

    public ClaimActionResult untrust(Player player, Claim claim, UUID targetUuid) {
        if (!player.hasPermission("landclaim.untrust")) {
            return ClaimActionResult.fail(Component.text("You don't have permission.", NamedTextColor.RED));
        }
        if (!claim.getOwner().equals(player.getUniqueId())) {
            return ClaimActionResult.fail(Component.text("You don't own this claim.", NamedTextColor.RED));
        }
        if (!claim.getMembers().contains(targetUuid)) {
            return ClaimActionResult.fail(Component.text(resolveOwnerName(targetUuid) + " is not trusted in this claim.", NamedTextColor.YELLOW));
        }
        claimRepository.removeMember(claim.getId(), targetUuid);
        claimRepository.deleteMemberFlags(claim.getId(), targetUuid);
        return ClaimActionResult.ok(Component.text(resolveOwnerName(targetUuid) + " has been untrusted.", NamedTextColor.GREEN));
    }

    public ClaimActionResult upgrade(Player player, Claim claim) {
        if (!player.hasPermission("landclaim.upgrade")) {
            return ClaimActionResult.fail(Component.text("You don't have permission.", NamedTextColor.RED));
        }
        if (!claim.getOwner().equals(player.getUniqueId())) {
            return ClaimActionResult.fail(Component.text("You don't own this claim.", NamedTextColor.RED));
        }

        int currentTier = claim.getTier();
        int nextTierNum = currentTier + 1;
        int maxTier = getMaxTier(player);

        if (nextTierNum > maxTier) {
            return ClaimActionResult.fail(Component.text("You've reached your maximum allowed tier.", NamedTextColor.RED));
        }

        TierConfig nextTier = configManager.getTiers().stream()
                .filter(t -> t.getTier() == nextTierNum)
                .findFirst().orElse(null);
        if (nextTier == null) {
            return ClaimActionResult.fail(Component.text("No higher tiers available.", NamedTextColor.RED));
        }

        if (claimRepository.overlapsAny(claim.getWorld(), claim.getX(), claim.getZ(), nextTier.getRadius(), claim.getId())) {
            return ClaimActionResult.fail(Component.text("Upgrade would overlap another claim.", NamedTextColor.RED));
        }

        if (economyManager.hasEconomy() && !economyManager.hasBalance(player, nextTier.getCost())) {
            return ClaimActionResult.fail(Component.text("You need " + economyManager.format(nextTier.getCost()) + " to upgrade.", NamedTextColor.RED));
        }

        economyManager.withdraw(player, nextTier.getCost());
        com.landclaim.integration.RewindHook.syncClaim(claim, claim.getRadius(), nextTier.getRadius());
        claimRepository.upgradeClaim(claim.getId(), nextTier.getRadius(), nextTier.getTier());
        return ClaimActionResult.ok(Component.text("Claim \"", NamedTextColor.GREEN)
                .append(renderDisplayName(claim))
                .append(Component.text("\" upgraded to tier " + nextTierNum + "! Radius: " + nextTier.getRadius(), NamedTextColor.GREEN)));
    }

    public ClaimActionResult payTax(Player player) {
        if (!player.hasPermission("landclaim.paytax")) {
            return ClaimActionResult.fail(Component.text("You don't have permission.", NamedTextColor.RED));
        }
        if (!configManager.isTaxEnabled()) {
            return ClaimActionResult.fail(Component.text("Tax system is disabled.", NamedTextColor.YELLOW));
        }
        taxManager.payTax(player);
        return ClaimActionResult.ok(Component.empty());
    }

    public ClaimActionResult setFlag(Player player, Claim claim, String flag, boolean value) {
        if (!player.hasPermission("landclaim.manage")) {
            return ClaimActionResult.fail(Component.text("You don't have permission.", NamedTextColor.RED));
        }
        if (!claim.getOwner().equals(player.getUniqueId())) {
            return ClaimActionResult.fail(Component.text("You don't own this claim.", NamedTextColor.RED));
        }
        if (!ConfigManager.CLAIM_FLAGS.contains(flag)) {
            return ClaimActionResult.fail(Component.text("Unknown flag \"" + flag + "\". Valid flags: " + String.join(", ", ConfigManager.CLAIM_FLAGS), NamedTextColor.RED));
        }
        claimRepository.setClaimFlag(claim.getId(), flag, value);
        return ClaimActionResult.ok(Component.text("Flag \"" + flag + "\" is now " + (value ? "on" : "off") + " for \"", NamedTextColor.GREEN)
                .append(renderDisplayName(claim))
                .append(Component.text("\".", NamedTextColor.GREEN)));
    }

    public ClaimActionResult setMemberFlag(Player player, Claim claim, UUID targetUuid, String flag, boolean value) {
        if (!player.hasPermission("landclaim.manage")) {
            return ClaimActionResult.fail(Component.text("You don't have permission.", NamedTextColor.RED));
        }
        if (!claim.getOwner().equals(player.getUniqueId())) {
            return ClaimActionResult.fail(Component.text("You don't own this claim.", NamedTextColor.RED));
        }
        if (!claim.getMembers().contains(targetUuid)) {
            return ClaimActionResult.fail(Component.text(resolveOwnerName(targetUuid) + " is not trusted in this claim.", NamedTextColor.RED));
        }
        if (!ConfigManager.MEMBER_FLAGS.contains(flag)) {
            return ClaimActionResult.fail(Component.text("Unknown flag \"" + flag + "\". Valid flags: " + String.join(", ", ConfigManager.MEMBER_FLAGS), NamedTextColor.RED));
        }
        claimRepository.setMemberFlag(claim.getId(), targetUuid, flag, value);
        return ClaimActionResult.ok(Component.text(resolveOwnerName(targetUuid) + "'s \"" + flag + "\" is now " + (value ? "on" : "off") + " in \"", NamedTextColor.GREEN)
                .append(renderDisplayName(claim))
                .append(Component.text("\".", NamedTextColor.GREEN)));
    }

    public ClaimActionResult rename(Player player, Claim claim, String newName) {
        if (!player.hasPermission("landclaim.manage")) {
            return ClaimActionResult.fail(Component.text("You don't have permission.", NamedTextColor.RED));
        }
        if (!claim.getOwner().equals(player.getUniqueId())) {
            return ClaimActionResult.fail(Component.text("You don't own this claim.", NamedTextColor.RED));
        }
        if (!newName.matches("[a-z0-9_-]+")) {
            return ClaimActionResult.fail(Component.text("Claim name can only contain letters, numbers, underscores, and hyphens.", NamedTextColor.RED));
        }
        Claim other = claimRepository.getPlayerClaimByName(player.getUniqueId(), newName);
        if (other != null && other.getId() != claim.getId()) {
            return ClaimActionResult.fail(Component.text("You already have a claim with that name.", NamedTextColor.RED));
        }
        if (newName.equalsIgnoreCase(claim.getName())) {
            return ClaimActionResult.fail(Component.text("That's already the claim's name.", NamedTextColor.YELLOW));
        }
        claimRepository.renameClaim(claim.getId(), newName);
        return ClaimActionResult.ok(Component.text("Claim renamed to \"" + newName + "\".", NamedTextColor.GREEN));
    }

    public ClaimActionResult setDisplayName(Player player, Claim claim, String text) {
        if (!player.hasPermission("landclaim.manage")) {
            return ClaimActionResult.fail(Component.text("You don't have permission.", NamedTextColor.RED));
        }
        if (!claim.getOwner().equals(player.getUniqueId())) {
            return ClaimActionResult.fail(Component.text("You don't own this claim.", NamedTextColor.RED));
        }
        String cleaned = text.trim()
                .replaceAll("[\\r\\n\\u0000-\\u001f\\u007f-\\u009f]", "");
        if (cleaned.isEmpty()) {
            return ClaimActionResult.fail(Component.text("Usage: /claim displayname <claim> <text...> (use - to clear)", NamedTextColor.RED));
        }
        if (cleaned.equals("-") || cleaned.equalsIgnoreCase("reset")) {
            claimRepository.setDisplayName(claim.getId(), null);
            return ClaimActionResult.ok(Component.text("Display name for \"" + claim.getName() + "\" cleared.", NamedTextColor.GREEN));
        }
        if (cleaned.length() > 48) {
            return ClaimActionResult.fail(Component.text("Display name is too long (max 48 characters).", NamedTextColor.RED));
        }
        claimRepository.setDisplayName(claim.getId(), cleaned);
        return ClaimActionResult.ok(Component.text("Display name for \"" + claim.getName() + "\" set to \"", NamedTextColor.GREEN)
                .append(legacy.deserialize(cleaned))
                .append(Component.text("\".", NamedTextColor.GREEN)));
    }

    public ClaimActionResult delete(Player player, Claim claim) {
        if (!player.hasPermission("landclaim.delete")) {
            return ClaimActionResult.fail(Component.text("You don't have permission.", NamedTextColor.RED));
        }
        if (!claim.getOwner().equals(player.getUniqueId())) {
            return ClaimActionResult.fail(Component.text("You don't own this claim.", NamedTextColor.RED));
        }

        double refundRate = configManager.getRefundOnDelete();
        Component refundMessage = Component.empty();
        if (refundRate > 0 && economyManager.hasEconomy()) {
            double totalSpent = 0;
            for (TierConfig tier : configManager.getTiers()) {
                if (tier.getTier() <= claim.getTier()) {
                    totalSpent += tier.getCost();
                }
            }
            double refund = totalSpent * refundRate;
            economyManager.deposit(player, refund);
            refundMessage = Component.text("Refunded: " + economyManager.format(refund), NamedTextColor.GREEN);
        }

        com.landclaim.integration.RewindHook.unexcludeClaim(claim);
        claimRepository.deleteClaim(claim.getId());
        Component deleted = Component.text("Claim \"", NamedTextColor.GREEN)
                .append(renderDisplayName(claim))
                .append(Component.text("\" deleted.", NamedTextColor.GREEN));
        if (refundMessage.equals(Component.empty())) {
            return ClaimActionResult.ok(deleted);
        }
        return ClaimActionResult.ok(refundMessage, deleted);
    }

    public boolean isOverdue(Claim claim) {
        return taxManager.isOverdue(claim);
    }

    public double getTotalOverdue(Player player) {
        return taxManager.getTotalOverdue(player);
    }

    public boolean isTaxEnabled() {
        return configManager.isTaxEnabled();
    }

    public String resolveOwnerName(UUID uuid) {
        return claimAccess.resolveOwnerName(uuid);
    }

    public LandClaimPlugin getPlugin() {
        return plugin;
    }

    private Component renderDisplayName(Claim claim) {
        return legacy.deserialize(ClaimFormat.resolvedDisplayName(claim, claimRepository, this::resolveOwnerName));
    }
}
