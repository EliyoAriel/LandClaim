package com.landclaim.data;

import java.util.List;
import java.util.UUID;

public class Claim {
    private final int id;
    private final UUID owner;
    private String name;
    private final UUID world;
    private final int x;
    private final int z;
    private int radius;
    private int tier;
    private boolean active;
    private final long createdAt;
    private Long deactivatedAt;
    private List<UUID> members;

    public Claim(int id, UUID owner, String name, UUID world, int x, int z, int radius, int tier, boolean active, long createdAt) {
        this(id, owner, name, world, x, z, radius, tier, active, createdAt, null);
    }

    public Claim(int id, UUID owner, String name, UUID world, int x, int z, int radius, int tier, boolean active, long createdAt, Long deactivatedAt) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.world = world;
        this.x = x;
        this.z = z;
        this.radius = radius;
        this.tier = tier;
        this.active = active;
        this.createdAt = createdAt;
        this.deactivatedAt = deactivatedAt;
    }

    public boolean contains(UUID worldUuid, int bx, int bz) {
        if (!world.equals(worldUuid)) return false;
        double dx = bx - x;
        double dz = bz - z;
        return (dx * dx + dz * dz) <= (radius * radius);
    }

    public int getId() { return id; }
    public UUID getOwner() { return owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getWorld() { return world; }
    public int getX() { return x; }
    public int getZ() { return z; }
    public int getRadius() { return radius; }
    public void setRadius(int radius) { this.radius = radius; }
    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getCreatedAt() { return createdAt; }
    public Long getDeactivatedAt() { return deactivatedAt; }
    public void setDeactivatedAt(Long deactivatedAt) { this.deactivatedAt = deactivatedAt; }
    public List<UUID> getMembers() { return members; }
    public void setMembers(List<UUID> members) { this.members = members; }
}
