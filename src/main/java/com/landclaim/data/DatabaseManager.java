package com.landclaim.data;

import com.landclaim.LandClaimPlugin;

import java.sql.*;

public class DatabaseManager {

    private final LandClaimPlugin plugin;
    private Connection connection;

    public DatabaseManager(LandClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/landclaim.db"
            );
            createTables();
            migrate();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS claims (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    name TEXT NOT NULL,
                    world_uuid TEXT NOT NULL,
                    center_x INTEGER NOT NULL,
                    center_z INTEGER NOT NULL,
                    radius INTEGER NOT NULL,
                    tier INTEGER NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    UNIQUE(owner_uuid, name)
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS claim_members (
                    claim_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (claim_id, player_uuid),
                    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS claim_taxes (
                    claim_id INTEGER NOT NULL,
                    week_start TEXT NOT NULL,
                    paid INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (claim_id, week_start),
                    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
                )
            """);
        }
    }

    private void migrate() throws SQLException {
        try (ResultSet rs = getConnection().getMetaData().getColumns(null, null, "claims", "name")) {
            if (!rs.next()) {
                try (Statement stmt = getConnection().createStatement()) {
                    stmt.execute("ALTER TABLE claims ADD COLUMN name TEXT NOT NULL DEFAULT ''");
                }
            }
        }
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_claims_owner_name ON claims(owner_uuid, name)");
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close database: " + e.getMessage());
        }
    }
}
