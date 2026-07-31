package com.landclaim.gui;

import com.landclaim.data.Claim;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class GuiSession {

    public enum InputType {
        TRUST_PLAYER,
        RENAME,
        DISPLAYNAME
    }

    public record PendingInput(InputType type, int claimId, GuiPage returnPage) {
    }

    public record PendingConfirm(int claimId, long time) {

        public boolean expired() {
            return System.currentTimeMillis() - time > 30_000;
        }
    }

    private final UUID playerId;
    private GuiPage currentPage = GuiPage.CLAIMS_LIST;
    private int claimsPage = 0;
    private int membersPage = 0;
    private Claim selectedClaim;
    private UUID selectedMember;
    private PendingInput pendingInput;
    private PendingConfirm pendingConfirm;
    private Inventory inventory;

    public GuiSession(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public GuiPage getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(GuiPage currentPage) {
        this.currentPage = currentPage;
    }

    public int getClaimsPage() {
        return claimsPage;
    }

    public void setClaimsPage(int claimsPage) {
        this.claimsPage = claimsPage;
    }

    public int getMembersPage() {
        return membersPage;
    }

    public void setMembersPage(int membersPage) {
        this.membersPage = membersPage;
    }

    public Claim getSelectedClaim() {
        return selectedClaim;
    }

    public void setSelectedClaim(Claim selectedClaim) {
        this.selectedClaim = selectedClaim;
    }

    public UUID getSelectedMember() {
        return selectedMember;
    }

    public void setSelectedMember(UUID selectedMember) {
        this.selectedMember = selectedMember;
    }

    public PendingInput getPendingInput() {
        return pendingInput;
    }

    public void setPendingInput(PendingInput pendingInput) {
        this.pendingInput = pendingInput;
    }

    public PendingConfirm getPendingConfirm() {
        return pendingConfirm;
    }

    public void setPendingConfirm(PendingConfirm pendingConfirm) {
        this.pendingConfirm = pendingConfirm;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
