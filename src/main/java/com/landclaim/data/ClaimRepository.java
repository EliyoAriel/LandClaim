package com.landclaim.data;

import com.landclaim.config.ConfigManager;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class ClaimRepository {

    private final DatabaseManager db;
    private final ConfigManager configManager;
    private final Map<UUID, List<Claim>> claimsByWorld = new HashMap<>();
    private final Map<Integer, Claim> claimsById = new HashMap<>();
    private final Map<Integer, Map<String, Boolean>> claimFlagOverrides = new HashMap<>();
    private final Map<Integer, Map<UUID, Map<String, Boolean>>> memberFlagOverrides = new HashMap<>();

    public ClaimRepository(DatabaseManager db, ConfigManager configManager) {
        this.db = db;
        this.configManager = configManager;
    }

    public void loadAllClaims() {
        claimsByWorld.clear();
        claimsById.clear();
        loadFlagOverrides();
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
                claim.setDisplayName(rs.getString("displayname"));
                claimsById.put(claim.getId(), claim);
                claimsByWorld.computeIfAbsent(claim.getWorld(), k -> new ArrayList<>()).add(claim);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadFlagOverrides() {
        claimFlagOverrides.clear();
        memberFlagOverrides.clear();
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT claim_id, flag, value FROM claim_flags")) {
            while (rs.next()) {
                claimFlagOverrides.computeIfAbsent(rs.getInt("claim_id"), k -> new HashMap<>())
                        .put(rs.getString("flag"), rs.getInt("value") == 1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT claim_id, player_uuid, flag, value FROM claim_member_flags")) {
            while (rs.next()) {
                memberFlagOverrides.computeIfAbsent(rs.getInt("claim_id"), k -> new HashMap<>())
                        .computeIfAbsent(UUID.fromString(rs.getString("player_uuid")), k -> new HashMap<>())
                        .put(rs.getString("flag"), rs.getInt("value") == 1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean getClaimFlag(int claimId, String flag) {
        Boolean override = claimFlagOverrides.getOrDefault(claimId, Map.of()).get(flag);
        return override != null ? override : configManager.getFlagDefault(flag);
    }

    public void setClaimFlag(int claimId, String flag, boolean value) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO claim_flags (claim_id, flag, value) VALUES (?, ?, ?) " +
                "ON CONFLICT(claim_id, flag) DO UPDATE SET value = excluded.value")) {
            ps.setInt(1, claimId);
            ps.setString(2, flag);
            ps.setInt(3, value ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        claimFlagOverrides.computeIfAbsent(claimId, k -> new HashMap<>()).put(flag, value);
    }

    public boolean getMemberFlag(int claimId, UUID playerUuid, String flag) {
        Boolean override = memberFlagOverrides.getOrDefault(claimId, Map.of())
                .getOrDefault(playerUuid, Map.of()).get(flag);
        return override == null || override;
    }

    public void setMemberFlag(int claimId, UUID playerUuid, String flag, boolean value) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO claim_member_flags (claim_id, player_uuid, flag, value) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(claim_id, player_uuid, flag) DO UPDATE SET value = excluded.value")) {
            ps.setInt(1, claimId);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, flag);
            ps.setInt(4, value ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        memberFlagOverrides.computeIfAbsent(claimId, k -> new HashMap<>())
                .computeIfAbsent(playerUuid, k -> new HashMap<>()).put(flag, value);
    }

    public void deleteMemberFlags(int claimId, UUID playerUuid) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM claim_member_flags WHERE claim_id = ? AND player_uuid = ?")) {
            ps.setInt(1, claimId);
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Map<UUID, Map<String, Boolean>> members = memberFlagOverrides.get(claimId);
        if (members != null) {
            members.remove(playerUuid);
        }
    }

    public void setDisplayName(int claimId, String displayName) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE claims SET displayname = ? WHERE id = ?")) {
            ps.setString(1, displayName);
            ps.setInt(2, claimId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Claim claim = claimsById.get(claimId);
        if (claim != null) {
            claim.setDisplayName(displayName);
        }
    }

    public boolean renameClaim(int claimId, String newName) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE claims SET name = ? WHERE id = ?")) {
            ps.setString(1, newName);
            ps.setInt(2, claimId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Claim claim = claimsById.get(claimId);
        if (claim != null) {
            claim.setName(newName);
            return true;
        }
        return false;
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

    public boolean overlapsAny(UUID worldUuid, int x, int z, int radius, Integer excludeClaimId) {
        List<Claim> claims = claimsByWorld.get(worldUuid);
        if (claims == null) return false;
        for (Claim claim : claims) {
            if (excludeClaimId != null && claim.getId() == excludeClaimId) continue;
            long dx = claim.getX() - x;
            long dz = claim.getZ() - z;
            long sum = (long) radius + claim.getRadius();
            if (dx * dx + dz * dz < sum * sum) return true;
        }
        return false;
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
