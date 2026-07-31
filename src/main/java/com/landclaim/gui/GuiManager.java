package com.landclaim.gui;

import com.landclaim.LandClaimPlugin;
import com.landclaim.data.Claim;
import com.landclaim.service.ClaimActionResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager implements Listener {

    private static final long INPUT_TIMEOUT_TICKS = 20L * 60;

    private final LandClaimPlugin plugin;
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();

    public GuiManager(LandClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public void openClaimGui(Player player) {
        cancelInput(player);
        openPage(player, GuiPage.CLAIMS_LIST, 0);
    }

    public void openPage(Player player, GuiPage page) {
        openPage(player, page, 0);
    }

    public void openPage(Player player, GuiPage page, int pageIndex) {
        GuiSession session = sessions.computeIfAbsent(player.getUniqueId(), GuiSession::new);
        if (page != GuiPage.CLAIM_DETAIL || session.getCurrentPage() != GuiPage.CLAIM_DETAIL) {
            session.setPendingConfirm(null);
        }
        session.setCurrentPage(page);
        if (page == GuiPage.CLAIMS_LIST) {
            session.setClaimsPage(pageIndex);
        } else if (page == GuiPage.MEMBERS) {
            session.setMembersPage(pageIndex);
        }

        Inventory inv = switch (page) {
            case CLAIMS_LIST -> ClaimsListPage.render(this, player, session);
            case CLAIM_DETAIL -> ClaimDetailPage.render(this, player, session);
            case FLAGS -> FlagsPage.render(this, player, session);
            case MEMBERS -> MembersPage.render(this, player, session);
            case MEMBER_PERMS -> MemberPermsPage.render(this, player, session);
        };
        if (inv == null) {
            return;
        }
        session.setInventory(inv);
        player.openInventory(inv);
    }

    public void openClaimDetail(Player player, Claim claim) {
        GuiSession session = sessions.computeIfAbsent(player.getUniqueId(), GuiSession::new);
        session.setSelectedClaim(claim);
        openPage(player, GuiPage.CLAIM_DETAIL);
    }

    public void openMemberPerms(Player player, Claim claim, UUID memberUuid) {
        GuiSession session = sessions.computeIfAbsent(player.getUniqueId(), GuiSession::new);
        session.setSelectedClaim(claim);
        session.setSelectedMember(memberUuid);
        openPage(player, GuiPage.MEMBER_PERMS);
    }

    public void promptInput(Player player, GuiSession.InputType type, Claim claim) {
        GuiSession session = sessions.computeIfAbsent(player.getUniqueId(), GuiSession::new);
        GuiPage returnPage = switch (type) {
            case TRUST_PLAYER -> GuiPage.MEMBERS;
            case RENAME, DISPLAYNAME -> GuiPage.CLAIM_DETAIL;
        };
        GuiSession.PendingInput input = new GuiSession.PendingInput(type, claim.getId(), returnPage);
        session.setPendingInput(input);
        session.setPendingConfirm(null);

        String prompt = switch (type) {
            case TRUST_PLAYER -> "Type the player name to trust, or \"cancel\".";
            case RENAME -> "Type a new claim name, or \"cancel\".";
            case DISPLAYNAME -> "Type a display name, or \"cancel\".";
        };
        player.sendMessage(Component.text(prompt, NamedTextColor.GOLD));
        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            GuiSession current = sessions.get(player.getUniqueId());
            if (current != null && current.getPendingInput() == input) {
                current.setPendingInput(null);
                if (player.isOnline()) {
                    player.sendMessage(Component.text("Input timed out.", NamedTextColor.RED));
                    openPage(player, returnPage);
                }
            }
        }, INPUT_TIMEOUT_TICKS);
    }

    public void handleInput(Player player, String message) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null || session.getPendingInput() == null) return;

        GuiSession.PendingInput input = session.getPendingInput();
        String msg = message.trim();

        if (msg.equalsIgnoreCase("cancel")) {
            session.setPendingInput(null);
            player.sendMessage(Component.text("Cancelled.", NamedTextColor.YELLOW));
            openPage(player, input.returnPage());
            return;
        }

        Claim claim = plugin.getClaimRepository().getClaimById(input.claimId());
        if (claim == null) {
            session.setPendingInput(null);
            player.sendMessage(Component.text("That claim no longer exists.", NamedTextColor.RED));
            openPage(player, GuiPage.CLAIMS_LIST);
            return;
        }

        switch (input.type()) {
            case TRUST_PLAYER -> {
                Player target = Bukkit.getPlayerExact(msg);
                if (target == null) {
                    player.sendMessage(Component.text("Player not found. Type a valid name or \"cancel\".", NamedTextColor.RED));
                    return;
                }
                session.setPendingInput(null);
                sendResult(player, plugin.getClaimService().trust(player, claim, target));
                openPage(player, input.returnPage());
            }
            case RENAME -> {
                session.setPendingInput(null);
                sendResult(player, plugin.getClaimService().rename(player, claim, msg.toLowerCase()));
                openPage(player, input.returnPage());
            }
            case DISPLAYNAME -> {
                session.setPendingInput(null);
                sendResult(player, plugin.getClaimService().setDisplayName(player, claim, msg));
                openPage(player, input.returnPage());
            }
        }
    }

    public void sendResult(Player player, ClaimActionResult result) {
        for (Component message : result.messages()) {
            if (!message.equals(Component.empty())) {
                player.sendMessage(message);
            }
        }
    }

    public void cancelInput(Player player) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.setPendingInput(null);
        }
    }

    public GuiSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public LandClaimPlugin getPlugin() {
        return plugin;
    }

    public void shutdown() {
        for (GuiSession session : sessions.values()) {
            Inventory inv = session.getInventory();
            if (inv != null && !inv.getViewers().isEmpty()) {
                for (org.bukkit.entity.HumanEntity viewer : inv.getViewers()) {
                    viewer.closeInventory();
                }
            }
        }
        sessions.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null || session.getInventory() == null) return;
        if (!event.getView().getTopInventory().equals(session.getInventory())) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(session.getInventory())) {
            return;
        }

        switch (session.getCurrentPage()) {
            case CLAIMS_LIST -> ClaimsListPage.onClick(this, player, session, event.getSlot());
            case CLAIM_DETAIL -> ClaimDetailPage.onClick(this, player, session, event.getSlot());
            case FLAGS -> FlagsPage.onClick(this, player, session, event.getSlot());
            case MEMBERS -> MembersPage.onClick(this, player, session, event.getSlot());
            case MEMBER_PERMS -> MemberPermsPage.onClick(this, player, session, event.getSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null || session.getInventory() == null) return;
        if (!event.getView().getTopInventory().equals(session.getInventory())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null || session.getInventory() == null) return;
        if (!event.getInventory().equals(session.getInventory())) return;
        session.setInventory(null);
        session.setPendingConfirm(null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }
}
