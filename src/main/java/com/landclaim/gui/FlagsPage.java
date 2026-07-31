package com.landclaim.gui;

import com.landclaim.LandClaimPlugin;
import com.landclaim.bedrock.BedrockSupport;
import com.landclaim.config.ConfigManager;
import com.landclaim.data.Claim;
import com.landclaim.data.ClaimRepository;
import com.landclaim.service.ClaimActionResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

public final class FlagsPage {

    private static final int SLOT_BACK = 22;

    private FlagsPage() {
    }

    public static Inventory render(GuiManager manager, Player player, GuiSession session) {
        LandClaimPlugin plugin = manager.getPlugin();
        Claim claim = session.getSelectedClaim();
        if (claim == null || plugin.getClaimRepository().getClaimById(claim.getId()) == null) {
            manager.openPage(player, GuiPage.CLAIMS_LIST);
            return null;
        }

        Inventory inv = Bukkit.createInventory(null, 27, GuiItems.color("&3Flags: " + claim.getName()));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, GuiItems.filler());
        }

        ClaimRepository repository = plugin.getClaimRepository();
        List<String> flags = ConfigManager.CLAIM_FLAGS;
        boolean descriptionInName = plugin.getConfigManager().isBedrockInventoryDescriptionsInNames()
                && BedrockSupport.isFloodgatePlayer(player.getUniqueId());
        for (int i = 0; i < flags.size(); i++) {
            String flag = flags.get(i);
            inv.setItem(i, GuiItems.toggle(flag, repository.getClaimFlag(claim.getId(), flag),
                    FlagDescriptions.CLAIM.getOrDefault(flag, ""), descriptionInName));
        }
        inv.setItem(SLOT_BACK, GuiItems.back("Claim"));
        return inv;
    }

    public static void onClick(GuiManager manager, Player player, GuiSession session, int slot) {
        if (slot == SLOT_BACK) {
            manager.openPage(player, GuiPage.CLAIM_DETAIL);
            return;
        }
        if (slot < 0 || slot >= ConfigManager.CLAIM_FLAGS.size()) {
            return;
        }

        Claim claim = session.getSelectedClaim();
        if (claim == null) {
            manager.openPage(player, GuiPage.CLAIMS_LIST);
            return;
        }

        String flag = ConfigManager.CLAIM_FLAGS.get(slot);
        boolean current = manager.getPlugin().getClaimRepository().getClaimFlag(claim.getId(), flag);
        ClaimActionResult result = manager.getPlugin().getClaimService().setFlag(player, claim, flag, !current);
        manager.sendResult(player, result);
        manager.openPage(player, GuiPage.FLAGS);
    }
}
