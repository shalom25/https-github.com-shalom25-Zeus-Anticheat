package com.anticheat.core.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

public class BlockUtil {

    public static boolean hasSolidBelow(Location location, int depth) {
        Location loc = location.clone();
        for (int i = 1; i <= depth; i++) {
            Block b = loc.clone().add(0, -i, 0).getBlock();
            if (b.getType().isSolid()) return true;
        }
        return false;
    }

    public static boolean isInLiquid(Location location) {
        Block b = location.getBlock();
        Material type = b.getType();
        return type == Material.WATER || type == Material.LAVA;
    }

    public static boolean isWater(Material type) {
        return type == Material.WATER;
    }

    public static boolean isStairsOrSlab(Material type) {
        String name = type.name();
        return name.contains("STAIRS") || name.contains("SLAB");
    }
}