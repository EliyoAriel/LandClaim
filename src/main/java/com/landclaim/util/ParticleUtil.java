package com.landclaim.util;

import com.landclaim.data.Claim;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class ParticleUtil {

    public static void showClaimBoundary(Player player, Claim claim) {
        World world = player.getServer().getWorld(claim.getWorld());
        if (world == null) return;

        boolean isOwner = claim.getOwner().equals(player.getUniqueId());
        Particle.DustOptions color = isOwner
                ? new Particle.DustOptions(Color.LIME, 1)
                : new Particle.DustOptions(Color.RED, 1);

        int points = 32;
        double radius = claim.getRadius();
        double cx = claim.getX() + 0.5;
        double cz = claim.getZ() + 0.5;

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            world.spawnParticle(Particle.DUST, x, player.getLocation().getY(), z, 1, 0, 0, 0, 0, color);
        }
    }
}
