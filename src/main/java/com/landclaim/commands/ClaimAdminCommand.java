package com.landclaim.commands;

import com.landclaim.LandClaimPlugin;
import com.landclaim.config.ConfigManager;
import com.landclaim.data.Claim;
import com.landclaim.data.ClaimRepository;
import com.landclaim.protection.ClaimAccess;
import com.landclaim.util.ClaimFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClaimAdminCommand {

    private final LandClaimPlugin plugin;
    private final ClaimRepository claimRepository;
    private final ConfigManager configManager;
    private final ClaimAccess claimAccess;

    public ClaimAdminCommand(LandClaimPlugin plugin, ClaimRepository claimRepository, ConfigManager configManager, ClaimAccess claimAccess) {
        this.plugin = plugin;
        this.claimRepository = claimRepository;
        this.configManager = configManager;
        this.claimAccess = claimAccess;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("landclaim.admin")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /claim admin <info|delete|reload> [player]", NamedTextColor.RED));
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "info" -> onAdminInfo(sender, args);
            case "delete" -> onAdminDelete(sender, args);
            case "reload" -> onAdminReload(sender);
            default -> {
                sender.sendMessage(Component.text("Unknown admin subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean onAdminInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /claim admin info <player>", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        List<Claim> claims = claimRepository.getPlayerClaims(target.getUniqueId());
        sender.sendMessage(Component.text(target.getName() + " has " + claims.size() + " claims:", NamedTextColor.GOLD));
        String format = configManager.getListFormat();
        for (Claim c : claims) {
            sender.sendMessage(formatClaimTemplate(format, c));
        }
        return true;
    }

    private boolean onAdminDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /claim admin delete <player>", NamedTextColor.RED));
            return true;
        }
        String playerName = args[2];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(playerName);
        if (target == null || !target.hasPlayedBefore()) {
            Player online = plugin.getServer().getPlayerExact(playerName);
            if (online == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            target = online;
        }
        List<Claim> claims = claimRepository.getPlayerClaims(target.getUniqueId());
        if (claims.isEmpty()) {
            sender.sendMessage(Component.text(target.getName() + " has no claims.", NamedTextColor.YELLOW));
            return true;
        }
        for (Claim c : claims) {
            com.landclaim.integration.RewindHook.unexcludeClaim(c);
            claimRepository.deleteClaim(c.getId());
        }
        sender.sendMessage(Component.text("Deleted " + claims.size() + " claim(s) for " + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean onAdminReload(CommandSender sender) {
        configManager.reload();
        claimRepository.loadAllClaims();
        com.landclaim.integration.RewindHook.init();
        com.landclaim.integration.RewindHook.rebuild(claimRepository.getAllClaims());
        sender.sendMessage(Component.text("Config and claims reloaded.", NamedTextColor.GREEN));
        return true;
    }

    private Component formatClaimTemplate(String template, Claim claim) {
        String formatted = ClaimFormat.resolveFields(template, claim, claimRepository, this::getOwnerName)
                .replace("{displayname}", ClaimFormat.styledDisplayName(template,
                        ClaimFormat.resolvedDisplayName(claim, claimRepository, this::getOwnerName)));

        return LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);
    }

    private String getOwnerName(UUID uuid) {
        return claimAccess.resolveOwnerName(uuid);
    }
}
