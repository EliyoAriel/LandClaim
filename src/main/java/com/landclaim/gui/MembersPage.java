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
import java.util.UUID;

public final class MembersPage {

    private static final int CONTENT_START = 0;
    private static final int CONTENT_SIZE = 36;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 47;
    private static final int SLOT_PAGE_LABEL = 49;
    private static final int SLOT_TRUST = 51;
    private static final int SLOT_BACK = 53;

    private MembersPage() {
    }

    public static Inventory render(GuiManager manager, Player player, GuiSession session) {
        LandClaimPlugin plugin = manager.getPlugin();
        Claim claim = session.getSelectedClaim();
        if (claim == null || plugin.getClaimRepository().getClaimById(claim.getId()) == null) {
            manager.openPage(player, GuiPage.CLAIMS_LIST);
            return null;
        }

        Inventory inv = Bukkit.createInventory(null, 54, GuiItems.color("&3Members: " + claim.getName()));
        ClaimService service = plugin.getClaimService();

        List<UUID> members = claim.getMembers().stream()
                .sorted(Comparator.comparing(service::resolveOwnerName))
                .toList();

        int page = session.getMembersPage();
        int totalPages = Math.max(1, (int) Math.ceil(members.size() / (double) CONTENT_SIZE));
        if (page >= totalPages) {
            page = totalPages - 1;
            session.setMembersPage(page);
        }

        if (members.isEmpty()) {
            inv.setItem(13, GuiItems.item(Material.PAPER, "&7No members",
                    "&7No players are trusted in this claim.",
                    "&7Use the Trust Player button below."));
        } else {
            int from = page * CONTENT_SIZE;
            int to = Math.min(from + CONTENT_SIZE, members.size());
            for (int slot = 0; slot < CONTENT_SIZE; slot++) {
                int index = from + slot;
                if (index >= to) break;
                UUID member = members.get(index);
                String memberName = service.resolveOwnerName(member);
                ItemStack head = GuiItems.head(memberName);
                org.bukkit.inventory.meta.ItemMeta itemMeta = head.getItemMeta();
                itemMeta.setDisplayName(GuiItems.color("&e" + memberName));
                itemMeta.setLore(List.of(GuiItems.color("&7Click to manage permissions")));
                head.setItemMeta(itemMeta);
                inv.setItem(CONTENT_START + slot, head);
            }
        }

        if (page > 0) {
            inv.setItem(SLOT_PREV, GuiItems.item(Material.ARROW, "&ePrevious Page"));
        }
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, GuiItems.item(Material.ARROW, "&eNext Page"));
        }
        inv.setItem(SLOT_PAGE_LABEL, GuiItems.item(Material.PAPER, "&7Page " + (page + 1) + " / " + totalPages));
        inv.setItem(SLOT_TRUST, GuiItems.item(Material.PLAYER_HEAD, "&aTrust Player",
                "&7Click, then type the player name in chat"));
        inv.setItem(SLOT_BACK, GuiItems.back("Claim"));

        for (int slot = 45; slot < 54; slot++) {
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, GuiItems.filler());
            }
        }
        return inv;
    }

    public static void onClick(GuiManager manager, Player player, GuiSession session, int slot) {
        LandClaimPlugin plugin = manager.getPlugin();
        Claim claim = session.getSelectedClaim();
        if (claim == null) {
            manager.openPage(player, GuiPage.CLAIMS_LIST);
            return;
        }

        ClaimService service = plugin.getClaimService();
        List<UUID> members = claim.getMembers().stream()
                .sorted(Comparator.comparing(service::resolveOwnerName))
                .toList();
        int page = session.getMembersPage();
        int totalPages = Math.max(1, (int) Math.ceil(members.size() / (double) CONTENT_SIZE));

        if (slot >= CONTENT_START && slot < CONTENT_START + CONTENT_SIZE) {
            int index = page * CONTENT_SIZE + (slot - CONTENT_START);
            if (index >= 0 && index < members.size()) {
                manager.openMemberPerms(player, claim, members.get(index));
            }
            return;
        }
        if (slot == SLOT_PREV && page > 0) {
            manager.openPage(player, GuiPage.MEMBERS, page - 1);
            return;
        }
        if (slot == SLOT_NEXT && page < totalPages - 1) {
            manager.openPage(player, GuiPage.MEMBERS, page + 1);
            return;
        }
        if (slot == SLOT_TRUST) {
            manager.promptInput(player, GuiSession.InputType.TRUST_PLAYER, claim);
            return;
        }
        if (slot == SLOT_BACK) {
            manager.openPage(player, GuiPage.CLAIM_DETAIL);
        }
    }
}
