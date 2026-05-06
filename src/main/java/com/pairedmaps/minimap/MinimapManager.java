package com.pairedmaps.minimap;
import com.pairedmaps.PairedMapsPlugin;
import com.pairedmaps.config.ConfigManager;
import com.pairedmaps.database.DatabaseManager;
import com.pairedmaps.database.MinimapRecord;
import com.pairedmaps.display.DisplayManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class MinimapManager {
    private final PairedMapsPlugin plugin;
    private final DatabaseManager databaseManager;
    private final DisplayManager displayManager;
    private final ConfigManager configManager;
    private final Map<Integer, MinimapRecord> minimaps = new ConcurrentHashMap<>();
    private int updateTaskId = -1;
    public MinimapManager(PairedMapsPlugin plugin, DatabaseManager databaseManager,
                          DisplayManager displayManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.displayManager = displayManager;
        this.configManager = configManager;
    }
    public void loadAll() throws SQLException {
        loadRecordsOnly();
        resetAll();
    }
    public void loadRecordsOnly() throws SQLException {
        minimaps.clear();
        List<MinimapRecord> records = databaseManager.getAllMinimaps();
        for (MinimapRecord rec : records) {
            minimaps.put(rec.getId(), rec);
        }
    }
    public int addMinimap(String world,
                          double mapX1, double mapY1, double mapZ1,
                          double mapX2, double mapY2, double mapZ2,
                          double regX1, double regY1, double regZ1,
                          double regX2, double regY2, double regZ2) throws SQLException {
        int id = databaseManager.insertMinimap(world,
                mapX1, mapY1, mapZ1, mapX2, mapY2, mapZ2,
                regX1, regY1, regZ1, regX2, regY2, regZ2);
        MinimapRecord rec = databaseManager.getMinimap(id).orElseThrow();
        minimaps.put(id, rec);
        World mapWorld = Bukkit.getWorld(rec.getWorld());
        if (mapWorld != null) {
            displayManager.spawnAnchorForMinimap(id, mapWorld, rec);
            displayManager.renderTerrainForMinimap(id, mapWorld, rec, configManager);
        }
        return id;
    }
    public boolean removeMinimap(int id) throws SQLException {
        displayManager.despawnAllForMinimap(id);
        boolean deleted = databaseManager.deleteMinimap(id);
        if (deleted) {
            minimaps.remove(id);
        }
        return deleted;
    }
    public Optional<MinimapRecord> getMinimap(int id) {
        return Optional.ofNullable(minimaps.get(id));
    }
    public Collection<MinimapRecord> getAllMinimaps() {
        return Collections.unmodifiableCollection(minimaps.values());
    }
    public void startUpdateTask() {
        stopUpdateTask();
        int interval = configManager.getUpdateIntervalTicks();
        if (interval < 1) interval = 1;
        updateTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval).getTaskId();
    }
    public void stopUpdateTask() {
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
        }
    }
    private void tick() {
        plugin.tickHandheldCubes();
        displayManager.incrementFlashCounter();
        for (MinimapRecord rec : minimaps.values()) {
            World world = Bukkit.getWorld(rec.getWorld());
            if (world == null) continue;
            MinimapVolume mapVol = new MinimapVolume(
                    rec.getMapX1(), rec.getMapY1(), rec.getMapZ1(),
                    rec.getMapX2(), rec.getMapY2(), rec.getMapZ2());
            MinimapVolume regVol = new MinimapVolume(
                    rec.getRegX1(), rec.getRegY1(), rec.getRegZ1(),
                    rec.getRegX2(), rec.getRegY2(), rec.getRegZ2());
            displayManager.updatePlayerDots(rec.getId(), world, mapVol, regVol);
            displayManager.updateMobDots(rec.getId(), world, mapVol, regVol);
            if (configManager.isItemDots()) {
                displayManager.updateItemDots(rec.getId(), world, mapVol, regVol, configManager);
            }
        }
    }
    public void resetAll() {
        displayManager.despawnAll();
        for (MinimapRecord rec : minimaps.values()) {
            World world = Bukkit.getWorld(rec.getWorld());
            if (world != null) {
                displayManager.spawnAnchorForMinimap(rec.getId(), world, rec);
                displayManager.renderTerrainForMinimap(rec.getId(), world, rec, configManager);
            }
        }
    }
    public void reload() {
        configManager.load();
        resetAll();
        stopUpdateTask();
        startUpdateTask();
    }
}

