package com.landclaim.util;

import com.landclaim.data.Claim;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class ParticleUtil {

    private static final double SPACING_BLOCKS = 1.0;
    private static final int MAX_POINTS = 200;

    public static void showClaimBoundary(Player player, Claim claim) {
        World world = player.getServer().getWorld(claim.getWorld());
        if (world == null) return;

        boolean isOwner = claim.getOwner().equals(player.getUniqueId());
        Particle.DustOptions color = isOwner
                ? new Particle.DustOptions(Color.LIME, 1)
                : new Particle.DustOptions(Color.RED, 1);

        double radius = claim.getRadius();
        double cx = claim.getX() + 0.5;
        double cz = claim.getZ() + 0.5;
        int points = Math.min(MAX_POINTS, (int) Math.ceil(2 * Math.PI * radius / SPACING_BLOCKS));

        int playerY = player.getLocation().getBlockY();
        int belowY = Math.max(world.getMinHeight(), playerY - 20);
        int aboveY = Math.min(world.getMaxHeight() - 1, playerY + 20);
        int[] heights = {belowY, playerY, aboveY};

        for (int y : heights) {
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                double x = cx + radius * Math.cos(angle);
                double z = cz + radius * Math.sin(angle);
                world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, color);
            }
        }
    }
}
