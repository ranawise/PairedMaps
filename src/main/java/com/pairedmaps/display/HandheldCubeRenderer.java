package com.pairedmaps.display;

import com.pairedmaps.PairedMapsPlugin;
import com.pairedmaps.database.MinimapRecord;
import com.pairedmaps.minimap.MinimapVolume;
import com.pairedmaps.minimap.ProjectionUtil;
import com.pairedmaps.util.DisplayUtil;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HandheldCubeRenderer {

    private final PairedMapsPlugin plugin;
    private final Map<UUID, Map<String, UUID>> playerEntityMap = new ConcurrentHashMap<>();

    private static final double CUBE_SIZE = 0.7;
    private static final double FORWARD_OFFSET = 1.8;
    private static final double UP_OFFSET = 0.5;

    public HandheldCubeRenderer(PairedMapsPlugin plugin) {
        this.plugin = plugin;
    }

    public void updateCubeForPlayer(Player player, MinimapRecord rec) {
        UUID playerId = player.getUniqueId();
        playerEntityMap.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        Map<String, UUID> entityMap = playerEntityMap.get(playerId);

        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Vector right = forward.crossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = right.crossProduct(forward).normalize();

        Location cubeOrigin = eye.clone().add(forward.clone().multiply(FORWARD_OFFSET))
                .add(up.clone().multiply(UP_OFFSET))
                .subtract(right.clone().multiply(CUBE_SIZE / 2))
                .subtract(up.clone().multiply(CUBE_SIZE / 2));

        World world = player.getWorld();

        clearStructureOnly(player, entityMap);
        spawnHandheldCubeStructure(world, cubeOrigin, entityMap, right, up);

        MinimapVolume regVol = new MinimapVolume(
                rec.getRegX1(), rec.getRegY1(), rec.getRegZ1(),
                rec.getRegX2(), rec.getRegY2(), rec.getRegZ2()
        );
        MinimapVolume mapVol = new MinimapVolume(0, 0, 0, CUBE_SIZE, CUBE_SIZE, CUBE_SIZE);

        Set<String> activeKeys = new HashSet<>();

        for (Player p : world.getPlayers()) {
            if (!regVol.contains(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ())) continue;
            String key = "p:" + p.getUniqueId();
            activeKeys.add(key);
            
            double[] proj = ProjectionUtil.project(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(), mapVol, regVol);
            Location dotLoc = cubeOrigin.clone()
                    .add(right.clone().multiply(proj[0]))
                    .add(up.clone().multiply(proj[1]))
                    .add(forward.clone().multiply(proj[2] * 0.3));

            Material dotMat = p.equals(player) ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
            updateDot(world, entityMap, key, dotLoc, dotMat, 0.06f);
        }

        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player) continue;
            if (!regVol.contains(entity.getLocation().getX(), entity.getLocation().getY(), entity.getLocation().getZ())) continue;
            String key = "m:" + entity.getUniqueId();
            activeKeys.add(key);

            double[] proj = ProjectionUtil.project(entity.getLocation().getX(), entity.getLocation().getY(), entity.getLocation().getZ(), mapVol, regVol);
            Location dotLoc = cubeOrigin.clone()
                    .add(right.clone().multiply(proj[0]))
                    .add(up.clone().multiply(proj[1]))
                    .add(forward.clone().multiply(proj[2] * 0.3));

            Material mobMat = (entity.getType() == EntityType.SHULKER) ? Material.RED_CONCRETE : Material.RED_CONCRETE;
            updateDot(world, entityMap, key, dotLoc, mobMat, 0.05f);
        }

        entityMap.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith("struct:") && !activeKeys.contains(entry.getKey())) {
                Entity e = world.getEntity(entry.getValue());
                if (e != null) e.remove();
                return true;
            }
            return false;
        });
    }

    private void updateDot(World world, Map<String, UUID> entityMap, String key, Location loc, Material mat, float scale) {
        UUID existingId = entityMap.get(key);
        if (existingId != null) {
            Entity e = world.getEntity(existingId);
            if (e instanceof BlockDisplay bd) {
                bd.teleport(loc);
                return;
            }
        }

        BlockDisplay bd = world.spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(mat.createBlockData());
            d.setTransformation(DisplayUtil.buildTransformation(scale));
            d.setBrightness(new Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setPersistent(false);
        });
        entityMap.put(key, bd.getUniqueId());
    }

    private void spawnHandheldCubeStructure(World world, Location origin, Map<String, UUID> entityMap,
                                            Vector right, Vector up) {
        float sz = (float) CUBE_SIZE;
        float wT = 0.015f;
        float cS = 0.08f;

        spawnBlockAligned(world, entityMap, "struct:b1", origin, right, up, sz, wT, wT, Material.CYAN_CONCRETE);
        spawnBlockAligned(world, entityMap, "struct:b2", origin.clone().add(up.clone().multiply(sz)), right, up, sz, wT, wT, Material.CYAN_CONCRETE);
        spawnBlockAligned(world, entityMap, "struct:b3", origin.clone().add(right.clone().multiply(0).add(up.clone().multiply(0))), right, up, wT, sz, wT, Material.CYAN_CONCRETE);
        spawnBlockAligned(world, entityMap, "struct:b4", origin.clone().add(right.clone().multiply(sz)), right, up, wT, sz, wT, Material.CYAN_CONCRETE);

        Location[] corners = {
                origin.clone(),
                origin.clone().add(right.clone().multiply(sz)),
                origin.clone().add(up.clone().multiply(sz)),
                origin.clone().add(right.clone().multiply(sz)).add(up.clone().multiply(sz))
        };

        int cIdx = 0;
        for (Location corner : corners) {
            Location cornerOffset = corner.clone().subtract(right.clone().multiply(cS / 2)).subtract(up.clone().multiply(cS / 2));
            spawnBlockAligned(world, entityMap, "struct:c" + (cIdx++), cornerOffset, right, up, cS, cS, cS, Material.POLISHED_DEEPSLATE);
        }

        Location glassOrigin = origin.clone();
        spawnBlockAligned(world, entityMap, "struct:glass", glassOrigin, right, up, sz, sz, (float)(sz * 0.1), Material.CYAN_STAINED_GLASS);
    }

    private void spawnBlockAligned(World world, Map<String, UUID> entityMap, String key, Location loc,
                                   Vector right, Vector up, float sx, float sy, float sz, Material mat) {
        BlockDisplay bd = world.spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(mat.createBlockData());
            d.setTransformation(DisplayUtil.buildTransformation(sx, sy, sz));
            d.setBrightness(new Brightness(15, 15));
            d.setPersistent(false);
        });
        entityMap.put(key, bd.getUniqueId());
    }

    private void clearStructureOnly(Player player, Map<String, UUID> entityMap) {
        entityMap.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith("struct:")) {
                Entity e = player.getWorld().getEntity(entry.getValue());
                if (e != null) e.remove();
                return true;
            }
            return false;
        });
    }

    public void clearCubeForPlayer(Player player) {
        Map<String, UUID> entityMap = playerEntityMap.remove(player.getUniqueId());
        if (entityMap == null) return;
        for (UUID id : entityMap.values()) {
            Entity e = player.getWorld().getEntity(id);
            if (e != null) e.remove();
        }
    }

    public void clearAll() {
        for (UUID playerId : new HashSet<>(playerEntityMap.keySet())) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) clearCubeForPlayer(p);
            else playerEntityMap.remove(playerId);
        }
    }

    public boolean hasCube(Player player) {
        return playerEntityMap.containsKey(player.getUniqueId());
    }
}
