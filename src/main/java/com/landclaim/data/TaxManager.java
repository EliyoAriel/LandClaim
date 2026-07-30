package com.landclaim.data;

import com.landclaim.LandClaimPlugin;
import com.landclaim.config.ConfigManager;
import com.landclaim.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class TaxManager {

    private final LandClaimPlugin plugin;
    private final ClaimRepository claimRepository;
    private final ConfigManager configManager;
    private final EconomyManager economyManager;

    public TaxManager(LandClaimPlugin plugin, ClaimRepository claimRepository) {
        this.plugin = plugin;
        this.claimRepository = claimRepository;
        this.configManager = plugin.getConfigManager();
        this.economyManager = plugin.getEconomyManager();
    }

    public void scheduleTaxCheck() {
        if (!configManager.isTaxEnabled()) return;

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkOverdueClaims();
            deleteExpiredClaims();
        }, 20L * 60 * 30, 20L * 60 * 30);
    }

    private void checkOverdueClaims() {
        int periodDays = configManager.getTaxPeriodDays();
        long cutoff = Instant.now().minus(periodDays, ChronoUnit.DAYS).toEpochMilli();

        for (Claim claim : claimRepository.getAllClaims()) {
            if (!claim.isActive()) continue;
            if (!hasPaidRecentTax(claim, cutoff)) {
                claimRepository.setClaimActive(claim.getId(), false);
                plugin.getLogger().info("Claim " + claim.getId() + " deactivated (unpaid tax).");
            }
        }
    }

    private void deleteExpiredClaims() {
        int graceDays = configManager.getGracePeriodDays();
        long deleteCutoff = Instant.now().minus(graceDays, ChronoUnit.DAYS).toEpochMilli();

        List<Claim> toDelete = new ArrayList<>();
        for (Claim claim : claimRepository.getAllClaims()) {
            if (claim.isActive()) continue;
            if (claim.getCreatedAt() < deleteCutoff) {
                toDelete.add(claim);
            }
        }

        for (Claim claim : toDelete) {
            claimRepository.deleteClaim(claim.getId());
            plugin.getLogger().info("Claim " + claim.getId() + " permanently deleted (expired tax grace period).");
        }
    }

    private boolean hasPaidRecentTax(Claim claim, long cutoff) {
        String sql = "SELECT MAX(week_start) as last_paid FROM claim_taxes WHERE claim_id = ? AND paid = 1";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setInt(1, claim.getId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long lastPaid = rs.getLong("last_paid");
                return lastPaid >= cutoff;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void payTax(Player player) {
        List<Claim> claims = claimRepository.getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            player.sendMessage(Component.text("You have no claims.", NamedTextColor.YELLOW));
            return;
        }

        double total = 0;
        List<Claim> unpaid = new ArrayList<>();
        long periodMs = Instant.now().minus(configManager.getTaxPeriodDays(), ChronoUnit.DAYS).toEpochMilli();

        for (Claim claim : claims) {
            if (!hasPaidRecentTax(claim, periodMs)) {
                double tax = configManager.getTaxPerTier() * claim.getTier();
                total += tax;
                unpaid.add(claim);
            }
        }

        if (unpaid.isEmpty()) {
            player.sendMessage(Component.text("All your claims are up to date.", NamedTextColor.GREEN));
            return;
        }

        if (!economyManager.hasEconomy() || !economyManager.hasBalance(player, total)) {
            player.sendMessage(Component.text("You need " + economyManager.format(total) + " to pay all overdue taxes.", NamedTextColor.RED));
            return;
        }

        economyManager.withdraw(player, total);
        String weekStart = String.valueOf(Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli());

        for (Claim claim : unpaid) {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection()
                    .prepareStatement("INSERT OR REPLACE INTO claim_taxes (claim_id, week_start, paid) VALUES (?, ?, 1)")) {
                ps.setInt(1, claim.getId());
                ps.setString(2, weekStart);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            if (!claim.isActive()) {
                claimRepository.setClaimActive(claim.getId(), true);
            }
        }

        player.sendMessage(Component.text("Paid " + economyManager.format(total) + " in taxes. " + unpaid.size() + " claim(s) reactivated.", NamedTextColor.GREEN));
    }

    public List<Claim> getOverdueClaims() {
        List<Claim> overdue = new ArrayList<>();
        long periodMs = Instant.now().minus(configManager.getTaxPeriodDays(), ChronoUnit.DAYS).toEpochMilli();
        for (Claim claim : claimRepository.getAllClaims()) {
            if (claim.isActive() && !hasPaidRecentTax(claim, periodMs)) {
                overdue.add(claim);
            }
        }
        return overdue;
    }

    public double getTaxAmount(Claim claim) {
        return configManager.getTaxPerTier() * claim.getTier();
    }
}
