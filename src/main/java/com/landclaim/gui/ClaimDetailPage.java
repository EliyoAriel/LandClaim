package com.landclaim.gui;

import com.landclaim.LandClaimPlugin;
import com.landclaim.config.ConfigManager;
import com.landclaim.config.TierConfig;
import com.landclaim.data.Claim;
import com.landclaim.data.ClaimRepository;
import com.landclaim.economy.EconomyManager;
import com.landclaim.service.ClaimActionResult;
import com.landclaim.service.ClaimService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ClaimDetailPage {

    private static final int SLOT_BACK = 0;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_UPGRADE = 10;
    private static final int SLOT_FLAGS = 12;
    private static final int SLOT_MEMBERS = 14;
    private static final int SLOT_PAY_TAX = 16;
    private static final int SLOT_RENAME = 18;
    private static final int SLOT_DISPLAYNAME = 20;
    private static final int SLOT_BOUNDARY = 22;
    private static final int SLOT_DELETE = 24;

    private ClaimDetailPage() {
    }

    public static Inventory render(GuiManager manager, Player player, GuiSession session) {
        LandClaimPlugin plugin = manager.getPlugin();
        Claim claim = session.getSelectedClaim();
        if (claim == null || plugin.getClaimRepository().getClaimById(claim.getId()) == null) {
            manager.openPage(player, GuiPage.CLAIMS_LIST);
            return null;
        }

        Inventory inv = Bukkit.createInventory(null, 27, GuiItems.color("&3Claim: " + claim.getName()));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, GuiItems.filler());
        }

        ClaimService service = plugin.getClaimService();
        EconomyManager economy = plugin.getEconomyManager();
        ClaimRepository repository = plugin.getClaimRepository();

        inv.setItem(SLOT_BACK, GuiItems.back("Claims"));

        String worldName = Bukkit.getWorld(claim.getWorld()) != null
                ? Bukkit.getWorld(claim.getWorld()).getName() : "unknown";
        long flagsOn = countEnabledFlags(repository, claim);
        inv.setItem(SLOT_INFO, GuiItems.item(Material.BOOK,
                claim.getDisplayName(),
                "&7Owner: &f" + service.resolveOwnerName(claim.getOwner()),
                "&7Location: &f(" + claim.getX() + ", " + claim.getZ() + ") &7in &f" + worldName,
                "&7Radius: &f" + claim.getRadius(),
                "&7Tier: &f" + claim.getTier(),
                "&7Status: &f" + (claim.isActive() ? "Active" : "Inactive"),
                "&7Members: &f" + claim.getMembers().size(),
                "&7Flags: &f" + flagsOn + " / " + ConfigManager.CLAIM_FLAGS.size() + " on"));

        inv.setItem(SLOT_UPGRADE, upgradeItem(service, economy, player, claim));
        inv.setItem(SLOT_FLAGS, GuiItems.item(Material.BLUE_DYE, "&3Claim Flags",
                "&7Click to toggle claim-wide flags",
                "&7Currently " + flagsOn + " flags enabled"));
        inv.setItem(SLOT_MEMBERS, GuiItems.item(Material.SKELETON_SKULL, "&3Members",
                "&7Trusted players: &f" + claim.getMembers().size(),
                "&eClick to manage members"));
        inv.setItem(SLOT_PAY_TAX, payTaxItem(plugin, service, claim));
        inv.setItem(SLOT_RENAME, GuiItems.item(Material.NAME_TAG, "&eRename",
                "&7Type a new claim name in chat"));
        inv.setItem(SLOT_DISPLAYNAME, GuiItems.item(Material.OAK_SIGN, "&eDisplay Name",
                "&7Type a display name in chat"));
        boolean boundaryActive = plugin.getBoundaryManager().isActive(player, claim);
        inv.setItem(SLOT_BOUNDARY, boundaryActive
                ? GuiItems.item(Material.LIME_DYE, "&aBoundary Visible",
                        "&7Click to hide the boundary")
                : GuiItems.item(Material.REDSTONE_TORCH, "&dShow Boundary",
                        "&7Click to keep the boundary visible"));

        GuiSession.PendingConfirm pending = session.getPendingConfirm();
        boolean armed = pending != null && pending.claimId() == claim.getId() && !pending.expired();
        if (armed) {
            inv.setItem(SLOT_DELETE, GuiItems.item(Material.BARRIER, "&c&lClick again to confirm delete",
                    "&7Any other action cancels."));
        } else {
            inv.setItem(SLOT_DELETE, GuiItems.item(Material.BARRIER, "&cDelete Claim",
                    "&7Click once, then click again to confirm."));
        }
        return inv;
    }

    public static void onClick(GuiManager manager, Player player, GuiSession session, int slot) {
        if (slot != SLOT_DELETE) {
            session.setPendingConfirm(null);
        }

        Claim claim = session.getSelectedClaim();
        if (claim == null) {
            manager.openPage(player, GuiPage.CLAIMS_LIST);
            return;
        }
        ClaimService service = manager.getPlugin().getClaimService();

        switch (slot) {
            case SLOT_BACK -> manager.openPage(player, GuiPage.CLAIMS_LIST);
            case SLOT_UPGRADE -> {
                ClaimActionResult result = service.upgrade(player, claim);
                manager.sendResult(player, result);
                manager.openPage(player, GuiPage.CLAIM_DETAIL);
            }
            case SLOT_FLAGS -> manager.openPage(player, GuiPage.FLAGS);
            case SLOT_MEMBERS -> manager.openPage(player, GuiPage.MEMBERS);
            case SLOT_PAY_TAX -> {
                ClaimActionResult result = service.payTax(player);
                manager.sendResult(player, result);
                manager.openPage(player, GuiPage.CLAIM_DETAIL);
            }
            case SLOT_RENAME -> manager.promptInput(player, GuiSession.InputType.RENAME, claim);
            case SLOT_DISPLAYNAME -> manager.promptInput(player, GuiSession.InputType.DISPLAYNAME, claim);
            case SLOT_BOUNDARY -> {
                manager.getPlugin().getBoundaryManager().toggle(player, claim);
                manager.openPage(player, GuiPage.CLAIM_DETAIL);
            }
            case SLOT_DELETE -> {
                GuiSession.PendingConfirm pending = session.getPendingConfirm();
                if (pending != null && pending.claimId() == claim.getId() && !pending.expired()) {
                    ClaimActionResult result = service.delete(player, claim);
                    manager.sendResult(player, result);
                    if (result.success()) {
                        manager.openPage(player, GuiPage.CLAIMS_LIST);
                    } else {
                        session.setPendingConfirm(null);
                        manager.openPage(player, GuiPage.CLAIM_DETAIL);
                    }
                } else {
                    session.setPendingConfirm(new GuiSession.PendingConfirm(claim.getId(), System.currentTimeMillis()));
                    manager.openPage(player, GuiPage.CLAIM_DETAIL);
                }
            }
            default -> {
            }
        }
    }

    private static ItemStack upgradeItem(ClaimService service, EconomyManager economy, Player player, Claim claim) {
        TierConfig next = service.getNextTier(player, claim);
        if (next == null) {
            return GuiItems.item(Material.DIAMOND, "&7Upgrade",
                    "&7You are at your maximum tier.");
        }
        boolean affordable = !economy.hasEconomy() || economy.hasBalance(player, next.getCost());
        String costColor = affordable ? "&a" : "&c";
        return GuiItems.item(Material.DIAMOND, "&bUpgrade to Tier " + next.getTier(),
                "&7Current radius: &f" + claim.getRadius(),
                "&7Next radius: &f" + next.getRadius(),
                "&7Cost: " + costColor + economy.format(next.getCost()),
                "&eClick to upgrade");
    }

    private static ItemStack payTaxItem(LandClaimPlugin plugin, ClaimService service, Claim claim) {
        if (!service.isTaxEnabled()) {
            return GuiItems.item(Material.GOLD_INGOT, "&7Pay Tax",
                    "&7The tax system is disabled.");
        }
        if (service.isOverdue(claim)) {
            double amount = plugin.getTaxManager().getTaxAmount(claim);
            return GuiItems.item(Material.GOLD_INGOT, "&6Pay Overdue Taxes",
                    "&7This claim is overdue: &c" + plugin.getEconomyManager().format(amount),
                    "&eClick to pay all overdue taxes");
        }
        return GuiItems.item(Material.GOLD_INGOT, "&aTax Up to Date",
                "&7This claim has no overdue taxes.");
    }

    private static long countEnabledFlags(ClaimRepository repository, Claim claim) {
        return ConfigManager.CLAIM_FLAGS.stream()
                .filter(flag -> repository.getClaimFlag(claim.getId(), flag))
                .count();
    }
}
