package com.landclaim.commands;

import com.landclaim.LandClaimPlugin;
import com.landclaim.config.ConfigManager;
import com.landclaim.config.TierConfig;
import com.landclaim.data.Claim;
import com.landclaim.data.ClaimRepository;
import com.landclaim.data.TaxManager;
import com.landclaim.economy.EconomyManager;
import com.landclaim.protection.ClaimAccess;
import com.landclaim.util.ClaimFormat;
import com.landclaim.util.ParticleUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
    private final ClaimAccess claimAccess;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
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

    public ClaimCommand(LandClaimPlugin plugin, ClaimRepository claimRepository, EconomyManager economyManager, TaxManager taxManager, ConfigManager configManager, ClaimAccess claimAccess) {
        this.plugin = plugin;
        this.claimRepository = claimRepository;
        this.economyManager = economyManager;
        this.taxManager = taxManager;
        this.configManager = configManager;
        this.claimAccess = claimAccess;
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
            case "flag" -> onFlag(player, args);
            case "perm" -> onPerm(player, args);
            case "rename" -> onRename(player, args);
            case "displayname" -> onDisplayName(player, args);
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
        player.sendMessage(Component.text("/claim trust <claim> <player>", NamedTextColor.YELLOW).append(Component.text(" — Trust a player in a claim", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim untrust <claim> <player>", NamedTextColor.YELLOW).append(Component.text(" — Untrust a player", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim upgrade <name>", NamedTextColor.YELLOW).append(Component.text(" — Upgrade claim tier", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim paytax", NamedTextColor.YELLOW).append(Component.text(" — Pay overdue taxes", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim flag <claim> <flag> [on|off]", NamedTextColor.YELLOW).append(Component.text(" — Toggle a claim flag", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim perm <claim> <player> <flag> [on|off]", NamedTextColor.YELLOW).append(Component.text(" — Toggle a member's permission", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim rename <old> <new>", NamedTextColor.YELLOW).append(Component.text(" — Rename a claim", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/claim displayname <claim> <text...>", NamedTextColor.YELLOW).append(Component.text(" — Set a claim's display name (use - to clear)", NamedTextColor.WHITE)));
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

        TierConfig tier1 = configManager.getTiers().getFirst();
        if (claimRepository.overlapsAny(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockZ(), tier1.getRadius(), null)) {
            player.sendMessage(Component.text("This area is already claimed.", NamedTextColor.RED));
            return true;
        }

        if (configManager.isPreventClaimNearSpawn()) {
            Location spawn = loc.getWorld().getSpawnLocation();
            int spawnRadius = plugin.getServer().getSpawnRadius();
            if (spawnRadius > 0) {
                double dx = loc.getBlockX() - spawn.getBlockX();
                double dz = loc.getBlockZ() - spawn.getBlockZ();
                if (Math.sqrt(dx * dx + dz * dz) < spawnRadius + tier1.getRadius()) {
                    player.sendMessage(Component.text("You cannot claim this close to spawn.", NamedTextColor.RED));
                    return true;
                }
            }
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
            player.sendMessage(Component.text("Are you sure you want to delete \"", NamedTextColor.YELLOW)
                    .append(renderDisplayName(claim))
                    .append(Component.text("\"? ", NamedTextColor.YELLOW))
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
        player.sendMessage(Component.text("Claim \"", NamedTextColor.GREEN)
                .append(renderDisplayName(claim))
                .append(Component.text("\" deleted.", NamedTextColor.GREEN)));
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
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /claim trust <claim> <player>", NamedTextColor.RED));
            return true;
        }
        Claim claim = claimRepository.getPlayerClaimByName(player.getUniqueId(), args[1]);
        if (claim == null) {
            player.sendMessage(Component.text("You don't have a claim named \"" + args[1] + "\".", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(Component.text("You're already the owner.", NamedTextColor.RED));
            return true;
        }
        if (claim.getMembers().contains(target.getUniqueId())) {
            player.sendMessage(Component.text(target.getName() + " is already trusted.", NamedTextColor.YELLOW));
            return true;
        }
        claimRepository.addMember(claim.getId(), target.getUniqueId());
        player.sendMessage(Component.text(target.getName() + " is now trusted.", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You've been trusted in " + player.getName() + "'s claim \"", NamedTextColor.GREEN)
                .append(renderDisplayName(claim))
                .append(Component.text("\".", NamedTextColor.GREEN)));
        return true;
    }

    private boolean onUntrust(Player player, String[] args) {
        if (!player.hasPermission("landclaim.untrust")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /claim untrust <claim> <player>", NamedTextColor.RED));
            return true;
        }
        Claim claim = claimRepository.getPlayerClaimByName(player.getUniqueId(), args[1]);
        if (claim == null) {
            player.sendMessage(Component.text("You don't have a claim named \"" + args[1] + "\".", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        if (!claim.getMembers().contains(target.getUniqueId())) {
            player.sendMessage(Component.text(target.getName() + " is not trusted in this claim.", NamedTextColor.YELLOW));
            return true;
        }
        claimRepository.removeMember(claim.getId(), target.getUniqueId());
        claimRepository.deleteMemberFlags(claim.getId(), target.getUniqueId());
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

        if (claimRepository.overlapsAny(claim.getWorld(), claim.getX(), claim.getZ(), nextTier.getRadius(), claim.getId())) {
            player.sendMessage(Component.text("Upgrade would overlap another claim.", NamedTextColor.RED));
            return true;
        }

        if (economyManager.hasEconomy() && !economyManager.hasBalance(player, nextTier.getCost())) {
            player.sendMessage(Component.text("You need " + economyManager.format(nextTier.getCost()) + " to upgrade.", NamedTextColor.RED));
            return true;
        }

        economyManager.withdraw(player, nextTier.getCost());
        claimRepository.upgradeClaim(claim.getId(), nextTier.getRadius(), nextTier.getTier());
        player.sendMessage(Component.text("Claim \"", NamedTextColor.GREEN)
                .append(renderDisplayName(claim))
                .append(Component.text("\" upgraded to tier " + nextTierNum + "! Radius: " + nextTier.getRadius(), NamedTextColor.GREEN)));
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

    private boolean onFlag(Player player, String[] args) {
        if (!player.hasPermission("landclaim.manage")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /claim flag <claim> <flag> [on|off]", NamedTextColor.RED));
            return true;
        }
        Claim claim = claimRepository.getPlayerClaimByName(player.getUniqueId(), args[1]);
        if (claim == null) {
            player.sendMessage(Component.text("You don't have a claim named \"" + args[1] + "\".", NamedTextColor.RED));
            return true;
        }
        String flag = args[2].toLowerCase();
        if (!ConfigManager.CLAIM_FLAGS.contains(flag)) {
            player.sendMessage(Component.text("Unknown flag \"" + args[2] + "\". Valid flags: " + String.join(", ", ConfigManager.CLAIM_FLAGS), NamedTextColor.RED));
            return true;
        }
        if (args.length < 4) {
            boolean current = claimRepository.getClaimFlag(claim.getId(), flag);
            player.sendMessage(Component.text("Flag \"" + flag + "\" is " + (current ? "on" : "off") + " for \"", NamedTextColor.YELLOW)
                    .append(renderDisplayName(claim))
                    .append(Component.text("\".", NamedTextColor.YELLOW)));
            return true;
        }
        String value = args[3].toLowerCase();
        if (!value.equals("on") && !value.equals("off")) {
            player.sendMessage(Component.text("Usage: /claim flag <claim> <flag> [on|off]", NamedTextColor.RED));
            return true;
        }
        boolean enabled = value.equals("on");
        claimRepository.setClaimFlag(claim.getId(), flag, enabled);
        player.sendMessage(Component.text("Flag \"" + flag + "\" is now " + value + " for \"", NamedTextColor.GREEN)
                .append(renderDisplayName(claim))
                .append(Component.text("\".", NamedTextColor.GREEN)));
        return true;
    }

    private boolean onPerm(Player player, String[] args) {
        if (!player.hasPermission("landclaim.manage")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 4) {
            player.sendMessage(Component.text("Usage: /claim perm <claim> <player> <flag> [on|off]", NamedTextColor.RED));
            return true;
        }
        Claim claim = claimRepository.getPlayerClaimByName(player.getUniqueId(), args[1]);
        if (claim == null) {
            player.sendMessage(Component.text("You don't have a claim named \"" + args[1] + "\".", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        if (!claim.getMembers().contains(target.getUniqueId())) {
            player.sendMessage(Component.text(target.getName() + " is not trusted in this claim.", NamedTextColor.RED));
            return true;
        }
        String flag = args[3].toLowerCase();
        if (!ConfigManager.MEMBER_FLAGS.contains(flag)) {
            player.sendMessage(Component.text("Unknown flag \"" + args[3] + "\". Valid flags: " + String.join(", ", ConfigManager.MEMBER_FLAGS), NamedTextColor.RED));
            return true;
        }
        if (args.length < 5) {
            boolean current = claimRepository.getMemberFlag(claim.getId(), target.getUniqueId(), flag);
            player.sendMessage(Component.text(target.getName() + "'s \"" + flag + "\" is " + (current ? "on" : "off") + " in \"", NamedTextColor.YELLOW)
                    .append(renderDisplayName(claim))
                    .append(Component.text("\".", NamedTextColor.YELLOW)));
            return true;
        }
        String value = args[4].toLowerCase();
        if (!value.equals("on") && !value.equals("off")) {
            player.sendMessage(Component.text("Usage: /claim perm <claim> <player> <flag> [on|off]", NamedTextColor.RED));
            return true;
        }
        claimRepository.setMemberFlag(claim.getId(), target.getUniqueId(), flag, value.equals("on"));
        player.sendMessage(Component.text(target.getName() + "'s \"" + flag + "\" is now " + value + " in \"", NamedTextColor.GREEN)
                .append(renderDisplayName(claim))
                .append(Component.text("\".", NamedTextColor.GREEN)));
        return true;
    }

    private boolean onRename(Player player, String[] args) {
        if (!player.hasPermission("landclaim.manage")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /claim rename <old> <new>", NamedTextColor.RED));
            return true;
        }
        Claim claim = claimRepository.getPlayerClaimByName(player.getUniqueId(), args[1]);
        if (claim == null) {
            player.sendMessage(Component.text("You don't have a claim named \"" + args[1] + "\".", NamedTextColor.RED));
            return true;
        }
        String newName = args[2].toLowerCase();
        if (!newName.matches("[a-z0-9_-]+")) {
            player.sendMessage(Component.text("Claim name can only contain letters, numbers, underscores, and hyphens.", NamedTextColor.RED));
            return true;
        }
        Claim other = claimRepository.getPlayerClaimByName(player.getUniqueId(), newName);
        if (other != null && other.getId() != claim.getId()) {
            player.sendMessage(Component.text("You already have a claim with that name.", NamedTextColor.RED));
            return true;
        }
        if (newName.equalsIgnoreCase(claim.getName())) {
            player.sendMessage(Component.text("That's already the claim's name.", NamedTextColor.YELLOW));
            return true;
        }
        claimRepository.renameClaim(claim.getId(), newName);
        player.sendMessage(Component.text("Claim renamed to \"" + newName + "\".", NamedTextColor.GREEN));
        return true;
    }

    private boolean onDisplayName(Player player, String[] args) {
        if (!player.hasPermission("landclaim.manage")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /claim displayname <claim> <text...> (use - to clear)", NamedTextColor.RED));
            return true;
        }
        Claim claim = claimRepository.getPlayerClaimByName(player.getUniqueId(), args[1]);
        if (claim == null) {
            player.sendMessage(Component.text("You don't have a claim named \"" + args[1] + "\".", NamedTextColor.RED));
            return true;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim()
                .replaceAll("[\\r\\n\\u0000-\\u001f\\u007f-\\u009f]", "");
        if (text.isEmpty()) {
            player.sendMessage(Component.text("Usage: /claim displayname <claim> <text...> (use - to clear)", NamedTextColor.RED));
            return true;
        }
        if (text.equals("-") || text.equalsIgnoreCase("reset")) {
            claimRepository.setDisplayName(claim.getId(), null);
            player.sendMessage(Component.text("Display name for \"" + claim.getName() + "\" cleared.", NamedTextColor.GREEN));
            return true;
        }
        if (text.length() > 48) {
            player.sendMessage(Component.text("Display name is too long (max 48 characters).", NamedTextColor.RED));
            return true;
        }
        claimRepository.setDisplayName(claim.getId(), text);
        player.sendMessage(Component.text("Display name for \"" + claim.getName() + "\" set to \"", NamedTextColor.GREEN)
                .append(legacy.deserialize(text))
                .append(Component.text("\".", NamedTextColor.GREEN)));
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
        String formatted = ClaimFormat.resolveFields(template, claim, claimRepository, this::getOwnerName)
                .replace("{displayname}", ClaimFormat.styledDisplayName(template,
                        ClaimFormat.resolvedDisplayName(claim, claimRepository, this::getOwnerName)));

        return LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);
    }

    private Component renderDisplayName(Claim claim) {
        return legacy.deserialize(ClaimFormat.resolvedDisplayName(claim, claimRepository, this::getOwnerName));
    }

    private String getOwnerName(UUID uuid) {
        return claimAccess.resolveOwnerName(uuid);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("create", "delete", "list", "info", "trust", "untrust", "upgrade", "paytax", "flag", "perm", "rename", "displayname", "admin");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && sender instanceof Player player) {
            List<String> nameSubs = Arrays.asList("delete", "info", "upgrade", "flag", "perm", "rename", "displayname", "trust", "untrust");
            if (nameSubs.contains(args[0].toLowerCase())) {
                List<Claim> claims = claimRepository.getPlayerClaims(player.getUniqueId());
                return claims.stream().map(Claim::getName).filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("delete")) {
            if ("confirm".startsWith(args[2].toLowerCase())) {
                return List.of("confirm");
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("flag")) {
            return ConfigManager.CLAIM_FLAGS.stream()
                    .filter(f -> f.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            return null; // Bukkit completes online players
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("perm")) {
            return null; // Bukkit completes online players
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("perm")) {
            return ConfigManager.MEMBER_FLAGS.stream()
                    .filter(f -> f.startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if ((args.length == 4 && args[0].equalsIgnoreCase("flag"))
                || (args.length == 5 && args[0].equalsIgnoreCase("perm"))) {
            return Arrays.asList("on", "off").stream()
                    .filter(v -> v.startsWith(args[args.length - 1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
