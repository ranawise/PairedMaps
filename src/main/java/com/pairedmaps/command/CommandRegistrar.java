package com.pairedmaps.command;

import com.pairedmaps.PairedMapsPlugin;
import com.pairedmaps.config.MessageManager;
import com.pairedmaps.database.MinimapRecord;
import com.pairedmaps.hook.WorldGuardHook;
import com.pairedmaps.minimap.MinimapManager;
import com.pairedmaps.minimap.MinimapVolume;
import com.pairedmaps.minimap.ProjectionUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CommandRegistrar {

    private final PairedMapsPlugin plugin;
    private final MinimapManager minimapManager;
    private final MessageManager messageManager;

    public CommandRegistrar(PairedMapsPlugin plugin, MinimapManager minimapManager,
            MessageManager messageManager) {
        this.plugin = plugin;
        this.minimapManager = minimapManager;
        this.messageManager = messageManager;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("pm")
                        .requires(src -> src.getSender().hasPermission("pairedmaps.admin"))

                        .then(Commands.literal("add")
                                .then(Commands.argument("map_start", ArgumentTypes.blockPosition())
                                        .then(Commands.argument("map_end", ArgumentTypes.blockPosition())

                                                .then(Commands.argument("region_start", ArgumentTypes.blockPosition())
                                                        .then(Commands
                                                                .argument("region_end", ArgumentTypes.blockPosition())
                                                                .executes(this::executeAdd)))

                                                .then(Commands.argument("region_name", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            if (ctx.getSource().getSender() instanceof Player p) {
                                                                WorldGuardHook.getRegionNames(p.getWorld())
                                                                        .forEach(builder::suggest);
                                                            }
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(this::executeAddWG)))))

                        .then(Commands.literal("list")
                                .executes(this::executeList))

                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .suggests((ctx, builder) -> {
                                            minimapManager.getAllMinimaps()
                                                    .forEach(r -> builder.suggest(r.getId()));
                                            return builder.buildFuture();
                                        })
                                        .executes(this::executeRemove)))

                        .then(Commands.literal("reload")
                                .executes(this::executeReload))

                        .then(Commands.literal("reset")
                                .executes(this::executeReset))

                        .then(Commands.literal("info")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .suggests((ctx, builder) -> {
                                            minimapManager.getAllMinimaps()
                                                    .forEach(r -> builder.suggest(r.getId()));
                                            return builder.buildFuture();
                                        })
                                        .executes(this::executeInfo)))
                        .build(),
                "PairedMaps admin commands",
                List.of("pairedmaps")));
    }

    private int executeAdd(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(messageManager.get("player-only"));
            return 0;
        }

        try {
            BlockPosition mapStart = ctx.getArgument("map_start", BlockPositionResolver.class).resolve(ctx.getSource());
            BlockPosition mapEnd = ctx.getArgument("map_end", BlockPositionResolver.class).resolve(ctx.getSource());
            BlockPosition regStart = ctx.getArgument("region_start", BlockPositionResolver.class)
                    .resolve(ctx.getSource());
            BlockPosition regEnd = ctx.getArgument("region_end", BlockPositionResolver.class).resolve(ctx.getSource());

            return createMinimap(player,
                    mapStart.x(), mapStart.y(), mapStart.z(),
                    mapEnd.x(), mapEnd.y(), mapEnd.z(),
                    regStart.x(), regStart.y(), regStart.z(),
                    regEnd.x(), regEnd.y(), regEnd.z());

        } catch (SQLException | CommandSyntaxException e) {
            player.sendMessage(messageManager.get("db-error"));
            e.printStackTrace();
            return 0;
        }
    }

    private int executeAddWG(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(messageManager.get("player-only"));
            return 0;
        }

        try {
            BlockPosition mapStart = ctx.getArgument("map_start", BlockPositionResolver.class).resolve(ctx.getSource());
            BlockPosition mapEnd = ctx.getArgument("map_end", BlockPositionResolver.class).resolve(ctx.getSource());
            String regionName = ctx.getArgument("region_name", String.class);

            var boundsOpt = WorldGuardHook.getRegionBounds(player.getWorld(), regionName);
            if (boundsOpt.isEmpty()) {
                player.sendMessage(messageManager.get("wg-region-not-found", "name", regionName));
                return 0;
            }

            WorldGuardHook.RegionBounds b = boundsOpt.get();
            return createMinimap(player,
                    mapStart.x(), mapStart.y(), mapStart.z(),
                    mapEnd.x(), mapEnd.y(), mapEnd.z(),
                    b.x1(), b.y1(), b.z1(),
                    b.x2(), b.y2(), b.z2());

        } catch (SQLException | CommandSyntaxException e) {
            player.sendMessage(messageManager.get("db-error"));
            e.printStackTrace();
            return 0;
        }
    }

    private int createMinimap(Player player,
            double mapX1, double mapY1, double mapZ1,
            double mapX2, double mapY2, double mapZ2,
            double regX1, double regY1, double regZ1,
            double regX2, double regY2, double regZ2) throws SQLException {
        String world = player.getWorld().getName();

        double regSx = Math.abs(regX2 - regX1);
        double regSy = Math.abs(regY2 - regY1);
        double regSz = Math.abs(regZ2 - regZ1);

        if (regSx == 0 || regSz == 0) {
            player.sendMessage(messageManager.get("aspect-ratio-non-3d"));
            return 0;
        }

        double mapSx = Math.abs(mapX2 - mapX1);
        double mapSz = Math.abs(mapZ2 - mapZ1);

        if (mapSx == 0)
            mapSx = 1;
        if (mapSz == 0)
            mapSz = 1;

        double xScale = mapSx / regSx;
        double correctedMapY2 = mapY1 + (regSy * xScale);

        int id = minimapManager.addMinimap(world,
                mapX1, mapY1, mapZ1, mapX2, correctedMapY2, mapZ2,
                regX1, regY1, regZ1, regX2, regY2, regZ2);

        player.sendMessage(messageManager.get("minimap-added", "id", String.valueOf(id)));
        player.sendMessage("§7[SmartScale] Map height auto-adjusted to match region proportions.");
        return 1;
    }

    private int executeList(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(messageManager.get("player-only"));
            return 0;
        }

        Collection<MinimapRecord> maps = minimapManager.getAllMinimaps();
        if (maps.isEmpty()) {
            player.sendMessage(messageManager.get("minimap-list-empty"));
            return 1;
        }

        player.sendMessage(messageManager.get("minimap-list-header", "count", String.valueOf(maps.size())));

        int size = Math.min(54, Math.max(9, ((maps.size() / 9) + 1) * 9));
        Component guiTitle = messageManager.get("minimap-gui-title");
        Inventory gui = Bukkit.createInventory(null, size, guiTitle);

        NamespacedKey minimapIdKey = new NamespacedKey(plugin, PairedMapsPlugin.PDC_MINIMAP_ID);

        for (MinimapRecord rec : maps) {
            int dots = plugin.getDisplayManager().getActiveDotCount(rec.getId());

            ItemStack item = new ItemStack(Material.MAP);
            ItemMeta meta = item.getItemMeta();

            meta.displayName(messageManager.get("minimap-list-item-name",
                    "id", String.valueOf(rec.getId())));

            List<Component> lore = new ArrayList<>(List.of(
                    messageManager.get("minimap-list-lore-world", "world", rec.getWorld()),
                    messageManager.get("minimap-list-lore-map",
                            "map_x1", String.valueOf((int) rec.getMapX1()),
                            "map_y1", String.valueOf((int) rec.getMapY1()),
                            "map_z1", String.valueOf((int) rec.getMapZ1()),
                            "map_x2", String.valueOf((int) rec.getMapX2()),
                            "map_y2", String.valueOf((int) rec.getMapY2()),
                            "map_z2", String.valueOf((int) rec.getMapZ2())),
                    messageManager.get("minimap-list-lore-region",
                            "reg_x1", String.valueOf((int) rec.getRegX1()),
                            "reg_y1", String.valueOf((int) rec.getRegY1()),
                            "reg_z1", String.valueOf((int) rec.getRegZ1()),
                            "reg_x2", String.valueOf((int) rec.getRegX2()),
                            "reg_y2", String.valueOf((int) rec.getRegY2()),
                            "reg_z2", String.valueOf((int) rec.getRegZ2())),
                    messageManager.get("minimap-list-lore-dots", "dots", String.valueOf(dots))));

            lore.add(Component.text(" "));
            lore.add(Component.text("§e§lLEFT CLICK §7to get map item"));
            lore.add(Component.text("§c§lRIGHT CLICK §7to remove minimap"));

            Component hint = messageManager.get("minimap-list-click-hint");
            meta.lore(lore);

            meta.getPersistentDataContainer()
                    .set(minimapIdKey, PersistentDataType.INTEGER, rec.getId());

            item.setItemMeta(meta);
            gui.addItem(item);
        }

        player.openInventory(gui);
        return 1;
    }

    private int executeRemove(CommandContext<CommandSourceStack> ctx) {
        int id = ctx.getArgument("id", Integer.class);
        try {
            boolean removed = minimapManager.removeMinimap(id);
            if (removed) {
                ctx.getSource().getSender().sendMessage(
                        messageManager.get("minimap-removed", "id", String.valueOf(id)));
                return 1;
            } else {
                ctx.getSource().getSender().sendMessage(
                        messageManager.get("minimap-not-found", "id", String.valueOf(id)));
                return 0;
            }
        } catch (SQLException e) {
            ctx.getSource().getSender().sendMessage(messageManager.get("db-error"));
            e.printStackTrace();
            return 0;
        }
    }

    private int executeReload(CommandContext<CommandSourceStack> ctx) {
        minimapManager.reload();
        plugin.getMessageManager().load();
        ctx.getSource().getSender().sendMessage(messageManager.get("reload-success"));
        return 1;
    }

    private int executeReset(CommandContext<CommandSourceStack> ctx) {
        minimapManager.resetAll();
        ctx.getSource().getSender().sendMessage(messageManager.get("reset-success"));
        return 1;
    }

    private int executeInfo(CommandContext<CommandSourceStack> ctx) {
        int id = ctx.getArgument("id", Integer.class);
        var opt = minimapManager.getMinimap(id);

        if (opt.isEmpty()) {
            ctx.getSource().getSender().sendMessage(
                    messageManager.get("minimap-not-found", "id", String.valueOf(id)));
            return 0;
        }

        MinimapRecord rec = opt.get();
        int dots = plugin.getDisplayManager().getActiveDotCount(id);
        int interval = plugin.getConfigManager().getUpdateIntervalTicks();

        ctx.getSource().getSender().sendMessage(messageManager.get("minimap-info",
                "id", String.valueOf(id),
                "world", rec.getWorld(),
                "map_x1", String.valueOf((int) rec.getMapX1()),
                "map_y1", String.valueOf((int) rec.getMapY1()),
                "map_z1", String.valueOf((int) rec.getMapZ1()),
                "map_x2", String.valueOf((int) rec.getMapX2()),
                "map_y2", String.valueOf((int) rec.getMapY2()),
                "map_z2", String.valueOf((int) rec.getMapZ2()),
                "reg_x1", String.valueOf((int) rec.getRegX1()),
                "reg_y1", String.valueOf((int) rec.getRegY1()),
                "reg_z1", String.valueOf((int) rec.getRegZ1()),
                "reg_x2", String.valueOf((int) rec.getRegX2()),
                "reg_y2", String.valueOf((int) rec.getRegY2()),
                "reg_z2", String.valueOf((int) rec.getRegZ2()),
                "scale_x", String.format("%.2f", rec.mapSizeX() / rec.regSizeX()),
                "scale_y", String.format("%.2f", rec.mapSizeY() / rec.regSizeY()),
                "scale_z", String.format("%.2f", rec.mapSizeZ() / rec.regSizeZ()),
                "entities", String.valueOf(dots),
                "interval", String.valueOf(interval),
                "created", rec.getCreatedAt()));
        return 1;
    }

    private String volumeSizeString(MinimapVolume volume) {
        return (int) volume.sizeX() + "x" + (int) volume.sizeY() + "x" + (int) volume.sizeZ();
    }
}
