package com.pairedmaps.database;
public class MinimapRecord {
    private final int id;
    private final String world;
    private final double mapX1, mapY1, mapZ1;
    private final double mapX2, mapY2, mapZ2;
    private final double regX1, regY1, regZ1;
    private final double regX2, regY2, regZ2;
    private final String createdAt;
    public MinimapRecord(int id, String world,
                         double mapX1, double mapY1, double mapZ1,
                         double mapX2, double mapY2, double mapZ2,
                         double regX1, double regY1, double regZ1,
                         double regX2, double regY2, double regZ2,
                         String createdAt) {
        this.id = id;
        this.world = world;
        this.mapX1 = mapX1; this.mapY1 = mapY1; this.mapZ1 = mapZ1;
        this.mapX2 = mapX2; this.mapY2 = mapY2; this.mapZ2 = mapZ2;
        this.regX1 = regX1; this.regY1 = regY1; this.regZ1 = regZ1;
        this.regX2 = regX2; this.regY2 = regY2; this.regZ2 = regZ2;
        this.createdAt = createdAt;
    }
    public int getId() { return id; }
    public String getWorld() { return world; }
    public double getMapX1() { return mapX1; }
    public double getMapY1() { return mapY1; }
    public double getMapZ1() { return mapZ1; }
    public double getMapX2() { return mapX2; }
    public double getMapY2() { return mapY2; }
    public double getMapZ2() { return mapZ2; }
    public double getRegX1() { return regX1; }
    public double getRegY1() { return regY1; }
    public double getRegZ1() { return regZ1; }
    public double getRegX2() { return regX2; }
    public double getRegY2() { return regY2; }
    public double getRegZ2() { return regZ2; }
    public String getCreatedAt() { return createdAt; }
    public double mapSizeX() { return mapX2 - mapX1; }
    public double mapSizeY() { return mapY2 - mapY1; }
    public double mapSizeZ() { return mapZ2 - mapZ1; }
    public double regSizeX() { return regX2 - regX1; }
    public double regSizeY() { return regY2 - regY1; }
    public double regSizeZ() { return regZ2 - regZ1; }
}

