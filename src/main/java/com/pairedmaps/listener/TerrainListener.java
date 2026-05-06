package com.pairedmaps.listener;

import com.pairedmaps.PairedMapsPlugin;
import com.pairedmaps.minimap.MinimapVolume;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

public class TerrainListener implements Listener {

    private final PairedMapsPlugin plugin;

    public TerrainListener(PairedMapsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handleBlockChange(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        handleBlockChange(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        handleBlockChange(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        handleBlockChange(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        handleBlockChange(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        handleBlockChange(event.getBlock().getLocation());
    }

    private void handleBlockChange(Location loc) {
        if (!plugin.getConfigManager().isTerrainRender()) return;

        double bx = loc.getX();
        double by = loc.getY();
        double bz = loc.getZ();
        String worldName = loc.getWorld().getName();

        for (var rec : plugin.getMinimapManager().getAllMinimaps()) {
            if (!rec.getWorld().equals(worldName)) continue;

            MinimapVolume regVol = new MinimapVolume(
                    rec.getRegX1(), rec.getRegY1(), rec.getRegZ1(),
                    rec.getRegX2(), rec.getRegY2(), rec.getRegZ2());

            if (regVol.contains(bx, by, bz)) {
                plugin.getDisplayManager().updateTerrainColumn(rec.getId(), loc.getWorld(), rec, (int) bx, (int) bz);
            }
        }
    }
}
