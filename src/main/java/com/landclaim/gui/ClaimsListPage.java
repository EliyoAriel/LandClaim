package com.landclaim.gui;

import com.landclaim.LandClaimPlugin;
import com.landclaim.data.Claim;
import com.landclaim.service.ClaimService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.List;

public final class ClaimsListPage {

    private static final String TITLE = GuiItems.color("&3Land Claims");
    private static final int CONTENT_START = 0;
    private static final int CONTENT_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 47;
    private static final int SLOT_PAGE_LABEL = 49;
    private static final int SLOT_CLOSE = 53;

    private ClaimsListPage() {
    }

    public static Inventory render(GuiManager manager, Player player, GuiSession session) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        LandClaimPlugin plugin = manager.getPlugin();
        ClaimService service = plugin.getClaimService();
        List<Claim> claims = plugin.getClaimRepository().getPlayerClaims(player.getUniqueId());
        claims.sort(Comparator.comparingInt(Claim::getId));

        int page = session.getClaimsPage();
        int totalPages = Math.max(1, (int) Math.ceil(claims.size() / (double) CONTENT_SIZE));
        if (page >= totalPages) {
            page = totalPages - 1;
            session.setClaimsPage(page);
        }

        if (claims.isEmpty()) {
            inv.setItem(13, GuiItems.item(Material.PAPER, "&7No claims yet",
                    "&7You don't own any claims.",
                    "&7Use &e/claim create <name>&7 to claim land."));
        } else {
            int from = page * CONTENT_SIZE;
            int to = Math.min(from + CONTENT_SIZE, claims.size());
            for (int slot = 0; slot < CONTENT_SIZE; slot++) {
                int index = from + slot;
                if (index >= to) break;
                Claim claim = claims.get(index);
                inv.setItem(CONTENT_START + slot, claimItem(plugin, service, claim, service.isTaxEnabled() && service.isOverdue(claim)));
            }
        }

        if (page > 0) {
            inv.setItem(SLOT_PREV, GuiItems.item(Material.ARROW, "&ePrevious Page"));
        }
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, GuiItems.item(Material.ARROW, "&eNext Page"));
        }
        inv.setItem(SLOT_PAGE_LABEL, GuiItems.item(Material.PAPER, "&7Page " + (page + 1) + " / " + totalPages));
        inv.setItem(SLOT_CLOSE, GuiItems.close());

        for (int slot = 45; slot < 54; slot++) {
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, GuiItems.filler());
            }
        }
        return inv;
    }

    public static void onClick(GuiManager manager, Player player, GuiSession session, int slot) {
        LandClaimPlugin plugin = manager.getPlugin();
        List<Claim> claims = plugin.getClaimRepository().getPlayerClaims(player.getUniqueId());
        claims.sort(Comparator.comparingInt(Claim::getId));

        int page = session.getClaimsPage();
        int totalPages = Math.max(1, (int) Math.ceil(claims.size() / (double) CONTENT_SIZE));

        if (slot >= CONTENT_START && slot < CONTENT_START + CONTENT_SIZE) {
            int index = page * CONTENT_SIZE + (slot - CONTENT_START);
            if (index >= 0 && index < claims.size()) {
                manager.openClaimDetail(player, claims.get(index));
            }
            return;
        }
        if (slot == SLOT_PREV && page > 0) {
            manager.openPage(player, GuiPage.CLAIMS_LIST, page - 1);
            return;
        }
        if (slot == SLOT_NEXT && page < totalPages - 1) {
            manager.openPage(player, GuiPage.CLAIMS_LIST, page + 1);
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    private static ItemStack claimItem(LandClaimPlugin plugin, ClaimService service, Claim claim, boolean overdue) {
        String worldName = Bukkit.getWorld(claim.getWorld()) != null
                ? Bukkit.getWorld(claim.getWorld()).getName() : "unknown";
        String ownerName = service.resolveOwnerName(claim.getOwner());
        String status = claim.isActive() ? "Active" : "Inactive";
        String statusLine = overdue ? status + " &c(Tax overdue)" : status;

        return GuiItems.item(Material.GRASS_BLOCK,
                claim.getDisplayName(),
                "&7Owner: &f" + ownerName,
                "&7Location: &f(" + claim.getX() + ", " + claim.getZ() + ") &7in &f" + worldName,
                "&7Radius: &f" + claim.getRadius() + " &7Tier: &f" + claim.getTier(),
                "&7Status: &f" + statusLine,
                "&eClick to manage");
    }
}
