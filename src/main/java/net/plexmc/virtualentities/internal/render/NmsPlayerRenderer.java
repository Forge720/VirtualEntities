package net.plexmc.virtualentities.internal.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;

import net.plexmc.virtualentities.api.AnimationSpec;
import net.plexmc.virtualentities.api.FailureReason;
import net.plexmc.virtualentities.api.OperationResult;
import net.plexmc.virtualentities.api.VirtualEntitySpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Packet-only player-model renderer backed by an unregistered transient ServerPlayer. */
public final class NmsPlayerRenderer implements VirtualRenderer {
    private final int protocolId;
    private final UUID logicalId;
    private final UUID profileId;
    private final String profileName;
    private final VirtualEntitySpec spec;
    private ServerPlayer entity;
    private Location location;
    private boolean disposed;

    private NmsPlayerRenderer(int protocolId, UUID logicalId, ServerPlayer entity, String profileName,
                              VirtualEntitySpec spec) {
        this.protocolId = protocolId;
        this.logicalId = logicalId;
        this.profileId = entity.getGameProfile().id();
        this.profileName = profileName;
        this.spec = spec;
        this.entity = entity;
        this.location = spec.location();
    }

    public static RendererCreation create(int protocolId, UUID logicalId, VirtualEntitySpec spec, org.bukkit.plugin.Plugin plugin) {
        try {
            Location location = spec.location();
            ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
            String name = profileName(spec.definitionId(), spec.profileName());
            UUID profileId = spec.profileUuid() == null ? logicalId : spec.profileUuid();
            GameProfile profile = new GameProfile(profileId, name);
            if (spec.skinTexture() != null) {
                Property property = spec.skinSignature() == null
                        ? new Property("textures", spec.skinTexture())
                        : new Property("textures", spec.skinTexture(), spec.skinSignature());
                profile.properties().put("textures", property);
            }
            ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
            player.setId(protocolId);
            player.setUUID(logicalId);
            player.setPos(location.getX(), location.getY(), location.getZ());
            player.setRot(location.getYaw(), location.getPitch());
            if (spec.pose() != null) {
                try {
                    player.setPose(Pose.valueOf(spec.pose().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException exception) {
                    return RendererCreation.failure(FailureReason.INVALID_DEFINITION,
                            "Unsupported player pose: " + spec.pose());
                }
            }
            for (var entry : spec.equipment().entrySet()) {
                EquipmentSlot slot = toNmsSlot(entry.getKey());
                if (slot != null) {
                    player.setItemSlot(slot, CraftItemStack.asNMSCopy(entry.getValue()));
                }
            }
            return RendererCreation.success(new NmsPlayerRenderer(protocolId, logicalId, player, name, spec));
        } catch (Throwable throwable) {
            return RendererCreation.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Failed to prepare transient player renderer: " + throwable.getClass().getSimpleName()
                            + ": " + throwable.getMessage());
        }
    }

    @Override
    public String providerName() {
        return "VANILLA_NMS_PLAYER";
    }

    @Override
    public OperationResult show(Player viewer) {
        if (disposed) return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        try {
            // The ServerPlayer convenience constructors read latency and tab-list state from
            // player.connection. This transient packet-only ServerPlayer intentionally has no
            // connection, so construct a connection-independent ADD_PLAYER entry explicitly.
            ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                    profileId, entity.getGameProfile(), false, 0, GameType.SURVIVAL, null, true, 0, null);
            send(viewer, new ClientboundPlayerInfoUpdatePacket(
                    java.util.EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER), List.of(entry)));
            send(viewer, new ClientboundAddEntityPacket(protocolId, logicalId, location.getX(), location.getY(),
                    location.getZ(), location.getPitch(), location.getYaw(), entity.getType(), 0, Vec3.ZERO, 0.0));
            List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> data = entity.getEntityData().packAll();
            if (!data.isEmpty()) send(viewer, new ClientboundSetEntityDataPacket(protocolId, data));
            send(viewer, new ClientboundUpdateAttributesPacket(protocolId,
                    entity.getAttributes().getSyncableAttributes()));
            sendEquipment(viewer);
            return OperationResult.ok();
        } catch (Throwable throwable) {
            return OperationResult.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Failed to send player spawn packets: " + throwable.getMessage());
        }
    }

    @Override
    public OperationResult hide(Player viewer) {
        if (disposed) return OperationResult.ok();
        try {
            send(viewer, new ClientboundRemoveEntitiesPacket(protocolId));
            send(viewer, new ClientboundPlayerInfoRemovePacket(List.of(profileId)));
            return OperationResult.ok();
        } catch (Throwable throwable) {
            return OperationResult.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Failed to remove player packets: " + throwable.getMessage());
        }
    }

    @Override
    public OperationResult updateLocation(Location newLocation) {
        if (disposed) return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        if (!newLocation.getWorld().equals(location.getWorld())) {
            ServerLevel level = ((CraftWorld) newLocation.getWorld()).getHandle();
            entity = new ServerPlayer(((CraftServer) Bukkit.getServer()).getServer(), level,
                    entity.getGameProfile(), ClientInformation.createDefault());
            entity.setId(protocolId);
            entity.setUUID(logicalId);
        }
        location = newLocation.clone();
        entity.setPos(location.getX(), location.getY(), location.getZ());
        entity.setRot(location.getYaw(), location.getPitch());
        return OperationResult.ok();
    }

    @Override
    public OperationResult sendTransform(Iterable<Player> viewers) {
        if (disposed) return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        ClientboundTeleportEntityPacket packet = new ClientboundTeleportEntityPacket(protocolId,
                new net.minecraft.world.entity.PositionMoveRotation(
                        new Vec3(location.getX(), location.getY(), location.getZ()), Vec3.ZERO,
                        location.getYaw(), location.getPitch()), Set.of(), entity.onGround());
        try {
            for (Player viewer : viewers) send(viewer, packet);
            return OperationResult.ok();
        } catch (Throwable throwable) {
            return OperationResult.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Failed to send player transform: " + throwable.getMessage());
        }
    }

    @Override
    public Set<Integer> entityIds() {
        return Set.of(protocolId);
    }

    @Override
    public void sendUpdatedState(Iterable<Player> viewers) {
        if (disposed || entity == null) return;
        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> data = entity.getEntityData().packAll();
        if (!data.isEmpty()) {
            ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(protocolId, data);
            for (Player viewer : viewers) send(viewer, packet);
        }
        sendAttributes(viewers);
    }

    @Override
    public OperationResult updateScale(double scale) {
        if (disposed) return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        var attribute = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (attribute == null) {
            return OperationResult.failure(FailureReason.UNSUPPORTED_OPERATION,
                    "Player entity does not expose the scale attribute");
        }
        attribute.setBaseValue(scale);
        return OperationResult.ok();
    }

    @Override
    public OperationResult playAnimation(AnimationSpec animation) {
        return OperationResult.failure(FailureReason.UNSUPPORTED_OPERATION,
                "Native player entities do not expose named animation control in v0.1");
    }

    @Override
    public OperationResult stopAnimation(String animationId) {
        return OperationResult.failure(FailureReason.UNSUPPORTED_OPERATION,
                "Native player entities do not expose named animation control in v0.1");
    }

    @Override
    public void dispose() {
        disposed = true;
        entity = null;
        location = null;
    }

    void releaseProtocolId(ProtocolIdAllocator allocator) {
        allocator.release(protocolId);
    }

    private void sendEquipment(Player viewer) {
        if (spec.equipment().isEmpty()) return;
        List<com.mojang.datafixers.util.Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> slots = new ArrayList<>();
        for (var entry : spec.equipment().entrySet()) {
            EquipmentSlot slot = toNmsSlot(entry.getKey());
            if (slot != null) slots.add(com.mojang.datafixers.util.Pair.of(slot,
                    CraftItemStack.asNMSCopy(entry.getValue())));
        }
        if (!slots.isEmpty()) send(viewer, new ClientboundSetEquipmentPacket(protocolId, slots));
    }

    private void sendAttributes(Iterable<Player> viewers) {
        if (entity == null) return;
        ClientboundUpdateAttributesPacket packet = new ClientboundUpdateAttributesPacket(protocolId,
                entity.getAttributes().getSyncableAttributes());
        for (Player viewer : viewers) send(viewer, packet);
    }

    private static EquipmentSlot toNmsSlot(org.bukkit.inventory.EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> EquipmentSlot.MAINHAND;
            case OFF_HAND -> EquipmentSlot.OFFHAND;
            case FEET -> EquipmentSlot.FEET;
            case LEGS -> EquipmentSlot.LEGS;
            case CHEST -> EquipmentSlot.CHEST;
            case HEAD -> EquipmentSlot.HEAD;
            default -> null;
        };
    }

    private static String profileName(String definitionId, String requested) {
        String value = requested == null || requested.isBlank() ? definitionId : requested;
        value = value.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return value.length() <= 16 ? value : value.substring(0, 16);
    }

    private static void send(Player viewer, Packet<?> packet) {
        ((CraftPlayer) viewer).getHandle().connection.send(packet);
    }
}
