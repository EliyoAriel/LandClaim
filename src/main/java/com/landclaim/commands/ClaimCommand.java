package com.landclaim.commands;

import com.landclaim.LandClaimPlugin;
import com.landclaim.config.ConfigManager;
import com.landclaim.config.TierConfig;
import com.landclaim.data.Claim;
import com.landclaim.data.ClaimRepository;
import com.landclaim.data.TaxManager;
import com.landclaim.economy.EconomyManager;
import com.landclaim.util.ParticleUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ClaimCommand implements CommandExecutor, TabCompleter {

    private final LandClaimPlugin plugin;
    private final ClaimRepository claimRepository;
    private final EconomyManager economyManager;
    private final TaxManager taxManager;
    private final ConfigManager configManager;
    private final Map<UUID, PendingDelete> pendingDeletes = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 30_000;

    private static class PendingDelete {
        final int claimId;
        final long time;
        PendingDelete(int claimId) {
            this.claimId = claimId;
            this.time = System.currentTimeMillis();
        }
        boolean expired() {
            return System.currentTimeMillis() - time > CONFIRM_TIMEOUT_MS;
        }
    }

    public ClaimCommand(LandClaimPlugin plugin, ClaimRepository claimRepository, EconomyManager economyManager, TaxManager taxManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.claimRepository = claimRepository;
        this.economyManager = economyManager;
        this.taxManager = taxManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "create" -> onCreate(player, args);
            case "delete" -> onDelete(player, args);
            case "list" -> onList(player);
            case "info" -> onInfo(player, args);
            case "trust" -> onTrust(player, args);
            case "untrust" -> onUntrust(player, args);
            case "upgrade" -> onUpgrade(player, args);
            case "paytax" -> onPayTax(player);
            case "admin" -> {
                if (plugin.getClaimAdminCommand() != null) {
                    yield plugin.getClaimAdminCommand().execute(sender, args);
                }
                yield true;
            }
            default -> {
                sendHelp(player);
                yield true;
            }
        };
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== LandClaim Commands ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/claim create [name]", NamedTextColor.YELLOW).append(Component.text(" — Claim land at your location", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim delete <name> [confirm]", NamedTextColor.YELLOW).append(Component.text(" — Delete a claim", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim list", NamedTextColor.YELLOW).append(Component.text(" — List your claims", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim info [name]", NamedTextColor.YELLOW).append(Component.text(" — Show claim info", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim trust <player>", NamedTextColor.YELLOW).append(Component.text(" — Trust a player in your current claim", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim untrust <player>", NamedTextColor.YELLOW).append(Component.text(" — Untrust a player", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim upgrade <name>", NamedTextColor.YELLOW).append(Component.text(" — Upgrade claim tier", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim paytax", NamedTextColor.YELLOW).append(Component.text(" — Pay overdue taxes", NamedTextColor.WHITE)));
    }

    private boolean onCreate(Player player, String[] args) {
        if (!player.hasPermission("landclaim.create")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        Location loc = player.getLocation();

        if (!configManager.isWorldEnabled(loc.getWorld().getName())) {
            player.sendMessage(Component.text("Claiming is disabled in this world.", NamedTextColor.RED));
            return true;
        }

        int maxTier = getMaxTier(player);
        if (maxTier < 1) {
            player.sendMessage(Component.text("You don't have access to any claim tier.", NamedTextColor.RED));
            return true;
        }

        int count = claimRepository.getClaimCount(player.getUniqueId());
        if (count >= configManager.getEffectiveMaxClaims(player)) {
            player.sendMessage(Component.text("You've reached the maximum number of claims.", NamedTextColor.RED));
            return true;
        }

        Claim existing = claimRepository.getClaimAt(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockZ());
        if (existing != null) {
            player.sendMessage(Component.text("This area is already claimed.", NamedTextColor.RED));
            return true;
        }

        String name;
        if (args.length >= 2) {
            name = args[1].toLowerCase();
            if (!name.matches("[a-z0-9_-]+")) {
                player.sendMessage(Component.text("Claim name can only contain letters, numbers, underscores, and hyphens.", NamedTextColor.RED));
                return true;
            }
            if (claimRepository.getPlayerClaimByName(player.getUniqueId(), name) != null) {
                player.sendMessage(Component.text("You already have a claim with that name.", NamedTextColor.RED));
                return true;
            }
        } else {
            int num = 1;
            while (true) {
                name = "claim-" + num;
                if (claimRepository.getPlayerClaimByName(player.getUniqueId(), name) == null) break;
                num++;
            }
        }

        TierConfig tier1 = configManager.getTiers().getFirst();
        if (economyManager.hasEconomy() && !economyManager.hasBalance(player, tier1.getCost())) {
            player.sendMessage(Component.text("You need " + economyManager.format(tier1.getCost()) + " to create a claim.", NamedTextColor.RED));
            return true;
        }

        economyManager.withdraw(player, tier1.getCost());
        Claim claim = claimRepository.createClaim(
                player.getUniqueId(), name,
                loc.getWorld().getUID(),
                loc.getBlockX(), loc.getBlockZ(),
                tier1.getRadius(), tier1.getTier()
        );

        if (claim != null) {
            player.sendMessage(Component.text("Claim \"" + name + "\" created! Radius: " + tier1.getRadius(), NamedTextColor.GREEN));
            ParticleUtil.showClaimBoundary(player, claim);
        }
        return true;
    }

    private boolean onDelete(Player player, String[] args) {
        if (!player.hasPermission("landclaim.delete")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        pendingDeletes.values().removeIf(PendingDelete::expired);

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /claim delete <name> [confirm]", NamedTextColor.RED));
            return true;
        }

        String claimName = args[1];
        Claim claim = claimRepository.getPlayerClaimByName(player.getUniqueId(), claimName);
        if (claim == null) {
            player.sendMessage(Component.text("You don't have a claim named \"" + claimName + "\".", NamedTextColor.RED));
            return true;
        }

        boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
        if (!confirmed) {
            PendingDelete pending = pendingDeletes.get(player.getUniqueId());
            if (pending != null && pending.claimId == claim.getId() && !pending.expired()) {
                confirmed = true;
            }
        }

        if (!confirmed) {
            pendingDeletes.put(player.getUniqueId(), new PendingDelete(claim.getId()));
            player.sendMessage(Component.text("Are you sure you want to delete \"" + claimName + "\"? ", NamedTextColor.YELLOW)
                    .append(Component.text("/claim delete " + claimName + " confirm", NamedTextColor.GOLD)
                    .append(Component.text(" to confirm.", NamedTextColor.YELLOW))));
            return true;
        }

        pendingDeletes.remove(player.getUniqueId());

        double refundRate = configManager.getRefundOnDelete();
        if (refundRate > 0 && economyManager.hasEconomy()) {
            double totalSpent = 0;
            for (TierConfig tier : configManager.getTiers()) {
                if (tier.getTier() <= claim.getTier()) {
                    totalSpent += tier.getCost();
                }
            }
            double refund = totalSpent * refundRate;
            economyManager.deposit(player, refund);
            player.sendMessage(Component.text("Refunded: " + economyManager.format(refund), NamedTextColor.GREEN));
        }

        claimRepository.deleteClaim(claim.getId());
        player.sendMessage(Component.text("Claim \"" + claimName + "\" deleted.", NamedTextColor.GREEN));
        return true;
    }

    private boolean onList(Player player) {
        if (!player.hasPermission("landclaim.list")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        List<Claim> claims = claimRepository.getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            player.sendMessage(Component.text("You have no claims.", NamedTextColor.YELLOW));
            return true;
        }

        player.sendMessage(Component.text("=== Your Claims (" + claims.size() + ") ===", NamedTextColor.GOLD));
        String format = configManager.getListFormat();
        for (Claim c : claims) {
            player.sendMessage(formatClaimTemplate(format, c));
        }
        return true;
    }

    private boolean onInfo(Player player, String[] args) {
        if (!player.hasPermission("landclaim.info")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        Claim claim;
        if (args.length >= 2) {
            claim = claimRepository.getPlayerClaimByName(player.getUniqueId(), args[1]);
            if (claim == null) {
                player.sendMessage(Component.text("You don't have a claim named \"" + args[1] + "\".", NamedTextColor.RED));
                return true;
            }
        } else {
            Location loc = player.getLocation();
            claim = claimRepository.getClaimAt(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockZ());
            if (claim == null) {
                player.sendMessage(Component.text("No claim here.", NamedTextColor.YELLOW));
                return true;
            }
        }

        player.sendMessage(formatClaimTemplate(configManager.getInfoFormat(), claim));
        ParticleUtil.showClaimBoundary(player, claim);
        return true;
    }

    private boolean onTrust(Player player, String[] args) {
        if (!player.hasPermission("landclaim.trust")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /claim trust <player>", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(Component.text("You're already the owner.", NamedTextColor.RED));
            return true;
        }
        Location loc = player.getLocation();
        Claim claim = claimRepository.getClaimAt(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockZ());
        if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You don't own a claim here.", NamedTextColor.RED));
            return true;
        }
        if (claim.getMembers().contains(target.getUniqueId())) {
            player.sendMessage(Component.text(target.getName() + " is already trusted.", NamedTextColor.YELLOW));
            return true;
        }
        claimRepository.addMember(claim.getId(), target.getUniqueId());
        player.sendMessage(Component.text(target.getName() + " is now trusted.", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You've been trusted in " + player.getName() + "'s claim.", NamedTextColor.GREEN));
        return true;
    }

    private boolean onUntrust(Player player, String[] args) {
        if (!player.hasPermission("landclaim.untrust")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /claim untrust <player>", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        Location loc = player.getLocation();
        Claim claim = claimRepository.getClaimAt(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockZ());
        if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You don't own a claim here.", NamedTextColor.RED));
            return true;
        }
        claimRepository.removeMember(claim.getId(), target.getUniqueId());
        player.sendMessage(Component.text(target.getName() + " has been untrusted.", NamedTextColor.GREEN));
        return true;
    }

    private boolean onUpgrade(Player player, String[] args) {
        if (!player.hasPermission("landclaim.upgrade")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /claim upgrade <name>", NamedTextColor.RED));
            return true;
        }

        Claim claim = claimRepository.getPlayerClaimByName(player.getUniqueId(), args[1]);
        if (claim == null) {
            player.sendMessage(Component.text("You don't have a claim named \"" + args[1] + "\".", NamedTextColor.RED));
            return true;
        }

        int currentTier = claim.getTier();
        int nextTierNum = currentTier + 1;
        int maxTier = getMaxTier(player);

        if (nextTierNum > maxTier) {
            player.sendMessage(Component.text("You've reached your maximum allowed tier.", NamedTextColor.RED));
            return true;
        }

        TierConfig nextTier = configManager.getTiers().stream()
                .filter(t -> t.getTier() == nextTierNum)
                .findFirst().orElse(null);
        if (nextTier == null) {
            player.sendMessage(Component.text("No higher tiers available.", NamedTextColor.RED));
            return true;
        }

        if (economyManager.hasEconomy() && !economyManager.hasBalance(player, nextTier.getCost())) {
            player.sendMessage(Component.text("You need " + economyManager.format(nextTier.getCost()) + " to upgrade.", NamedTextColor.RED));
            return true;
        }

        economyManager.withdraw(player, nextTier.getCost());
        claimRepository.upgradeClaim(claim.getId(), nextTier.getRadius(), nextTier.getTier());
        player.sendMessage(Component.text("Claim \"" + args[1] + "\" upgraded to tier " + nextTierNum + "! Radius: " + nextTier.getRadius(), NamedTextColor.GREEN));
        ParticleUtil.showClaimBoundary(player, claim);
        return true;
    }

    private boolean onPayTax(Player player) {
        if (!player.hasPermission("landclaim.paytax")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (!configManager.isTaxEnabled()) {
            player.sendMessage(Component.text("Tax system is disabled.", NamedTextColor.YELLOW));
            return true;
        }
        taxManager.payTax(player);
        return true;
    }

    private int getMaxTier(Player player) {
        List<TierConfig> tiers = configManager.getTiers();
        int max = 0;
        for (TierConfig tier : tiers) {
            if (player.hasPermission("landclaim.tier." + tier.getTier())) {
                max = tier.getTier();
            }
        }
        return max;
    }

    private Component formatClaimTemplate(String template, Claim claim) {
        String ownerName = getOwnerName(claim.getOwner());
        String status = claim.isActive() ? "Active" : "Inactive";
        String worldName = Bukkit.getWorld(claim.getWorld()) != null
                ? Bukkit.getWorld(claim.getWorld()).getName() : "unknown";
        String membersStr = claim.getMembers().stream()
                .map(this::getOwnerName)
                .collect(Collectors.joining(", "));
        if (membersStr.isEmpty()) membersStr = "None";

        String formatted = template
                .replace("{name}", claim.getName())
                .replace("{owner}", ownerName)
                .replace("{x}", String.valueOf(claim.getX()))
                .replace("{z}", String.valueOf(claim.getZ()))
                .replace("{radius}", String.valueOf(claim.getRadius()))
                .replace("{tier}", String.valueOf(claim.getTier()))
                .replace("{status}", status)
                .replace("{world}", worldName)
                .replace("{id}", String.valueOf(claim.getId()))
                .replace("{members}", membersStr);

        return LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);
    }

    private String getOwnerName(UUID uuid) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(uuid);
        return owner.getName() != null ? owner.getName() : uuid.toString();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("create", "delete", "list", "info", "trust", "untrust", "upgrade", "paytax", "admin");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && sender instanceof Player player) {
            if (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("upgrade")) {
                List<Claim> claims = claimRepository.getPlayerClaims(player.getUniqueId());
                return claims.stream().map(Claim::getName).filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("delete")) {
            if ("confirm".startsWith(args[2].toLowerCase())) {
                return List.of("confirm");
            }
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            return null;
        }
        return List.of();
    }
}
