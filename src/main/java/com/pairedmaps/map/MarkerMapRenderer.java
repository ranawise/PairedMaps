package com.pairedmaps.map;

import com.pairedmaps.database.MinimapRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

public class MarkerMapRenderer extends MapRenderer {

    private final MinimapRecord record;
    private final byte[][] terrainCache = new byte[128][128];
    private boolean terrainGenerated = false;

    public MarkerMapRenderer(MinimapRecord record) {
        super(false);
        this.record = record;
    }

    @Override
    public void render(@NotNull MapView view, @NotNull MapCanvas canvas, @NotNull Player player) {
        World world = Bukkit.getWorld(record.getWorld());
        if (world == null) return;

        double x1 = Math.min(record.getRegX1(), record.getRegX2());
        double z1 = Math.min(record.getRegZ1(), record.getRegZ2());
        double x2 = Math.max(record.getRegX1(), record.getRegX2());
        double z2 = Math.max(record.getRegZ1(), record.getRegZ2());
        double width = x2 - x1;
        double height = z2 - z1;

        if (width <= 0 || height <= 0) return;

        if (!terrainGenerated) {
            generateTerrain(world, x1, z1, width, height);
        }

        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                canvas.setPixel(x, z, terrainCache[x][z]);
            }
        }

        for (Player p : world.getPlayers()) {
            Location loc = p.getLocation();
            if (isInRegion(loc.getX(), loc.getZ(), x1, z1, x2, z2)) {
                drawHead(canvas, loc.getX(), loc.getZ(), x1, z1, width, height, "PLAYER");
            }
        }

        for (Entity entity : world.getNearbyEntities(new Location(world, (x1+x2)/2, 64, (z1+z2)/2), width, 256, height)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                Location loc = entity.getLocation();
                if (isInRegion(loc.getX(), loc.getZ(), x1, z1, x2, z2)) {
                    drawHead(canvas, loc.getX(), loc.getZ(), x1, z1, width, height, entity.getType().name());
                }
            }
        }
    }

    private void generateTerrain(World world, double x1, double z1, double width, double height) {
        int lastY = -1;
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                int worldX = (int) (x1 + (x / 128.0) * width);
                int worldZ = (int) (z1 + (z / 128.0) * height);
                Block top = world.getHighestBlockAt(worldX, worldZ);
                int currentY = top.getY();
                java.awt.Color baseColor = getDetailedBlockColor(top.getType());
                if (lastY != -1) {
                    if (currentY > lastY) baseColor = baseColor.brighter();
                    else if (currentY < lastY) baseColor = baseColor.darker();
                }
                terrainCache[x][z] = MapPalette.matchColor(baseColor);
                lastY = currentY;
            }
        }
        terrainGenerated = true;
    }

    @SuppressWarnings("deprecation")
    private void drawHead(MapCanvas canvas, double wx, double wz, double x1, double z1, double w, double h, String type) {
        int mx = (int) (((wx - x1) / w) * 128);
        int mz = (int) (((wz - z1) / h) * 128);

        byte color;
        byte detailColor;

        switch (type) {
            case "PLAYER" -> { 
                color = MapPalette.matchColor(new java.awt.Color(255, 200, 150)); 
                detailColor = MapPalette.matchColor(new java.awt.Color(100, 50, 0)); 
            }
            case "CREEPER" -> { 
                color = MapPalette.matchColor(new java.awt.Color(0, 255, 0)); 
                detailColor = MapPalette.matchColor(new java.awt.Color(0, 0, 0)); 
            }
            case "ZOMBIE" -> { 
                color = MapPalette.matchColor(new java.awt.Color(0, 100, 0)); 
                detailColor = MapPalette.matchColor(new java.awt.Color(0, 0, 0)); 
            }
            case "SKELETON" -> { 
                color = MapPalette.matchColor(new java.awt.Color(200, 200, 200)); 
                detailColor = MapPalette.matchColor(new java.awt.Color(0, 0, 0)); 
            }
            case "SPIDER" -> { 
                color = MapPalette.matchColor(new java.awt.Color(50, 50, 50)); 
                detailColor = MapPalette.matchColor(new java.awt.Color(255, 0, 0)); 
            }
            case "ENDERMAN" -> { 
                color = MapPalette.matchColor(new java.awt.Color(0, 0, 0)); 
                detailColor = MapPalette.matchColor(new java.awt.Color(255, 0, 255)); 
            }
            default -> { 
                color = MapPalette.matchColor(new java.awt.Color(255, 0, 0)); 
                detailColor = MapPalette.matchColor(new java.awt.Color(255, 255, 255)); 
            }
        }

        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                if (mx + dx >= 0 && mx + dx < 128 && mz + dz >= 0 && mz + dz < 128) {
                    canvas.setPixel(mx + dx, mz + dz, color);
                }
            }
        }
        if (mx >= 0 && mx < 128 && mz >= 0 && mz < 128) {
            canvas.setPixel(mx, mz, detailColor);
            if (mx + 1 < 128) canvas.setPixel(mx + 1, mz, detailColor);
        }
    }

    private java.awt.Color getDetailedBlockColor(Material mat) {
        String n = mat.name();
        
        // Grass & Plants
        if (n.contains("GRASS_BLOCK") || n.contains("GRASS") || n.contains("MOSS_BLOCK")) return new java.awt.Color(103, 137, 51);
        if (n.contains("LEAVES") || n.contains("FLOWER") || n.contains("SAPLING") || n.contains("BUSH")) return new java.awt.Color(40, 80, 40);
        if (n.contains("CROP") || n.contains("WHEAT") || n.contains("CARROT") || n.contains("POTATO")) return new java.awt.Color(150, 150, 0);

        // Stone & Earth
        if (n.contains("STONE") || n.contains("ANDESITE") || n.contains("DIORITE") || n.contains("POLISHED")) return new java.awt.Color(125, 125, 125);
        if (n.contains("DEEPSLATE") || n.contains("TUFF")) return new java.awt.Color(77, 77, 77);
        if (n.contains("COBBLESTONE") || n.contains("GRAVEL")) return new java.awt.Color(115, 115, 115);
        if (n.contains("DIRT") || n.contains("COARSE_DIRT") || n.contains("ROOTED_DIRT")) return new java.awt.Color(134, 96, 67);
        if (n.contains("CLAY") || n.contains("MUD")) return new java.awt.Color(158, 164, 176);
        if (n.contains("SANDSTONE")) return new java.awt.Color(216, 203, 155);
        if (n.contains("SAND")) return new java.awt.Color(219, 211, 160);

        // Water & Liquids
        if (n.contains("WATER") || n.contains("ICE")) return new java.awt.Color(64, 64, 255);
        if (n.contains("LAVA")) return new java.awt.Color(255, 100, 0);
        if (n.contains("SNOW") || n.contains("POWDER_SNOW")) return new java.awt.Color(255, 255, 255);

        // Wood Types
        if (n.contains("OAK") || n.contains("WOOD") || n.contains("PLANKS")) return new java.awt.Color(102, 81, 51);
        if (n.contains("SPRUCE") || n.contains("DARK_OAK")) return new java.awt.Color(60, 46, 32);
        if (n.contains("BIRCH")) return new java.awt.Color(196, 178, 131);
        if (n.contains("JUNGLE") || n.contains("ACACIA")) return new java.awt.Color(170, 116, 80);
        if (n.contains("CHERRY")) return new java.awt.Color(255, 180, 200);

        // Nether & End
        if (n.contains("NETHERRACK") || n.contains("CRIMSON") || n.contains("WART")) return new java.awt.Color(111, 54, 52);
        if (n.contains("SOUL") || n.contains("WARPED")) return new java.awt.Color(70, 50, 40);
        if (n.contains("BASALT") || n.contains("BLACKSTONE")) return new java.awt.Color(35, 35, 35);
        if (n.contains("END_STONE") || n.contains("PURPUR")) return new java.awt.Color(220, 220, 180);

        // Ores
        if (n.contains("COAL_ORE")) return new java.awt.Color(40, 40, 40);
        if (n.contains("IRON_ORE") || n.contains("RAW_IRON")) return new java.awt.Color(215, 185, 155);
        if (n.contains("GOLD_ORE") || n.contains("RAW_GOLD")) return new java.awt.Color(255, 215, 0);
        if (n.contains("DIAMOND_ORE")) return new java.awt.Color(100, 220, 220);
        if (n.contains("EMERALD_ORE")) return new java.awt.Color(20, 200, 20);
        if (n.contains("LAPIS_ORE")) return new java.awt.Color(20, 40, 200);
        if (n.contains("REDSTONE_ORE")) return new java.awt.Color(200, 0, 0);

        // Man-made Materials (Colored)
        if (n.contains("WHITE")) return new java.awt.Color(230, 230, 230);
        if (n.contains("ORANGE")) return new java.awt.Color(240, 120, 20);
        if (n.contains("MAGENTA")) return new java.awt.Color(180, 70, 180);
        if (n.contains("LIGHT_BLUE")) return new java.awt.Color(100, 150, 220);
        if (n.contains("YELLOW")) return new java.awt.Color(240, 220, 40);
        if (n.contains("LIME")) return new java.awt.Color(120, 200, 30);
        if (n.contains("PINK")) return new java.awt.Color(240, 150, 170);
        if (n.contains("GRAY") || n.contains("GREY")) return new java.awt.Color(60, 60, 60);
        if (n.contains("CYAN")) return new java.awt.Color(20, 130, 150);
        if (n.contains("PURPLE")) return new java.awt.Color(120, 40, 170);
        if (n.contains("BLUE")) return new java.awt.Color(50, 50, 180);
        if (n.contains("BROWN")) return new java.awt.Color(100, 60, 30);
        if (n.contains("GREEN")) return new java.awt.Color(60, 90, 30);
        if (n.contains("RED")) return new java.awt.Color(160, 40, 40);
        if (n.contains("BLACK")) return new java.awt.Color(20, 20, 20);

        return new java.awt.Color(100, 100, 100);
    }

    private boolean isInRegion(double x, double z, double x1, double z1, double x2, double z2) {
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }
}
