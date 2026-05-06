package com.pairedmaps.display;

import com.pairedmaps.PairedMapsPlugin;
import org.bukkit.Bukkit;
import com.pairedmaps.config.ConfigManager;
import com.pairedmaps.database.MinimapRecord;
import com.pairedmaps.minimap.MinimapVolume;
import com.pairedmaps.minimap.ProjectionUtil;
import com.pairedmaps.util.DisplayUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayManager {

    private final PairedMapsPlugin plugin;
    private final Map<String, UUID> playerDots  = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> interactionToPlayer = new ConcurrentHashMap<>();

    public void handleInteractionClick(Player clicker, UUID interactionId) {
        UUID targetId = interactionToPlayer.get(interactionId);
        if (targetId == null) return;

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            clicker.sendMessage("§cThat player is no longer online.");
            return;
        }

        if (clicker.getUniqueId().equals(targetId)) {
            clicker.sendMessage("§eYou cannot spectate yourself!");
            return;
        }

        clicker.setGameMode(org.bukkit.GameMode.SPECTATOR);
        clicker.teleport(target.getLocation());
        clicker.sendMessage("§aNow spectating §f" + target.getName() + "§a. Use §f/gm c §ato return.");
    }

    private final Map<String, UUID> mobDots     = new ConcurrentHashMap<>();
    private final Map<String, UUID> itemDots    = new ConcurrentHashMap<>();
    private final Map<String, UUID> terrainDots = new ConcurrentHashMap<>();
    private final Map<Integer, List<UUID>> aestheticEntities = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> anchors    = new ConcurrentHashMap<>();
    private final Set<UUID> managedEntities     = ConcurrentHashMap.newKeySet();

    private int flashTickCounter = 0;

    private static final String DOT_KEY_SEP = ":";
    private static final double DOT_Y_OFFSET = 0.03;

    private static final List<int[]>     CONCRETE_RGB;
    private static final List<Material>  CONCRETE_MAT;

    static {
        Object[][] table = {
            { Material.WHITE_CONCRETE,       207, 213, 214 },
            { Material.ORANGE_CONCRETE,      224,  97,   0 },
            { Material.MAGENTA_CONCRETE,     169,  48, 159 },
            { Material.LIGHT_BLUE_CONCRETE,   36, 137, 199 },
            { Material.YELLOW_CONCRETE,      241, 175,  21 },
            { Material.LIME_CONCRETE,         94, 169,  24 },
            { Material.PINK_CONCRETE,        213, 101, 142 },
            { Material.GRAY_CONCRETE,         54,  57,  61 },
            { Material.LIGHT_GRAY_CONCRETE,  125, 125, 115 },
            { Material.CYAN_CONCRETE,         21, 119, 136 },
            { Material.PURPLE_CONCRETE,      100,  32, 156 },
            { Material.BLUE_CONCRETE,         45,  47, 143 },
            { Material.BROWN_CONCRETE,        96,  60,  32 },
            { Material.GREEN_CONCRETE,        73,  91,  36 },
            { Material.RED_CONCRETE,         142,  33,  33 },
            { Material.BLACK_CONCRETE,         8,  10,  15 },
        };
        CONCRETE_MAT = new ArrayList<>();
        CONCRETE_RGB = new ArrayList<>();
        for (Object[] row : table) {
            CONCRETE_MAT.add((Material) row[0]);
            CONCRETE_RGB.add(new int[]{ (int) row[1], (int) row[2], (int) row[3] });
        }
    }

    public DisplayManager(PairedMapsPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnAnchorForMinimap(int minimapId, World world, MinimapRecord rec) {
        UUID existing = anchors.remove(minimapId);
        if (existing != null) {
            removeManagedEntity(existing);
        }

        Location loc = new Location(world, rec.getMapX1(), rec.getMapY1(), rec.getMapZ1());
        BlockDisplay anchor = world.spawn(loc, BlockDisplay.class, bd -> {
            bd.setTransformation(DisplayUtil.buildTransformation(0.0f));
            applyCommonDisplaySettings(bd);
            bd.setPersistent(false);
        });

        anchors.put(minimapId, anchor.getUniqueId());
        managedEntities.add(anchor.getUniqueId());

        spawnAestheticCube(minimapId, world, rec);
    }

    private void spawnAestheticCube(int id, World world, MinimapRecord rec) {
        clearAesthetics(id);
        List<UUID> list = new ArrayList<>();

        double sx = Math.abs(rec.getMapX2() - rec.getMapX1());
        double sy = Math.abs(rec.getMapY2() - rec.getMapY1());
        double sz = Math.abs(rec.getMapZ2() - rec.getMapZ1());

        Location origin = new Location(world, rec.getMapX1(), rec.getMapY1(), rec.getMapZ1());

        // 1. Tech Glass Enclosure
        spawnBlock(list, world, origin, (float)sx, (float)sy, (float)sz, Material.CYAN_STAINED_GLASS);

        // 2. Full Pro Frame (Deepslate Beams & Corners)
        float fS = 0.25f; // Frame thickness
        Material frameMat = Material.POLISHED_DEEPSLATE;
        Material lightMat = Material.SEA_LANTERN; // For glowing corners

        // Horizontal Beams (Bottom & Top)
        spawnBlock(list, world, origin.clone().add(0, -fS/2, -fS/2), (float)sx, fS, fS, frameMat);
        spawnBlock(list, world, origin.clone().add(0, sy - fS/2, -fS/2), (float)sx, fS, fS, frameMat);
        spawnBlock(list, world, origin.clone().add(0, -fS/2, sz - fS/2), (float)sx, fS, fS, frameMat);
        spawnBlock(list, world, origin.clone().add(0, sy - fS/2, sz - fS/2), (float)sx, fS, fS, frameMat);

        spawnBlock(list, world, origin.clone().add(-fS/2, -fS/2, 0), fS, fS, (float)sz, frameMat);
        spawnBlock(list, world, origin.clone().add(sx - fS/2, -fS/2, 0), fS, fS, (float)sz, frameMat);
        spawnBlock(list, world, origin.clone().add(-fS/2, sy - fS/2, 0), fS, fS, (float)sz, frameMat);
        spawnBlock(list, world, origin.clone().add(sx - fS/2, sy - fS/2, 0), fS, fS, (float)sz, frameMat);

        // Vertical Beams
        spawnBlock(list, world, origin.clone().add(-fS/2, 0, -fS/2), fS, (float)sy, fS, frameMat);
        spawnBlock(list, world, origin.clone().add(sx - fS/2, 0, -fS/2), fS, (float)sy, fS, frameMat);
        spawnBlock(list, world, origin.clone().add(-fS/2, 0, sz - fS/2), fS, (float)sy, fS, frameMat);
        spawnBlock(list, world, origin.clone().add(sx - fS/2, 0, sz - fS/2), fS, (float)sy, fS, frameMat);

        // 3. Glowing Corner Lights (Sea Lanterns at vertices)
        float lS = 0.3f;
        spawnBlock(list, world, origin.clone().add(-lS/2, -lS/2, -lS/2), lS, lS, lS, lightMat);
        spawnBlock(list, world, origin.clone().add(sx-lS/2, -lS/2, -lS/2), lS, lS, lS, lightMat);
        spawnBlock(list, world, origin.clone().add(-lS/2, sy-lS/2, -lS/2), lS, lS, lS, lightMat);
        spawnBlock(list, world, origin.clone().add(sx-lS/2, sy-lS/2, -lS/2), lS, lS, lS, lightMat);
        spawnBlock(list, world, origin.clone().add(-lS/2, -lS/2, sz-lS/2), lS, lS, lS, lightMat);
        spawnBlock(list, world, origin.clone().add(sx-lS/2, -lS/2, sz-lS/2), lS, lS, lS, lightMat);
        spawnBlock(list, world, origin.clone().add(-lS/2, sy-lS/2, sz-lS/2), lS, lS, lS, lightMat);
        spawnBlock(list, world, origin.clone().add(sx-lS/2, sy-lS/2, sz-lS/2), lS, lS, lS, lightMat);

        // 4. Comprehensive Grid (Cyan Glow)
        Material wireMat = Material.CYAN_CONCRETE;
        float wT = 0.02f;
        for (int i = 1; i < 5; i++) {
            float offX = (float) (i * (sx / 5.0));
            float offY = (float) (i * (sy / 5.0));
            float offZ = (float) (i * (sz / 5.0));
            
            // Floor Grid
            spawnBlock(list, world, origin.clone().add(offX, 0, 0), wT, wT, (float)sz, wireMat);
            spawnBlock(list, world, origin.clone().add(0, 0, offZ), (float)sx, wT, wT, wireMat);
            
            // Wall Grid
            spawnBlock(list, world, origin.clone().add(0, offY, 0), (float)sx, wT, wT, wireMat);
            spawnBlock(list, world, origin.clone().add(0, offY, sz), (float)sx, wT, wT, wireMat);
        }

        // 5. Axis Labels
        spawnLabel(list, world, origin.clone().add(sx/2, -0.8, 0), "§b§l§n[ X-AXIS ]");
        spawnLabel(list, world, origin.clone().add(-0.8, sy/2, 0), "§a§l§n[ Y-AXIS ]");
        spawnLabel(list, world, origin.clone().add(0, -0.8, sz/2), "§d§l§n[ Z-AXIS ]");

        aestheticEntities.put(id, list);
    }

    private void spawnBlock(List<UUID> list, World world, Location loc, float sx, float sy, float sz, Material mat) {
        BlockDisplay bd = world.spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(mat.createBlockData());
            d.setTransformation(DisplayUtil.buildTransformation(sx, sy, sz));
            applyCommonDisplaySettings(d);
            d.setPersistent(false);
        });
        list.add(bd.getUniqueId());
        managedEntities.add(bd.getUniqueId());
    }

    private void spawnLabel(List<UUID> list, World world, Location loc, String text) {
        TextDisplay label = world.spawn(loc, TextDisplay.class, td -> {
            td.setText(text);
            td.setBillboard(Billboard.CENTER);
            td.setBackgroundColor(org.bukkit.Color.fromARGB(150, 0, 0, 0));
            td.setBrightness(new Brightness(15, 15));
            td.setPersistent(false);
        });
        list.add(label.getUniqueId());
        managedEntities.add(label.getUniqueId());
    }

    private void clearAesthetics(int id) {
        List<UUID> ids = aestheticEntities.remove(id);
        if (ids != null) {
            for (UUID uid : ids) removeManagedEntity(uid);
        }
    }

    public void updatePlayerDots(int minimapId, World world, MinimapVolume mapVol, MinimapVolume regVol) {
        ConfigManager cfg = plugin.getConfigManager();
        Material dotMaterial = Material.RED_GLAZED_TERRACOTTA; // Brighter red
        float scale = (float) cfg.getDotScale() * 1.5f;
        boolean useHeads = cfg.isPlayerHeadDots();

        for (Player player : world.getPlayers()) {
            double px = player.getLocation().getX();
            double py = player.getLocation().getY();
            double pz = player.getLocation().getZ();

            if (px < regVol.getX1() || px > regVol.getX2() ||
                pz < regVol.getZ1() || pz > regVol.getZ2()) {
                removePlayerDot(minimapId, player.getUniqueId());
                continue;
            }

            double[] projected = ProjectionUtil.project(px, py, pz, mapVol, regVol);
            String key = minimapId + DOT_KEY_SEP + player.getUniqueId();

            if (useHeads) {
                updateHeadDot(key, world, projected, player);
            } else {
                updatePlayerBlockDot(key, world, projected, dotMaterial, scale, player.getUniqueId());
            }
        }
        cleanupOfflinePlayerDots(minimapId, world);
    }

    public void updateMobDots(int minimapId, World world, MinimapVolume mapVol, MinimapVolume regVol) {
        float scale = (float) plugin.getConfigManager().getDotScale();

        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player) continue;
            double px = entity.getLocation().getX();
            double py = entity.getLocation().getY();
            double pz = entity.getLocation().getZ();

            if (!regVol.contains(px, py, pz)) {
                removeMobDot(minimapId, entity.getUniqueId());
                continue;
            }

            double[] projected = ProjectionUtil.project(px, py, pz, mapVol, regVol);
            String key = minimapId + DOT_KEY_SEP + entity.getUniqueId();
            
            Material dotMaterial = getMobMaterial(entity.getType());
            updateMobBlockDot(key, world, projected, dotMaterial, scale);
        }
        cleanupRemovedMobDots(minimapId, world);
    }

    private Material getMobMaterial(EntityType type) {
        return switch (type) {
            case SHULKER -> Material.RED_CONCRETE;
            case CREEPER -> Material.LIME_CONCRETE;
            case ZOMBIE -> Material.GREEN_CONCRETE;
            case SKELETON -> Material.LIGHT_GRAY_CONCRETE;
            case ENDERMAN -> Material.BLACK_CONCRETE;
            case SPIDER, CAVE_SPIDER -> Material.BLACK_CONCRETE;
            case WITCH -> Material.MAGENTA_CONCRETE;
            case SLIME -> Material.LIME_CONCRETE;
            case MAGMA_CUBE -> Material.ORANGE_CONCRETE;
            case BLAZE -> Material.YELLOW_CONCRETE;
            case GHAST -> Material.WHITE_CONCRETE;
            case GUARDIAN, ELDER_GUARDIAN -> Material.CYAN_CONCRETE;
            case PIG -> Material.PINK_CONCRETE;
            case COW -> Material.BROWN_CONCRETE;
            case SHEEP -> Material.WHITE_CONCRETE;
            case CHICKEN -> Material.WHITE_CONCRETE;
            case VILLAGER -> Material.BROWN_CONCRETE;
            default -> Material.LIME_CONCRETE;
        };
    }

    public void updateItemDots(int minimapId, World world, MinimapVolume mapVol,
                               MinimapVolume regVol, ConfigManager cfg) {
        for (Item item : world.getEntitiesByClass(Item.class)) {
            double ix = item.getLocation().getX();
            double iy = item.getLocation().getY();
            double iz = item.getLocation().getZ();

            if (!regVol.contains(ix, iy, iz)) {
                removeItemDot(minimapId, item.getUniqueId());
                continue;
            }

            double[] projected = ProjectionUtil.project(ix, iy, iz, mapVol, regVol);
            String key = minimapId + DOT_KEY_SEP + item.getUniqueId();
            
            updateItemDetailedDisplay(key, world, projected, item.getItemStack());
        }
        cleanupRemovedItemDots(minimapId, world);
    }

    private void updateItemDetailedDisplay(String key, World world, double[] pos, ItemStack stack) {
        UUID existingId = itemDots.get(key);
        float scale = 0.25f; // Actual item display scale
        Location loc = new Location(world, pos[0], pos[1] + DOT_Y_OFFSET, pos[2]);
        loc.setYaw((flashTickCounter * 3) % 360); // Rotate item

        if (existingId != null) {
            Entity ent = world.getEntity(existingId);
            if (ent instanceof ItemDisplay id) {
                id.teleport(loc);
                return;
            }
            removeManagedEntity(existingId);
            itemDots.remove(key);
        }

        ItemDisplay dot = world.spawn(loc, ItemDisplay.class, id -> {
            id.setItemStack(stack);
            id.setTransformation(DisplayUtil.buildTransformation(scale));
            applyCommonDisplaySettings(id);
            id.setPersistent(false);
        });
        itemDots.put(key, dot.getUniqueId());
        managedEntities.add(dot.getUniqueId());
    }

    private void updateMobBlockDot(String key, World world,
                                   double[] pos, Material material, float scale) {
        UUID existingId = mobDots.get(key);
        float offset = scale / 2f;
        Location loc = new Location(world, pos[0] - offset, pos[1] - offset + DOT_Y_OFFSET, pos[2] - offset);

        if (existingId != null) {
            Entity ent = world.getEntity(existingId);
            if (ent instanceof BlockDisplay bd) {
                bd.teleport(loc);
                return;
            }
            removeManagedEntity(existingId);
            mobDots.remove(key);
        }

        BlockDisplay dot = world.spawn(loc, BlockDisplay.class, bd -> {
            bd.setTransformation(DisplayUtil.buildTransformation(scale));
            bd.setBlock(material.createBlockData());
            applyCommonDisplaySettings(bd);
            bd.setPersistent(false);
        });
        mobDots.put(key, dot.getUniqueId());
        managedEntities.add(dot.getUniqueId());
    }

    private float computeItemScale(int stackSize) {
        double minScale = 0.06;
        double maxScale = 0.14;
        if (stackSize <= 1) return (float) minScale;
        double logScale = Math.log(stackSize) / Math.log(64);
        return (float) (minScale + (maxScale - minScale) * Math.min(1.0, logScale));
    }

    private void updatePlayerBlockDot(String key, World world,
                                      double[] pos, Material material, float scale, UUID playerUuid) {
        UUID existingId = playerDots.get(key);
        // Offset by half scale to center the block on the coordinate
        float offset = scale / 2f;
        Location loc = new Location(world, pos[0] - offset, pos[1] - offset + DOT_Y_OFFSET, pos[2] - offset);

        if (existingId != null) {
            Entity ent = world.getEntity(existingId);
            if (ent instanceof BlockDisplay bd) {
                bd.teleport(loc);
                for (Entity nearby : bd.getNearbyEntities(0.1, 0.1, 0.1)) {
                    if (nearby instanceof Interaction) nearby.teleport(loc.clone().add(offset, offset, offset));
                }
                return;
            }
            removeManagedEntity(existingId);
            playerDots.remove(key);
        }

        BlockDisplay dot = world.spawn(loc, BlockDisplay.class, bd -> {
            bd.setTransformation(DisplayUtil.buildTransformation(scale));
            bd.setBlock(material.createBlockData());
            applyCommonDisplaySettings(bd);
            bd.setPersistent(false);
        });
        
        // Interaction should stay centered on the point
        Interaction interaction = world.spawn(loc.clone().add(offset, offset, offset), Interaction.class, i -> {
            i.setInteractionWidth(0.5f);
            i.setInteractionHeight(0.5f);
            i.setPersistent(false);
        });
        interactionToPlayer.put(interaction.getUniqueId(), playerUuid);
        managedEntities.add(interaction.getUniqueId());

        playerDots.put(key, dot.getUniqueId());
        managedEntities.add(dot.getUniqueId());
    }

    private void updateItemBlockDot(String key, World world,
                                    double[] pos, Material material, float scale) {
        UUID existingId = itemDots.get(key);
        float offset = scale / 2f;
        Location loc = new Location(world, pos[0] - offset, pos[1] - offset + DOT_Y_OFFSET, pos[2] - offset);

        if (existingId != null) {
            Entity ent = world.getEntity(existingId);
            if (ent instanceof BlockDisplay bd) {
                bd.teleport(loc);
                bd.setBlock(material.createBlockData());
                return;
            }
            removeManagedEntity(existingId);
            itemDots.remove(key);
        }

        BlockDisplay dot = world.spawn(loc, BlockDisplay.class, bd -> {
            bd.setTransformation(DisplayUtil.buildTransformation(scale));
            bd.setBlock(material.createBlockData());
            applyCommonDisplaySettings(bd);
            bd.setPersistent(false);
        });
        itemDots.put(key, dot.getUniqueId());
        managedEntities.add(dot.getUniqueId());
    }

    private void updateHeadDot(String key, World world,
                               double[] pos, Player player) {
        float scale = (float) plugin.getConfigManager().getDotScale();
        float yaw   = player.getLocation().getYaw();
        UUID existingId = playerDots.get(key);

        if (existingId != null) {
            Entity ent = world.getEntity(existingId);
            if (ent instanceof ItemDisplay id) {
                Location newLoc = new Location(world, pos[0], pos[1] + DOT_Y_OFFSET, pos[2], yaw, 0);
                id.teleport(newLoc);
                ItemStack skull = buildSkull(player);
                id.setItemStack(skull);
                return;
            }
            removeManagedEntity(existingId);
            playerDots.remove(key);
        }

        Location loc = new Location(world, pos[0], pos[1] + DOT_Y_OFFSET, pos[2], yaw, 0);
        ItemStack skull = buildSkull(player);
        ItemDisplay dot = world.spawn(loc, ItemDisplay.class, id -> {
            id.setTransformation(DisplayUtil.buildTransformation(scale * 2));
            id.setItemStack(skull);
            applyCommonDisplaySettings(id);
            id.setPersistent(false);
        });
        playerDots.put(key, dot.getUniqueId());
        managedEntities.add(dot.getUniqueId());
    }

    private ItemStack buildSkull(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private void removeMobDot(int minimapId, UUID mobUuid) {
        String key = minimapId + DOT_KEY_SEP + mobUuid;
        UUID dotId = mobDots.remove(key);
        if (dotId != null) {
            removeManagedEntity(dotId);
        }
    }

    private void cleanupRemovedMobDots(int minimapId, World world) {
        String prefix = minimapId + DOT_KEY_SEP;
        for (Map.Entry<String, UUID> entry : new HashMap<>(mobDots).entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            String uuidStr = entry.getKey().substring(prefix.length());
            try {
                UUID uuid = UUID.fromString(uuidStr);
                if (world.getEntity(uuid) == null) {
                    removeManagedEntity(entry.getValue());
                    mobDots.remove(entry.getKey());
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void removePlayerDot(int minimapId, UUID playerUuid) {
        String key = minimapId + DOT_KEY_SEP + playerUuid;
        UUID dotId = playerDots.remove(key);
        if (dotId != null) {
            removeManagedEntity(dotId);
        }
    }

    private void removeItemDot(int minimapId, UUID itemUuid) {
        String key = minimapId + DOT_KEY_SEP + itemUuid;
        UUID dotId = itemDots.remove(key);
        if (dotId != null) {
            removeManagedEntity(dotId);
        }
    }

    private void cleanupOfflinePlayerDots(int minimapId, World world) {
        String prefix = minimapId + DOT_KEY_SEP;
        for (Map.Entry<String, UUID> entry : new HashMap<>(playerDots).entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            String uuidStr = entry.getKey().substring(prefix.length());
            try {
                UUID uuid = UUID.fromString(uuidStr);
                if (plugin.getServer().getPlayer(uuid) == null) {
                    removeManagedEntity(entry.getValue());
                    playerDots.remove(entry.getKey());
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void cleanupRemovedItemDots(int minimapId, World world) {
        String prefix = minimapId + DOT_KEY_SEP;
        for (Map.Entry<String, UUID> entry : new HashMap<>(itemDots).entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            String uuidStr = entry.getKey().substring(prefix.length());
            try {
                UUID uuid = UUID.fromString(uuidStr);
                if (world.getEntity(uuid) == null) {
                    removeManagedEntity(entry.getValue());
                    itemDots.remove(entry.getKey());
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void despawnAllForMinimap(int minimapId) {
        String prefix = minimapId + DOT_KEY_SEP;
        
        // Clear Player Dots
        for (Map.Entry<String, UUID> entry : new HashMap<>(playerDots).entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                removeManagedEntity(entry.getValue());
                playerDots.remove(entry.getKey());
            }
        }
        
        // Clear Mob Dots
        for (Map.Entry<String, UUID> entry : new HashMap<>(mobDots).entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                removeManagedEntity(entry.getValue());
                mobDots.remove(entry.getKey());
            }
        }

        // Clear Item Dots
        for (Map.Entry<String, UUID> entry : new HashMap<>(itemDots).entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                removeManagedEntity(entry.getValue());
                itemDots.remove(entry.getKey());
            }
        }

        // Clear Terrain Dots
        for (Map.Entry<String, UUID> entry : new HashMap<>(terrainDots).entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                removeManagedEntity(entry.getValue());
                terrainDots.remove(entry.getKey());
            }
        }

        // Clear Aesthetic Structures (Frames, etc.)
        clearAesthetics(minimapId);

        // Clear Anchor
        UUID anchorId = anchors.remove(minimapId);
        if (anchorId != null) {
            removeManagedEntity(anchorId);
        }
    }

    public void despawnAll() {
        for (UUID id : new HashSet<>(managedEntities)) {
            for (World w : plugin.getServer().getWorlds()) {
                Entity ent = w.getEntity(id);
                if (ent != null) {
                    ent.remove();
                }
            }
        }
        managedEntities.clear();
        playerDots.clear();
        itemDots.clear();
        terrainDots.clear();
        anchors.clear();
    }

    public void renderTerrainForMinimap(int minimapId, World world, MinimapRecord rec, ConfigManager cfg) {
        if (!cfg.isTerrainRender()) {
            clearTerrainForMinimap(minimapId);
            return;
        }
        clearTerrainForMinimap(minimapId);

        int samples = Math.max(2, Math.min(64, cfg.getTerrainSamplesPerAxis()));
        MinimapVolume regVol = new MinimapVolume(
                rec.getRegX1(), rec.getRegY1(), rec.getRegZ1(),
                rec.getRegX2(), rec.getRegY2(), rec.getRegZ2()
        );

        double stepX = (samples == 1) ? 0 : regVol.sizeX() / (samples - 1);
        double stepZ = (samples == 1) ? 0 : regVol.sizeZ() / (samples - 1);

        for (int xi = 0; xi < samples; xi++) {
            for (int zi = 0; zi < samples; zi++) {
                int rx = (int) Math.round(regVol.getX1() + stepX * xi);
                int rz = (int) Math.round(regVol.getZ1() + stepZ * zi);
                updateTerrainColumnAtIndices(minimapId, world, rec, xi, zi, rx, rz);
            }
        }
    }

    public void updateTerrainColumn(int minimapId, World world, MinimapRecord rec, int bx, int bz) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isTerrainRender()) return;

        int samples = Math.max(2, Math.min(64, cfg.getTerrainSamplesPerAxis()));
        MinimapVolume regVol = new MinimapVolume(
                rec.getRegX1(), rec.getRegY1(), rec.getRegZ1(),
                rec.getRegX2(), rec.getRegY2(), rec.getRegZ2()
        );

        double stepX = (samples == 1) ? 0 : regVol.sizeX() / (samples - 1);
        double stepZ = (samples == 1) ? 0 : regVol.sizeZ() / (samples - 1);

        int xi = (stepX == 0) ? 0 : (int) Math.round((bx - regVol.getX1()) / stepX);
        int zi = (stepZ == 0) ? 0 : (int) Math.round((bz - regVol.getZ1()) / stepZ);

        if (xi < 0 || xi >= samples || zi < 0 || zi >= samples) return;

        int rx = (int) Math.round(regVol.getX1() + stepX * xi);
        int rz = (int) Math.round(regVol.getZ1() + stepZ * zi);

        if (Math.abs(rx - bx) <= 1 && Math.abs(rz - bz) <= 1) {
            updateTerrainColumnAtIndices(minimapId, world, rec, xi, zi, rx, rz);
        }
    }

    private void updateTerrainColumnAtIndices(int minimapId, World world, MinimapRecord rec, int xi, int zi, int rx, int rz) {
        ConfigManager cfg = plugin.getConfigManager();
        float scale = (float) Math.max(0.02, cfg.getTerrainDotScale());

        MinimapVolume mapVol = new MinimapVolume(
                rec.getMapX1(), rec.getMapY1(), rec.getMapZ1(),
                rec.getMapX2(), rec.getMapY2(), rec.getMapZ2()
        );
        MinimapVolume regVol = new MinimapVolume(
                rec.getRegX1(), rec.getRegY1(), rec.getRegZ1(),
                rec.getRegX2(), rec.getRegY2(), rec.getRegZ2()
        );

        int startY = (int) Math.floor(regVol.getY2());
        int endY = (int) Math.floor(regVol.getY1());
        Block top = null;

        for (int y = startY; y >= endY; y--) {
            Block b = world.getBlockAt(rx, y, rz);
            if (!b.getType().isAir() && b.getType().isSolid()) {
                top = b;
                break;
            }
        }

        String key = minimapId + DOT_KEY_SEP + "terrain:" + xi + ":" + zi;
        UUID existingId = terrainDots.get(key);

        if (top == null) {
            if (existingId != null) {
                removeManagedEntity(existingId);
                terrainDots.remove(key);
            }
            return;
        }

        double ry = top.getY();
        Material mat = top.getType();
        double[] projected = ProjectionUtil.project(rx, ry, rz, mapVol, regVol);

        if (existingId != null) {
            Entity ent = world.getEntity(existingId);
            if (ent instanceof BlockDisplay bd) {
                bd.teleport(new Location(world, projected[0], projected[1], projected[2]));
                bd.setBlock(mat.createBlockData());
                return;
            }
            removeManagedEntity(existingId);
        }

        Location loc = new Location(world, projected[0], projected[1], projected[2]);
        BlockDisplay terrain = world.spawn(loc, BlockDisplay.class, bd -> {
            bd.setTransformation(DisplayUtil.buildTransformation(scale));
            bd.setBlock(mat.createBlockData());
            applyCommonDisplaySettings(bd);
            bd.setPersistent(false);
        });

        terrainDots.put(key, terrain.getUniqueId());
        managedEntities.add(terrain.getUniqueId());
    }

    private void clearTerrainForMinimap(int minimapId) {
        String prefix = minimapId + DOT_KEY_SEP + "terrain:";
        for (Map.Entry<String, UUID> entry : new HashMap<>(terrainDots).entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                removeManagedEntity(entry.getValue());
                terrainDots.remove(entry.getKey());
            }
        }
    }

    private void removeManagedEntity(UUID uuid) {
        managedEntities.remove(uuid);
        for (World w : plugin.getServer().getWorlds()) {
            Entity ent = w.getEntity(uuid);
            if (ent != null) {
                ent.remove();
                return;
            }
        }
    }

    public void incrementFlashCounter() {
        flashTickCounter = (flashTickCounter + 1) % 72000;
    }

    public int getActiveDotCount(int minimapId) {
        String prefix = minimapId + DOT_KEY_SEP;
        int count = 0;
        for (String key : playerDots.keySet()) {
            if (key.startsWith(prefix)) count++;
        }
        for (String key : itemDots.keySet()) {
            if (key.startsWith(prefix)) count++;
        }
        return count;
    }

    private void applyCommonDisplaySettings(Display display) {
        display.setBrightness(new Brightness(15, 15));
        display.setViewRange(128f);
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        display.setTeleportDuration(1);
        display.setBillboard(Billboard.FIXED);
    }

    private Material colorToConcrete(String colorHex) {
        if (colorHex == null) return Material.RED_CONCRETE;
        String hex = colorHex.trim().replace("#", "");
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0)
                    + hex.charAt(1) + hex.charAt(1)
                    + hex.charAt(2) + hex.charAt(2);
        }

        int r, g, b;
        try {
            int rgb = Integer.parseInt(hex, 16);
            r = (rgb >> 16) & 0xFF;
            g = (rgb >> 8)  & 0xFF;
            b =  rgb        & 0xFF;
        } catch (NumberFormatException e) {
            return Material.RED_CONCRETE;
        }

        Material best = Material.RED_CONCRETE;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < CONCRETE_MAT.size(); i++) {
            int[] c = CONCRETE_RGB.get(i);
            double dist = Math.pow(r - c[0], 2) + Math.pow(g - c[1], 2) + Math.pow(b - c[2], 2);
            if (dist < bestDist) {
                bestDist = dist;
                best = CONCRETE_MAT.get(i);
            }
        }
        return best;
    }
}
