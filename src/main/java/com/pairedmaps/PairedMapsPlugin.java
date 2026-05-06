package com.pairedmaps;

import com.pairedmaps.command.CommandRegistrar;
import com.pairedmaps.config.ConfigManager;
import com.pairedmaps.config.MessageManager;
import com.pairedmaps.database.DatabaseManager;
import com.pairedmaps.database.MinimapRecord;
import com.pairedmaps.display.DisplayManager;
import com.pairedmaps.display.HandheldCubeRenderer;
import com.pairedmaps.minimap.MinimapManager;
import com.pairedmaps.minimap.MinimapVolume;
import com.pairedmaps.minimap.ProjectionUtil;
import com.pairedmaps.util.DisplayUtil;
import com.pairedmaps.listener.TerrainListener;
import com.pairedmaps.map.MarkerMapRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.*;

public class PairedMapsPlugin extends org.bukkit.plugin.java.JavaPlugin implements Listener {

    public static final String PDC_MINIMAP_ID = "minimap_id";

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private DisplayManager displayManager;
    private MinimapManager minimapManager;
    private HandheldCubeRenderer handheldCubeRenderer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        configManager = new ConfigManager(this);
        configManager.load();

        messageManager = new MessageManager(this);
        messageManager.load();

        var mysql = getConfig().getConfigurationSection("mysql");
        if (mysql == null) {
            getLogger().severe("Missing mysql section in config.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            databaseManager = new DatabaseManager(mysql, getLogger());
        } catch (RuntimeException e) {
            getLogger().severe("Failed to initialize MySQL pool");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            databaseManager.initSchema();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database schema");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        displayManager = new DisplayManager(this);
        handheldCubeRenderer = new HandheldCubeRenderer(this);
        minimapManager = new MinimapManager(this, databaseManager, displayManager, configManager);

        try {
            minimapManager.loadRecordsOnly();
        } catch (SQLException e) {
            getLogger().severe("Failed to load minimaps from database");
            e.printStackTrace();
        }

        CommandRegistrar commandRegistrar = new CommandRegistrar(this, minimapManager, messageManager);
        commandRegistrar.register();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new TerrainListener(this), this);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            minimapManager.resetAll();
            minimapManager.startUpdateTask();
        }, 20L);
    }

    @Override
    public void onDisable() {
        if (handheldCubeRenderer != null) handheldCubeRenderer.clearAll();
        if (minimapManager != null) minimapManager.stopUpdateTask();
        if (displayManager != null) displayManager.despawnAll();
        if (databaseManager != null) databaseManager.shutdown();
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (handheldCubeRenderer != null) handheldCubeRenderer.clearCubeForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (displayManager != null) {
            displayManager.handleInteractionClick(event.getPlayer(), event.getRightClicked().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (handheldCubeRenderer != null) handheldCubeRenderer.clearCubeForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        NamespacedKey pdcKey = new NamespacedKey(this, PDC_MINIMAP_ID);
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        boolean holdingMap = newItem != null && newItem.hasItemMeta()
                && newItem.getItemMeta().getPersistentDataContainer().has(pdcKey, PersistentDataType.INTEGER);
        if (!holdingMap) handheldCubeRenderer.clearCubeForPlayer(player);
    }

    private MinimapRecord getHeldMinimapRecord(Player player) {
        NamespacedKey pdcKey = new NamespacedKey(this, PDC_MINIMAP_ID);
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        ItemStack held = null;
        if (isMinimapItem(main, pdcKey)) held = main;
        else if (isMinimapItem(off, pdcKey)) held = off;

        if (held == null) return null;
        int id = held.getItemMeta().getPersistentDataContainer().get(pdcKey, PersistentDataType.INTEGER);
        return minimapManager.getMinimap(id).orElse(null);
    }

    private boolean isMinimapItem(ItemStack item, NamespacedKey key) {
        return item != null && item.getType() != Material.AIR && item.hasItemMeta() 
               && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.INTEGER);
    }

    public void tickHandheldCubes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            MinimapRecord rec = getHeldMinimapRecord(player);
            if (rec != null) handheldCubeRenderer.updateCubeForPlayer(player, rec);
            else if (handheldCubeRenderer.hasCube(player)) handheldCubeRenderer.clearCubeForPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!configManager.isDeathEffect()) return;
        Player player = event.getEntity();
        World world = player.getWorld();
        for (var rec : minimapManager.getAllMinimaps()) {
            if (!rec.getWorld().equals(world.getName())) continue;
            MinimapVolume regVol = new MinimapVolume(rec.getRegX1(), rec.getRegY1(), rec.getRegZ1(), rec.getRegX2(), rec.getRegY2(), rec.getRegZ2());
            if (!regVol.contains(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())) continue;
            MinimapVolume mapVol = new MinimapVolume(rec.getMapX1(), rec.getMapY1(), rec.getMapZ1(), rec.getMapX2(), rec.getMapY2(), rec.getMapZ2());
            double[] proj = ProjectionUtil.project(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(), mapVol, regVol);
            spawnDeathBurst(world, proj);
        }
    }

    private void spawnDeathBurst(World world, double[] center) {
        int duration = configManager.getDeathEffectDurationTicks();
        int count = 6 + new Random().nextInt(3);
        float scale = (float) (configManager.getDotScale() * 0.6);
        List<BlockDisplay> particles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double dx = Math.cos(angle) * 0.3;
            double dz = Math.sin(angle) * 0.3;
            Location loc = new Location(world, center[0] + dx, center[1], center[2] + dz);
            BlockDisplay bd = world.spawn(loc, BlockDisplay.class, d -> {
                d.setTransformation(DisplayUtil.buildTransformation(scale));
                d.setBlock(Material.RED_CONCRETE.createBlockData());
                d.setPersistent(false);
            });
            particles.add(bd);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (BlockDisplay bd : particles) bd.remove();
        }, duration);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!configManager.isExplosionRing() || event.getEntity() == null) return;
        handleExplosionAt(event.getEntity().getWorld(), event.getLocation().getX(), event.getLocation().getY(), event.getLocation().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!configManager.isExplosionRing()) return;
        handleExplosionAt(event.getBlock().getWorld(), event.getBlock().getLocation().getX(), event.getBlock().getLocation().getY(), event.getBlock().getLocation().getZ());
    }

    private void handleExplosionAt(World world, double ex, double ey, double ez) {
        for (var rec : minimapManager.getAllMinimaps()) {
            if (!rec.getWorld().equals(world.getName())) continue;
            MinimapVolume regVol = new MinimapVolume(rec.getRegX1(), rec.getRegY1(), rec.getRegZ1(), rec.getRegX2(), rec.getRegY2(), rec.getRegZ2());
            if (!regVol.contains(ex, ey, ez)) continue;
            MinimapVolume mapVol = new MinimapVolume(rec.getMapX1(), rec.getMapY1(), rec.getMapZ1(), rec.getMapX2(), rec.getMapY2(), rec.getMapZ2());
            double[] proj = ProjectionUtil.project(ex, ey, ez, mapVol, regVol);
            spawnExplosionRing(world, proj);
        }
    }

    private void spawnExplosionRing(World world, double[] center) {
        float scale = (float) (configManager.getDotScale() * 0.8);
        int segments = 8;
        List<BlockDisplay> ring = new ArrayList<>();
        for (int i = 0; i < segments; i++) {
            double angle = (2 * Math.PI / segments) * i;
            double dx = Math.cos(angle) * 0.5;
            double dz = Math.sin(angle) * 0.5;
            Location loc = new Location(world, center[0] + dx, center[1], center[2] + dz);
            BlockDisplay bd = world.spawn(loc, BlockDisplay.class, d -> {
                d.setTransformation(DisplayUtil.buildTransformation(scale));
                d.setBlock(Material.ORANGE_CONCRETE.createBlockData());
                d.setPersistent(false);
            });
            ring.add(bd);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (BlockDisplay bd : ring) bd.remove();
        }, 20L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;
        var meta = event.getCurrentItem().getItemMeta();
        if (meta == null) return;
        NamespacedKey key = new NamespacedKey(this, PDC_MINIMAP_ID);
        if (!meta.getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int id = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        if (event.getClick().isRightClick()) {
            try {
                if (minimapManager.removeMinimap(id)) {
                    player.sendMessage(messageManager.get("minimap-removed", "id", String.valueOf(id)));
                    player.closeInventory();
                }
            } catch (SQLException e) {
                player.sendMessage(messageManager.get("db-error"));
                e.printStackTrace();
            }
            return;
        }
        minimapManager.getMinimap(id).ifPresent(rec -> {
            World w = Bukkit.getWorld(rec.getWorld());
            if (w == null) return;
            int cx = (int) ((rec.getRegX1() + rec.getRegX2()) / 2.0);
            int cz = (int) ((rec.getRegZ1() + rec.getRegZ2()) / 2.0);
            double regionSize = Math.max(Math.abs(rec.getRegX2() - rec.getRegX1()), Math.abs(rec.getRegZ2() - rec.getRegZ1()));
            MapView.Scale scale;
            if (regionSize <= 128) scale = MapView.Scale.CLOSEST;
            else if (regionSize <= 256) scale = MapView.Scale.CLOSE;
            else if (regionSize <= 512) scale = MapView.Scale.NORMAL;
            else if (regionSize <= 1024) scale = MapView.Scale.FAR;
            else scale = MapView.Scale.FARTHEST;
            MapView view = Bukkit.createMap(w);
            view.setCenterX(cx);
            view.setCenterZ(cz);
            view.setScale(scale);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(true);
            view.addRenderer(new MarkerMapRenderer(rec));
            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
            mapMeta.setMapView(view);
            mapMeta.setDisplayName("§6Region Map \u2014 Minimap #" + id);
            mapMeta.setLore(Collections.singletonList("§7This is a real world map of the region."));
            mapItem.setItemMeta(mapMeta);
            player.closeInventory();
            player.getInventory().addItem(mapItem);
            player.sendMessage(messageManager.get("region-map-given", "id", String.valueOf(id)));
        });
    }

    public ConfigManager getConfigManager() { return configManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public DisplayManager getDisplayManager() { return displayManager; }
    public MinimapManager getMinimapManager() { return minimapManager; }
}