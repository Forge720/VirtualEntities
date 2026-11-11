package net.plexmc.virtualentities.internal.render;

import io.papermc.paper.adventure.PaperAdventure;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import net.plexmc.virtualentities.api.AnimationSpec;
import net.plexmc.virtualentities.api.FailureReason;
import net.plexmc.virtualentities.api.OperationResult;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Packet renderer for a validated subset of vanilla entity types. */
public final class NmsEntityRenderer implements VirtualRenderer {
    private static final Set<String> SUPPORTED = Set.of(
            "armor_stand", "zombie", "skeleton", "creeper", "spider", "villager",
            "item_display", "block_display", "text_display", "marker", "interaction"
    );

    private final int protocolId;
    private final UUID logicalId;
    private final String entityTypeId;
    private final String customName;
    private final Component text;
    private final float interactionWidth;
    private final float interactionHeight;
    private Entity entity;
    private Location location;
    private double scale;
    private boolean disposed;

    private NmsEntityRenderer(int protocolId, UUID logicalId, String entityTypeId, String customName, Location location,
                              Entity entity, double scale, Component text, float interactionWidth,
                              float interactionHeight) {
        this.protocolId = protocolId;
        this.logicalId = logicalId;
        this.entityTypeId = entityTypeId;
        this.customName = customName;
        this.text = text;
        this.interactionWidth = interactionWidth;
        this.interactionHeight = interactionHeight;
        this.location = location.clone();
        this.entity = entity;
        this.scale = scale;
    }

    public static RendererCreation create(int protocolId, UUID logicalId, String requestedType, String customName,
                                          Location location) {
        return create(protocolId, logicalId, requestedType, customName, location, 1.0, null, 0.0f, 0.0f);
    }

