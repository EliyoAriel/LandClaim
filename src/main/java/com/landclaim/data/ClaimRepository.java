package com.landclaim.data;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class ClaimRepository {

    private final DatabaseManager db;
    private final Map<UUID, List<Claim>> claimsByWorld = new HashMap<>();
    private final Map<Integer, Claim> claimsById = new HashMap<>();

    public ClaimRepository(DatabaseManager db) {
        this.db = db;
    }

    public void loadAllClaims() {
        claimsByWorld.clear();
        claimsById.clear();
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM claims")) {
            while (rs.next()) {
                Long deactivatedAt = rs.getString("deactivated_at") != null
                        ? rs.getLong("deactivated_at") : null;
                Claim claim = new Claim(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("name"),
                        UUID.fromString(rs.getString("world_uuid")),
                        rs.getInt("center_x"),
                        rs.getInt("center_z"),
                        rs.getInt("radius"),
                        rs.getInt("tier"),
                        rs.getBoolean("active"),
                        rs.getLong("created_at"),
                        deactivatedAt
                );
                claim.setMembers(getMembers(claim.getId()));
                claimsById.put(claim.getId(), claim);
                claimsByWorld.computeIfAbsent(claim.getWorld(), k -> new ArrayList<>()).add(claim);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Claim createClaim(UUID owner, String name, UUID world, int x, int z, int radius, int tier) {
        String sql = "INSERT INTO claims (owner_uuid, name, world_uuid, center_x, center_z, radius, tier, active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, owner.toString());
            ps.setString(2, name);
            ps.setString(3, world.toString());
            ps.setInt(4, x);
            ps.setInt(5, z);
            ps.setInt(6, radius);
            ps.setInt(7, tier);
            ps.setLong(8, Instant.now().toEpochMilli());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                Claim claim = new Claim(id, owner, name, world, x, z, radius, tier, true, Instant.now().toEpochMilli());
                claim.setMembers(new ArrayList<>());
                claimsById.put(id, claim);
                claimsByWorld.computeIfAbsent(world, k -> new ArrayList<>()).add(claim);
                return claim;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteClaim(int id) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM claims WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            Claim claim = claimsById.remove(id);
            if (claim != null) {
                List<Claim> list = claimsByWorld.get(claim.getWorld());
                if (list != null) {
                    list.remove(claim);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Claim getClaimAt(UUID worldUuid, int x, int z) {
        List<Claim> claims = claimsByWorld.get(worldUuid);
        if (claims == null) return null;
        for (Claim claim : claims) {
            if (claim.contains(worldUuid, x, z)) return claim;
        }
        return null;
    }

    public List<Claim> getPlayerClaims(UUID playerUuid) {
        return claimsById.values().stream()
                .filter(c -> c.getOwner().equals(playerUuid))
                .collect(Collectors.toList());
    }

    public int getClaimCount(UUID playerUuid) {
        return (int) claimsById.values().stream()
                .filter(c -> c.getOwner().equals(playerUuid))
                .count();
    }

    public Claim getPlayerClaimByName(UUID ownerUuid, String name) {
        return claimsById.values().stream()
                .filter(c -> c.getOwner().equals(ownerUuid) && c.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public void addMember(int claimId, UUID playerUuid) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("INSERT OR IGNORE INTO claim_members (claim_id, player_uuid) VALUES (?, ?)")) {
            ps.setInt(1, claimId);
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
            Claim claim = claimsById.get(claimId);
            if (claim != null && claim.getMembers() != null) {
                claim.getMembers().add(playerUuid);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeMember(int claimId, UUID playerUuid) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM claim_members WHERE claim_id = ? AND player_uuid = ?")) {
            ps.setInt(1, claimId);
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
            Claim claim = claimsById.get(claimId);
            if (claim != null && claim.getMembers() != null) {
                claim.getMembers().remove(playerUuid);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<UUID> getMembers(int claimId) {
        List<UUID> members = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT player_uuid FROM claim_members WHERE claim_id = ?")) {
            ps.setInt(1, claimId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(UUID.fromString(rs.getString("player_uuid")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return members;
    }

    public void upgradeClaim(int claimId, int newRadius, int newTier) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("UPDATE claims SET radius = ?, tier = ? WHERE id = ?")) {
            ps.setInt(1, newRadius);
            ps.setInt(2, newTier);
            ps.setInt(3, claimId);
            ps.executeUpdate();
            Claim claim = claimsById.get(claimId);
            if (claim != null) {
                claim.setRadius(newRadius);
                claim.setTier(newTier);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setClaimActive(int claimId, boolean active) {
        String sql = active
                ? "UPDATE claims SET active = 1, deactivated_at = NULL WHERE id = ?"
                : "UPDATE claims SET active = 0, deactivated_at = ? WHERE id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            if (active) {
                ps.setInt(1, claimId);
            } else {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setInt(2, claimId);
            }
            ps.executeUpdate();
            Claim claim = claimsById.get(claimId);
            if (claim != null) {
                claim.setActive(active);
                claim.setDeactivatedAt(active ? null : Instant.now().toEpochMilli());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Claim> getAllClaims() {
        return new ArrayList<>(claimsById.values());
    }

    public Map<UUID, List<Claim>> getClaimsByWorld() {
        return claimsByWorld;
    }

    public Claim getClaimById(int id) {
        return claimsById.get(id);
    }
}
