package com.pairedmaps.hook;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.World;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
public final class WorldGuardHook {
    private WorldGuardHook() {}
    public static Optional<RegionBounds> getRegionBounds(World world, String regionName) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
            if (manager == null) return Optional.empty();
            ProtectedRegion region = manager.getRegion(regionName);
            if (region == null) return Optional.empty();
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            return Optional.of(new RegionBounds(min.x(), min.y(), min.z(), max.x(), max.y(), max.z()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    public static Set<String> getRegionNames(World world) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
            if (manager == null) return Collections.emptySet();
            return manager.getRegions().keySet().stream()
                    .filter(id -> !id.equals("__global__"))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
    public record RegionBounds(int x1, int y1, int z1, int x2, int y2, int z2) {}
}

