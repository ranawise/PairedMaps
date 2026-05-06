package com.pairedmaps.minimap;
public class MinimapVolume {
    private final double x1, y1, z1;
    private final double x2, y2, z2;
    public MinimapVolume(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
    }
    public double getX1() { return x1; }
    public double getY1() { return y1; }
    public double getZ1() { return z1; }
    public double getX2() { return x2; }
    public double getY2() { return y2; }
    public double getZ2() { return z2; }
    public double sizeX() { return x2 - x1; }
    public double sizeY() { return y2 - y1; }
    public double sizeZ() { return z2 - z1; }
    public boolean contains(double x, double y, double z) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }
    public boolean isCube() {
        double sx = sizeX(), sy = sizeY(), sz = sizeZ();
        return approxEqual(sx, sy) && approxEqual(sy, sz);
    }
    private boolean approxEqual(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }
}

