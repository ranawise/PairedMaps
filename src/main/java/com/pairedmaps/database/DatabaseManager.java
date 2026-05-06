package com.pairedmaps.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final boolean memoryMode;
    private final Map<Integer, MinimapRecord> memoryMinimaps = new LinkedHashMap<>();
    private final AtomicInteger memoryNextId = new AtomicInteger(1);

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS pm_minimaps (
                id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                world VARCHAR(64) NOT NULL,
                map_x1 DOUBLE NOT NULL,
                map_y1 DOUBLE NOT NULL,
                map_z1 DOUBLE NOT NULL,
                map_x2 DOUBLE NOT NULL,
                map_y2 DOUBLE NOT NULL,
                map_z2 DOUBLE NOT NULL,
                reg_x1 DOUBLE NOT NULL,
                reg_y1 DOUBLE NOT NULL,
                reg_z1 DOUBLE NOT NULL,
                reg_x2 DOUBLE NOT NULL,
                reg_y2 DOUBLE NOT NULL,
                reg_z2 DOUBLE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    public DatabaseManager(ConfigurationSection mysqlConfig, Logger logger) {
        if (mysqlConfig == null) {
            throw new IllegalArgumentException("mysql config section is required");
        }

        String mode = mysqlConfig.getString("mode", "mysql");
        this.memoryMode = "memory".equalsIgnoreCase(mode);

        if (memoryMode) {
            this.dataSource = null;
            logger.warning("[PairedMaps] Running in MEMORY mode — minimap data will NOT persist across restarts!");
            return;
        }

        HikariConfig cfg = new HikariConfig();
        String host = mysqlConfig.getString("host", "localhost");
        int port = mysqlConfig.getInt("port", 3306);
        String database = mysqlConfig.getString("database", "pairedmaps");
        String username = mysqlConfig.getString("username", "root");
        String password = mysqlConfig.getString("password", "password");
        int poolSize = mysqlConfig.getInt("pool-size", 5);

        cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(poolSize);
        cfg.setPoolName("PairedMaps-HikariPool");
        cfg.setConnectionTimeout(5000);
        cfg.setValidationTimeout(3000);
        cfg.setInitializationFailTimeout(5000);

        this.dataSource = new HikariDataSource(cfg);
    }

    public void initSchema() throws SQLException {
        if (memoryMode) return;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(CREATE_TABLE);
        }
    }

    public int insertMinimap(String world,
                             double mapX1, double mapY1, double mapZ1,
                             double mapX2, double mapY2, double mapZ2,
                             double regX1, double regY1, double regZ1,
                             double regX2, double regY2, double regZ2) throws SQLException {
        if (memoryMode) {
            int id = memoryNextId.getAndIncrement();
            MinimapRecord rec = new MinimapRecord(
                    id, world,
                    mapX1, mapY1, mapZ1, mapX2, mapY2, mapZ2,
                    regX1, regY1, regZ1, regX2, regY2, regZ2,
                    String.valueOf(System.currentTimeMillis())
            );
            memoryMinimaps.put(id, rec);
            return id;
        }

        String sql = """
                INSERT INTO pm_minimaps (world, map_x1, map_y1, map_z1, map_x2, map_y2, map_z2,
                                         reg_x1, reg_y1, reg_z1, reg_x2, reg_y2, reg_z2)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, world);
            ps.setDouble(2, mapX1); ps.setDouble(3, mapY1); ps.setDouble(4, mapZ1);
            ps.setDouble(5, mapX2); ps.setDouble(6, mapY2); ps.setDouble(7, mapZ2);
            ps.setDouble(8, regX1); ps.setDouble(9, regY1); ps.setDouble(10, regZ1);
            ps.setDouble(11, regX2); ps.setDouble(12, regY2); ps.setDouble(13, regZ2);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve generated minimap ID");
    }

    public boolean deleteMinimap(int id) throws SQLException {
        if (memoryMode) {
            return memoryMinimaps.remove(id) != null;
        }

        String sql = "DELETE FROM pm_minimaps WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<MinimapRecord> getMinimap(int id) throws SQLException {
        if (memoryMode) {
            return Optional.ofNullable(memoryMinimaps.get(id));
        }

        String sql = "SELECT * FROM pm_minimaps WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(recordFromRs(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<MinimapRecord> getAllMinimaps() throws SQLException {
        if (memoryMode) {
            return new ArrayList<>(memoryMinimaps.values());
        }

        String sql = "SELECT * FROM pm_minimaps";
        List<MinimapRecord> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(recordFromRs(rs));
            }
        }
        return list;
    }

    private MinimapRecord recordFromRs(ResultSet rs) throws SQLException {
        return new MinimapRecord(
                rs.getInt("id"),
                rs.getString("world"),
                rs.getDouble("map_x1"), rs.getDouble("map_y1"), rs.getDouble("map_z1"),
                rs.getDouble("map_x2"), rs.getDouble("map_y2"), rs.getDouble("map_z2"),
                rs.getDouble("reg_x1"), rs.getDouble("reg_y1"), rs.getDouble("reg_z1"),
                rs.getDouble("reg_x2"), rs.getDouble("reg_y2"), rs.getDouble("reg_z2"),
                rs.getString("created_at")
        );
    }

    public void shutdown() {
        if (memoryMode) return;
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
