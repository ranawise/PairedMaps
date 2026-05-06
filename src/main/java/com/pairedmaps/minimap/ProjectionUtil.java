package com.pairedmaps.minimap;

public final class ProjectionUtil {

    public enum AspectRatioError {
        NONE,
        NON_3D_VOLUME
    }

    public record AspectRatioValidationResult(boolean valid, AspectRatioError error) {
    }

    public static AspectRatioValidationResult validateAspectRatioDetailed(
            MinimapVolume map, MinimapVolume region, double epsilon) {
        return new AspectRatioValidationResult(true, AspectRatioError.NONE);
    }

    public static boolean validateAspectRatio(MinimapVolume map, MinimapVolume region, double epsilon) {
        return validateAspectRatioDetailed(map, region, epsilon).valid();
    }

    public static double[] project(double rx, double ry, double rz,
            MinimapVolume map, MinimapVolume region) {
        double regSx = region.sizeX();
        double regSy = region.sizeY();
        double regSz = region.sizeZ();

        if (regSx == 0)
            regSx = 1;
        if (regSy == 0)
            regSy = 1;
        if (regSz == 0)
            regSz = 1;

        double mx = map.getX1() + ((rx - region.getX1()) / regSx) * map.sizeX();
        double my = map.getY1() + ((ry - region.getY1()) / regSy) * map.sizeY();
        double mz = map.getZ1() + ((rz - region.getZ1()) / regSz) * map.sizeZ();

        return new double[] { mx, my, mz };
    }
}
