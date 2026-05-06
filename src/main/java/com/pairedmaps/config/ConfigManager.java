package com.pairedmaps.config;
import com.pairedmaps.PairedMapsPlugin;
import org.bukkit.configuration.file.FileConfiguration;
public final class ConfigManager {
    private final PairedMapsPlugin plugin;
    private int updateIntervalTicks;
    private double dotScale;
    private String playerDotColor;
    private String itemDotColor;
    private int itemFlashThresholdTicks;
    private int deathEffectDurationTicks;
    private boolean playerHeadDots;
    private boolean itemDots;
    private boolean terrainRender;
    private int terrainSamplesPerAxis;
    private double terrainDotScale;
    private boolean deathEffect;
    private boolean explosionRing;
    private double aspectRatioEpsilon;
    public ConfigManager(PairedMapsPlugin plugin) {
        this.plugin = plugin;
    }
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        updateIntervalTicks = cfg.getInt("update-interval-ticks", 1);
        if (updateIntervalTicks < 1) {
            plugin.getLogger().warning("update-interval-ticks must be >= 1; defaulting to 1");
            updateIntervalTicks = 1;
        }
        dotScale = cfg.getDouble("dot-scale", 0.1);
        if (dotScale <= 0) {
            plugin.getLogger().warning("dot-scale must be > 0; defaulting to 0.1");
            dotScale = 0.1;
        }
        playerDotColor = cfg.getString("player-dot-color", "#FF0000");
        itemDotColor   = cfg.getString("item-dot-color", "#FFFFFF");
        itemFlashThresholdTicks = cfg.getInt("item-flash-threshold-ticks", 60);
        if (itemFlashThresholdTicks < 0) {
            plugin.getLogger().warning("item-flash-threshold-ticks must be >= 0; defaulting to 60");
            itemFlashThresholdTicks = 60;
        }
        deathEffectDurationTicks = cfg.getInt("death-effect-duration-ticks", 60);
        if (deathEffectDurationTicks < 1) {
            plugin.getLogger().warning("death-effect-duration-ticks must be >= 1; defaulting to 60");
            deathEffectDurationTicks = 60;
        }
        playerHeadDots       = cfg.getBoolean("player-head-dots", false);
        itemDots             = cfg.getBoolean("item-dots", true);
        terrainRender        = cfg.getBoolean("terrain-render", true);
        terrainSamplesPerAxis = cfg.getInt("terrain-samples-per-axis", 20);
        if (terrainSamplesPerAxis < 2) {
            plugin.getLogger().warning("terrain-samples-per-axis must be >= 2; defaulting to 2");
            terrainSamplesPerAxis = 2;
        } else if (terrainSamplesPerAxis > 64) {
            plugin.getLogger().warning("terrain-samples-per-axis is capped at 64 to prevent TPS spikes; clamping.");
            terrainSamplesPerAxis = 64;
        }
        terrainDotScale = cfg.getDouble("terrain-dot-scale", 0.08);
        if (terrainDotScale <= 0) {
            plugin.getLogger().warning("terrain-dot-scale must be > 0; defaulting to 0.08");
            terrainDotScale = 0.08;
        }
        deathEffect    = cfg.getBoolean("death-effect", true);
        explosionRing  = cfg.getBoolean("explosion-ring", true);
        aspectRatioEpsilon = cfg.getDouble("aspect-ratio-epsilon", 0.05);
        if (aspectRatioEpsilon < 0) {
            plugin.getLogger().warning("aspect-ratio-epsilon must be >= 0; defaulting to 0.05");
            aspectRatioEpsilon = 0.05;
        }
    }
    public int getUpdateIntervalTicks() { return updateIntervalTicks; }
    public double getDotScale() { return dotScale; }
    public String getPlayerDotColor() { return playerDotColor; }
    public String getItemDotColor() { return itemDotColor; }
    public int getItemFlashThresholdTicks() { return itemFlashThresholdTicks; }
    public int getDeathEffectDurationTicks() { return deathEffectDurationTicks; }
    public boolean isPlayerHeadDots() { return playerHeadDots; }
    public boolean isItemDots() { return itemDots; }
    public boolean isTerrainRender() { return terrainRender; }
    public int getTerrainSamplesPerAxis() { return terrainSamplesPerAxis; }
    public double getTerrainDotScale() { return terrainDotScale; }
    public boolean isDeathEffect() { return deathEffect; }
    public boolean isExplosionRing() { return explosionRing; }
    public double getAspectRatioEpsilon() { return aspectRatioEpsilon; }
}

