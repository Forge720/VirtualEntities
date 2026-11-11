package net.plexmc.virtualentities.internal.render;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;

/** Allocates high positive protocol IDs and verifies live-world coexistence. */
public final class ProtocolIdAllocator {
    private static final int FIRST_ID = 1_000_000_000;
    private final Set<Integer> allocated = new HashSet<>();
    private int next = FIRST_ID;

    public int allocate() {
        for (int attempts = 0; attempts < Integer.MAX_VALUE; attempts++) {
            int candidate = next--;
            if (candidate <= 0) {
                next = FIRST_ID;
                candidate = next--;
            }
            if (!allocated.contains(candidate) && !isLiveEntityId(candidate)) {
                allocated.add(candidate);
                return candidate;
            }
        }
        throw new IllegalStateException("No collision-free virtual entity protocol ID is available");
    }

    public void release(int id) {
        allocated.remove(id);
    }

    private boolean isLiveEntityId(int candidate) {
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity.getEntityId() == candidate) {
                    return true;
                }
            }
        }
        return false;
    }
}