    public static RendererCreation create(int protocolId, UUID logicalId, String requestedType, String customName,
                                          Location location, double scale, Component text,
                                          float interactionWidth, float interactionHeight) {
        String normalized = normalizeType(requestedType);
        if (!SUPPORTED.contains(normalized)) {
            return RendererCreation.failure(FailureReason.INVALID_DEFINITION,
                    "Vanilla entity type is outside the v0.1 supported set: " + requestedType);
        }
        try {
            EntityType<?> type = EntityType.byString(normalized).orElse(null);
            if (type == null) {
                return RendererCreation.failure(FailureReason.INVALID_DEFINITION,
                        "Unknown vanilla entity type: " + requestedType);
            }
            Entity entity = createEntity(type, logicalId, protocolId, location, customName, scale, text,
                    interactionWidth, interactionHeight);
            if (entity == null) {
                return RendererCreation.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                        "Paper could not construct the transient NMS entity: " + normalized);
            }
            return RendererCreation.success(new NmsEntityRenderer(protocolId, logicalId, normalized, customName,
                    location, entity, scale, text, interactionWidth, interactionHeight));
        } catch (Throwable throwable) {
            return RendererCreation.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Failed to prepare vanilla packet entity " + normalized + ": " + throwable.getClass().getSimpleName()
                            + ": " + throwable.getMessage());
        }
    }

    private static Entity createEntity(EntityType<?> type, UUID logicalId, int protocolId, Location location,
                                       String customName, double scale, Component text,
                                       float interactionWidth, float interactionHeight) {
        ServerLevel level = ((CraftWorld) Objects.requireNonNull(location.getWorld(), "location.world")).getHandle();
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            return null;
        }
        entity.setId(protocolId);
        entity.setUUID(logicalId);
        entity.setPos(location.getX(), location.getY(), location.getZ());
        entity.setRot(location.getYaw(), location.getPitch());
        if (customName != null && !customName.isBlank()) {
            entity.setCustomName(net.minecraft.network.chat.Component.literal(customName));
            entity.setCustomNameVisible(true);
        }
        if (entity instanceof Display.TextDisplay textDisplay && text != null) {
            textDisplay.setText(PaperAdventure.asVanilla(text));
            textDisplay.setTransformation(new com.mojang.math.Transformation(
                    new org.joml.Vector3f(), new org.joml.Quaternionf(),
                    new org.joml.Vector3f((float) scale, (float) scale, (float) scale),
                    new org.joml.Quaternionf()));
        }
        if (entity instanceof Interaction interaction) {
            interaction.setWidth(interactionWidth > 0.0f ? interactionWidth : 1.0f);
            interaction.setHeight(interactionHeight > 0.0f ? interactionHeight : 2.0f);
            interaction.setResponse(false);
        }
        applyScale(entity, scale);
        return entity;
    }

    @Override
    public String providerName() {
        return "VANILLA_NMS";
    }

    @Override
    public OperationResult show(Player player) {
        if (disposed) {
            return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        }
        try {
            send(player, new ClientboundAddEntityPacket(protocolId, logicalId, location.getX(), location.getY(),
                    location.getZ(), location.getPitch(), location.getYaw(), entity.getType(), 0, Vec3.ZERO, 0.0));
            List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> data = entity.getEntityData().packAll();
            if (!data.isEmpty()) {
                send(player, new ClientboundSetEntityDataPacket(protocolId, data));
            }
            sendAttributes(player);
            return OperationResult.ok();
        } catch (Throwable throwable) {
            return OperationResult.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Failed to send vanilla spawn packets: " + throwable.getMessage());
        }
    }

    @Override
    public OperationResult hide(Player player) {
        if (disposed) {
            return OperationResult.ok();
        }
        try {
            send(player, new ClientboundRemoveEntitiesPacket(protocolId));
            return OperationResult.ok();
        } catch (Throwable throwable) {
            return OperationResult.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Failed to send vanilla removal packet: " + throwable.getMessage());
        }
    }

    @Override
    public OperationResult updateLocation(Location newLocation) {
        if (disposed) {
            return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        }
        boolean worldChanged = !newLocation.getWorld().equals(location.getWorld());
        if (worldChanged) {
            Entity recreated = createEntity(EntityType.byString(entityTypeId).orElseThrow(), logicalId, protocolId,
                    newLocation, customName, scale, text, interactionWidth, interactionHeight);
            if (recreated == null) {
                return OperationResult.failure(FailureReason.INVALID_LOCATION,
                        "Could not recreate the transient entity in the destination world");
            }
            entity = recreated;
            location = newLocation.clone();
            return OperationResult.ok();
        }
        location = newLocation.clone();
        entity.setPos(location.getX(), location.getY(), location.getZ());
        entity.setRot(location.getYaw(), location.getPitch());
        return OperationResult.ok();
    }

    /** Sends an absolute transform to already-rendered viewers. */
    @Override
    public OperationResult sendTransform(Iterable<Player> viewers) {
        if (disposed) {
            return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        }
        ClientboundTeleportEntityPacket packet = new ClientboundTeleportEntityPacket(protocolId,
                new PositionMoveRotation(new Vec3(location.getX(), location.getY(), location.getZ()), Vec3.ZERO,
                        location.getYaw(), location.getPitch()), Set.of(), entity.onGround());
        try {
            for (Player player : viewers) {
                send(player, packet);
            }
        } catch (Throwable throwable) {
            // A disconnected viewer can invalidate a packet send between reconciliation and this pass.
            return OperationResult.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Failed to send vanilla transform packet: " + throwable.getMessage());
        }
        return OperationResult.ok();
    }

    @Override
    public OperationResult updateScale(double scale) {
        if (disposed) {
            return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        }
        if (!Double.isFinite(scale) || scale <= 0.0 || scale > 64.0) {
            return OperationResult.failure(FailureReason.INVALID_SPEC,
                    "scale must be finite, positive, and no greater than 64");
        }
        this.scale = scale;
        if (entity instanceof Display display) {
            display.setTransformation(new com.mojang.math.Transformation(
                    new org.joml.Vector3f(), new org.joml.Quaternionf(),
                    new org.joml.Vector3f((float) scale, (float) scale, (float) scale),
                    new org.joml.Quaternionf()));
        }
        applyScale(entity, scale);
        return OperationResult.ok();
    }

    @Override
    public Set<Integer> entityIds() {
        return Set.of(protocolId);
    }

    @Override
    public void sendUpdatedState(Iterable<Player> viewers) {
        if (disposed || entity == null) {
            return;
        }
        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> data = entity.getEntityData().packAll();
        if (!data.isEmpty()) {
            ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(protocolId, data);
            for (Player player : viewers) {
                send(player, packet);
            }
        }
        for (Player player : viewers) {
            sendAttributes(player);
        }
    }

    void releaseProtocolId(ProtocolIdAllocator allocator) {
        allocator.release(protocolId);
    }

    @Override
    public OperationResult playAnimation(AnimationSpec animation) {
        return OperationResult.failure(FailureReason.UNSUPPORTED_OPERATION,
                "Vanilla entities do not expose named animation control in v0.1");
    }

    @Override
    public OperationResult stopAnimation(String animationId) {
        return OperationResult.failure(FailureReason.UNSUPPORTED_OPERATION,
                "Vanilla entities do not expose named animation control in v0.1");
    }

    @Override
    public void dispose() {
        disposed = true;
        entity = null;
        location = null;
    }

    private static void applyScale(Entity entity, double scale) {
        if (entity instanceof LivingEntity living) {
            var attribute = living.getAttribute(Attributes.SCALE);
            if (attribute != null) {
                attribute.setBaseValue(scale);
            }
        }
    }

    private static void send(Player player, Packet<?> packet) {
        ((CraftPlayer) player).getHandle().connection.send(packet);
    }

    private void sendAttributes(Player player) {
        if (entity instanceof LivingEntity living) {
            send(player, new ClientboundUpdateAttributesPacket(protocolId,
                    living.getAttributes().getSyncableAttributes()));
        }
    }

    private static String normalizeType(String requestedType) {
        String normalized = requestedType.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return normalized;
    }
}
