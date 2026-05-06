package com.pairedmaps.util;

import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DisplayUtil {
    private DisplayUtil() {}

    public static Transformation buildTransformation(float scale) {
        return buildTransformation(scale, scale, scale);
    }

    public static Transformation buildTransformation(float sx, float sy, float sz) {
        return new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(new AxisAngle4f(0, 0, 0, 1)),
                new Vector3f(sx, sy, sz),
                new Quaternionf(new AxisAngle4f(0, 0, 0, 1))
        );
    }
}
