package net.plexmc.virtualentities.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicApiTest {
    @Test
    void animationFactoriesProduceValidatedImmutableValues() {
        AnimationSpec animation = AnimationSpec.loop("  idle  ");

        assertEquals("idle", animation.id());
        assertTrue(animation.loop());
        assertEquals(1.0, animation.speed());
        assertFalse(AnimationSpec.once("attack").loop());
        assertThrows(IllegalArgumentException.class, () -> new AnimationSpec("x", 0, 0, 0, true));
    }

    @Test
    void audiencePoliciesKeepOnlyStablePlayerIdentity() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        AudiencePolicy policy = AudiencePolicy.playersById(List.of(first, second, first));

        assertEquals(AudiencePolicy.Mode.PLAYERS, policy.mode());
        assertEquals(2, policy.playerIds().size());
        assertThrows(IllegalArgumentException.class, () -> AudiencePolicy.playersById(List.of()));
    }

    @Test
    void specClonesLocationAndRejectsInvalidTransform() {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        Plugin owner = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> method.getName().equals("isEnabled")
                        ? true : defaultValue(method.getReturnType()));
        Location source = new Location(world, 1, 2, 3, 4, 5);
        VirtualEntitySpec spec = VirtualEntitySpec.builder(SourceType.VANILLA, "armor_stand", source, owner)
                .expiresAfter(Duration.ofSeconds(2))
                .build();

        Location returned = spec.location();
        assertNotSame(source, returned);
        assertEquals(source, returned);
        returned.setX(999);
        assertEquals(1, spec.location().getX());
        assertThrows(IllegalArgumentException.class,
                () -> VirtualEntitySpec.builder(SourceType.VANILLA, "armor_stand",
                        new Location(world, Double.NaN, 0, 0), owner).build());
    }

    @Test
    void resultInvariantsDistinguishSuccessAndFailure() {
        assertTrue(OperationResult.ok().success());
        assertFalse(OperationResult.failure(FailureReason.INVALID_SPEC, "bad").success());
        assertThrows(IllegalArgumentException.class,
                () -> new OperationResult(true, FailureReason.INVALID_SPEC, "bad"));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationResult(false, FailureReason.NONE, "bad"));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
