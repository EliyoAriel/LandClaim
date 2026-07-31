package com.landclaim.gui;

import com.landclaim.LandClaimPlugin;
import com.landclaim.config.ConfigManager;
import com.landclaim.data.Claim;
import com.landclaim.data.ClaimRepository;
import com.landclaim.service.ClaimActionResult;
import com.landclaim.service.ClaimService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.UUID;

public final class MemberPermsPage {

    private static final int SLOT_BACK = 18;
    private static final int SLOT_UNTRUST = 22;

    private MemberPermsPage() {
    }

    public static Inventory render(GuiManager manager, Player player, GuiSession session) {
        LandClaimPlugin plugin = manager.getPlugin();
        Claim claim = session.getSelectedClaim();
        UUID memberUuid = session.getSelectedMember();
        if (claim == null || plugin.getClaimRepository().getClaimById(claim.getId()) == null) {
            manager.openPage(player, GuiPage.CLAIMS_LIST);
            return null;
        }
        if (memberUuid == null || !claim.getMembers().contains(memberUuid)) {
            manager.openPage(player, GuiPage.MEMBERS);
            return null;
        }

        ClaimService service = plugin.getClaimService();
        String memberName = service.resolveOwnerName(memberUuid);
        Inventory inv = Bukkit.createInventory(null, 27, GuiItems.color("&3Perms: " + memberName));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, GuiItems.filler());
        }

        ClaimRepository repository = plugin.getClaimRepository();
        List<String> flags = ConfigManager.MEMBER_FLAGS;
        for (int i = 0; i < flags.size(); i++) {
            String flag = flags.get(i);
            inv.setItem(i, GuiItems.toggle(flag, repository.getMemberFlag(claim.getId(), memberUuid, flag),
                    FlagDescriptions.MEMBER.getOrDefault(flag, "")));
        }
        inv.setItem(SLOT_BACK, GuiItems.back("Members"));
        inv.setItem(SLOT_UNTRUST, GuiItems.item(Material.BARRIER, "&cUntrust " + memberName,
                "&7Removes " + memberName + " from this claim."));
        return inv;
    }

    public static void onClick(GuiManager manager, Player player, GuiSession session, int slot) {
        if (slot == SLOT_BACK) {
            manager.openPage(player, GuiPage.MEMBERS);
            return;
        }

        Claim claim = session.getSelectedClaim();
        UUID memberUuid = session.getSelectedMember();
        if (claim == null || memberUuid == null) {
            manager.openPage(player, GuiPage.CLAIMS_LIST);
            return;
        }

        if (slot == SLOT_UNTRUST) {
            ClaimActionResult result = manager.getPlugin().getClaimService().untrust(player, claim, memberUuid);
            manager.sendResult(player, result);
            session.setSelectedMember(null);
            manager.openPage(player, GuiPage.MEMBERS);
            return;
        }

        if (slot < 0 || slot >= ConfigManager.MEMBER_FLAGS.size()) {
            return;
        }

        if (!claim.getMembers().contains(memberUuid)) {
            manager.openPage(player, GuiPage.MEMBERS);
            return;
        }

        String flag = ConfigManager.MEMBER_FLAGS.get(slot);
        boolean current = manager.getPlugin().getClaimRepository().getMemberFlag(claim.getId(), memberUuid, flag);
        ClaimActionResult result = manager.getPlugin().getClaimService().setMemberFlag(player, claim, memberUuid, flag, !current);
        manager.sendResult(player, result);
        manager.openPage(player, GuiPage.MEMBER_PERMS);
    }
}
