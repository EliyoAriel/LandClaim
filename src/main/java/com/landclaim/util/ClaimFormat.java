package com.landclaim.util;

import com.landclaim.config.ConfigManager;
import com.landclaim.data.Claim;
import com.landclaim.data.ClaimRepository;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ClaimFormat {

    private static final Pattern STYLE_PATTERN = Pattern.compile("(&[0-9a-fk-orA-FK-OR])+$");

    private ClaimFormat() {
    }

    public static String resolveFields(String template, Claim claim, ClaimRepository repository, Function<UUID, String> ownerNameResolver) {
        String ownerName = ownerNameResolver.apply(claim.getOwner());
        String status = claim.isActive() ? "Active" : "Inactive";
        String worldName = Bukkit.getWorld(claim.getWorld()) != null
                ? Bukkit.getWorld(claim.getWorld()).getName() : "unknown";
        String membersStr = claim.getMembers().stream()
                .map(ownerNameResolver)
                .collect(Collectors.joining(", "));
        if (membersStr.isEmpty()) membersStr = "None";
        String flagsStr = ConfigManager.CLAIM_FLAGS.stream()
                .map(f -> f + ": " + (repository.getClaimFlag(claim.getId(), f) ? "on" : "off"))
                .collect(Collectors.joining(", "));

        return template
                .replace("{name}", claim.getName())
                .replace("{owner}", ownerName)
                .replace("{x}", String.valueOf(claim.getX()))
                .replace("{z}", String.valueOf(claim.getZ()))
                .replace("{radius}", String.valueOf(claim.getRadius()))
                .replace("{tier}", String.valueOf(claim.getTier()))
                .replace("{status}", status)
                .replace("{world}", worldName)
                .replace("{id}", String.valueOf(claim.getId()))
                .replace("{members}", membersStr)
                .replace("{flags}", flagsStr);
    }

    public static String resolvedDisplayName(Claim claim, ClaimRepository repository, Function<UUID, String> ownerNameResolver) {
        return resolveFields(claim.getDisplayName(), claim, repository, ownerNameResolver);
    }

    public static String styledDisplayName(String template, String displayName) {
        int idx = template.indexOf("{displayname}");
        if (idx <= 0) return displayName;
        String prefix = template.substring(0, idx);
        String style = "";
        Matcher m = STYLE_PATTERN.matcher(prefix);
        if (m.find()) {
            style = m.group();
        }
        return displayName + style;
    }
}
