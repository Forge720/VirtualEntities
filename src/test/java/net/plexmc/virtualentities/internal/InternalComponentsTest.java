package net.plexmc.virtualentities.internal;

import net.plexmc.virtualentities.api.AnimationSpec;
import net.plexmc.virtualentities.api.OperationResult;
import net.plexmc.virtualentities.api.SourceType;
import net.plexmc.virtualentities.api.VirtualEntitySpec;
import net.plexmc.virtualentities.internal.render.NmsEntityRenderer;
import net.plexmc.virtualentities.internal.render.RendererCreation;
import net.plexmc.virtualentities.internal.render.VirtualRenderer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalComponentsTest {
    @Test
    void registryMaintainsOwnerIndexAndEntityLifecycleState() {
        Plugin owner = proxy(Plugin.class);
        World world = proxy(World.class);
        VirtualEntitySpec spec = VirtualEntitySpec.builder(
                SourceType.VANILLA, "armor_stand", new Location(world, 0, 64, 0), owner).build();
        InternalVirtualEntity first = new InternalVirtualEntity(null, UUID.randomUUID(), spec,
                new StubRenderer(), 5);
        InternalVirtualEntity second = new InternalVirtualEntity(null, UUID.randomUUID(), spec,
                new StubRenderer(), 0);
        EntityRegistry registry = new EntityRegistry();

        registry.add(first);
        registry.add(second);

        assertEquals(2, registry.size());
        assertEquals(2, registry.ownedBy(owner).size());
        assertFalse(first.isExpired(4));
        assertTrue(first.isExpired(5));
        assertEquals(first, registry.remove(first.id()));
        assertEquals(1, registry.size());
        assertEquals(1, registry.ownedBy(owner).size());
        first.markRemoved();
        assertTrue(first.isRemoved());
    }

    @Test
    void transformStateRejectsInvalidLocationsAndTracksDirtyUpdates() {
        World world = proxy(World.class);
        Plugin owner = proxy(Plugin.class);
        VirtualEntitySpec spec = VirtualEntitySpec.builder(
                SourceType.VANILLA, "armor_stand", new Location(world, 0, 64, 0), owner).build();
        InternalVirtualEntity entity = new InternalVirtualEntity(null, UUID.randomUUID(), spec,
                new StubRenderer(), 0);

        assertFalse(entity.dirtyTransform());
        assertTrue(entity.setLocation(new Location(world, 2, 65, 3)).success());
        assertTrue(entity.dirtyTransform());
        entity.clearDirtyTransform();
        assertFalse(entity.dirtyTransform());
        assertFalse(entity.setLocation(new Location(world, Double.NaN, 0, 0)).success());
    }

    @Test
    void rendererCreationAndUnsupportedVanillaTypeFailExplicitly() {
        RendererCreation created = RendererCreation.success(new StubRenderer());
        assertTrue(created.success());
        RendererCreation failed = RendererCreation.failure(
                net.plexmc.virtualentities.api.FailureReason.INVALID_DEFINITION, "unsupported");
        assertFalse(failed.success());
        assertNull(failed.renderer());

        RendererCreation unsupported = NmsEntityRenderer.create(
                1, UUID.randomUUID(), "dragon", null, new Location(null, 0, 0, 0));
        assertFalse(unsupported.success());
        assertEquals(net.plexmc.virtualentities.api.FailureReason.INVALID_DEFINITION,
                unsupported.failureReason());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getName().equals("isEnabled")) return true;
                    if (!method.getReturnType().isPrimitive()) return null;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == double.class) return 0D;
                    if (method.getReturnType() == float.class) return 0F;
                    if (method.getReturnType() == short.class) return (short) 0;
                    if (method.getReturnType() == byte.class) return (byte) 0;
                    if (method.getReturnType() == char.class) return '\0';
                    return null;
                });
    }

    private static final class StubRenderer implements VirtualRenderer {
        @Override public String providerName() { return "TEST"; }
        @Override public OperationResult show(Player player) { return OperationResult.ok(); }
        @Override public OperationResult hide(Player player) { return OperationResult.ok(); }
        @Override public OperationResult updateLocation(Location location) { return OperationResult.ok(); }
        @Override public OperationResult playAnimation(AnimationSpec animation) { return OperationResult.ok(); }
        @Override public OperationResult stopAnimation(String animationId) { return OperationResult.ok(); }
        @Override public void dispose() { }
    }
}
