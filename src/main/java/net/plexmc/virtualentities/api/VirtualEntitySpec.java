package net.plexmc.virtualentities.api;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable validated description of a logical virtual entity. */
public final class VirtualEntitySpec {
    private final SourceType sourceType;
    private final String definitionId;
    private final String modelOverride;
    private final Location location;
    private final AudiencePolicy audience;
    private final double renderDistance;
    private final boolean defaultRenderDistance;
    private final AnimationSpec initialAnimation;
    private final Duration expiration;
    private final Plugin owner;
    private final java.util.UUID profileUuid;
    private final String profileName;
    private final String skinTexture;
    private final String skinSignature;
    private final Component text;
    private final double scale;
    private final float interactionWidth;
    private final float interactionHeight;
    private final String pose;
    private final Map<EquipmentSlot, ItemStack> equipment;

    private VirtualEntitySpec(Builder builder) {
        sourceType = Objects.requireNonNull(builder.sourceType, "sourceType");
        definitionId = requireText(builder.definitionId, "definitionId");
        modelOverride = optionalText(builder.modelOverride);
        location = validateLocation(builder.location);
        audience = Objects.requireNonNull(builder.audience, "audience");
        if (!Double.isFinite(builder.renderDistance) || builder.renderDistance <= 0.0) {
            throw new IllegalArgumentException("renderDistance must be finite and positive");
        }
        renderDistance = builder.renderDistance;
        defaultRenderDistance = !builder.renderDistanceExplicit;
        initialAnimation = builder.initialAnimation;
        if (builder.expiration != null && (builder.expiration.isNegative() || builder.expiration.isZero())) {
            throw new IllegalArgumentException("expiration must be positive");
        }
        expiration = builder.expiration;
        owner = Objects.requireNonNull(builder.owner, "owner");
        profileUuid = builder.profileUuid;
        profileName = optionalText(builder.profileName);
        skinTexture = optionalText(builder.skinTexture);
        skinSignature = optionalText(builder.skinSignature);
        text = builder.text;
        if (!Double.isFinite(builder.scale) || builder.scale <= 0.0 || builder.scale > 64.0) {
            throw new IllegalArgumentException("scale must be finite, positive, and no greater than 64");
        }
        scale = builder.scale;
        if (!Float.isFinite(builder.interactionWidth) || builder.interactionWidth < 0.0f
                || !Float.isFinite(builder.interactionHeight) || builder.interactionHeight < 0.0f) {
            throw new IllegalArgumentException("interaction bounds must be finite and non-negative");
        }
        interactionWidth = builder.interactionWidth;
        interactionHeight = builder.interactionHeight;
        pose = optionalText(builder.pose);
        EnumMap<EquipmentSlot, ItemStack> copiedEquipment = new EnumMap<>(EquipmentSlot.class);
        builder.equipment.forEach((slot, item) -> {
            if (slot != null && item != null) {
                copiedEquipment.put(slot, item.clone());
            }
        });
        equipment = Collections.unmodifiableMap(copiedEquipment);
    }

    public static Builder builder(SourceType sourceType, String definitionId, Location location, Plugin owner) {
        return new Builder(sourceType, definitionId, location, owner);
    }

    public static final class Builder {
        private final SourceType sourceType;
        private final String definitionId;
        private final Location location;
        private final Plugin owner;
        private java.util.UUID profileUuid;
        private AudiencePolicy audience = AudiencePolicy.world();
        private double renderDistance = 64.0;
        private boolean renderDistanceExplicit;
        private String modelOverride;
        private AnimationSpec initialAnimation;
        private Duration expiration;
        private String profileName;
        private String skinTexture;
        private String skinSignature;
        private Component text;
        private double scale = 1.0;
        private float interactionWidth = 0.0f;
        private float interactionHeight = 0.0f;
        private String pose;
        private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);

        private Builder(SourceType sourceType, String definitionId, Location location, Plugin owner) {
            this.sourceType = sourceType;
            this.definitionId = definitionId;
            this.location = location;
            this.owner = owner;
        }

        public Builder audience(AudiencePolicy audience) {
            this.audience = audience;
            return this;
        }

        public Builder renderDistance(double renderDistance) {
            this.renderDistance = renderDistance;
            this.renderDistanceExplicit = true;
            return this;
        }

        public Builder modelOverride(String modelOverride) {
            this.modelOverride = modelOverride;
            return this;
        }

        public Builder initialAnimation(AnimationSpec initialAnimation) {
            this.initialAnimation = initialAnimation;
            return this;
        }

        public Builder expiresAfter(Duration expiration) {
            this.expiration = expiration;
            return this;
        }

        public Builder profileName(String profileName) {
            this.profileName = profileName;
            return this;
        }

        /** Stable profile identity used by packet-only player renderers. */
        public Builder profileUuid(java.util.UUID profileUuid) {
            this.profileUuid = profileUuid;
            return this;
        }

        public Builder skinTexture(String skinTexture, String skinSignature) {
            this.skinTexture = skinTexture;
            this.skinSignature = skinSignature;
            return this;
        }

        public Builder text(Component text) {
            this.text = text;
            return this;
        }

        public Builder scale(double scale) {
            this.scale = scale;
            return this;
        }

        public Builder interactionBounds(float width, float height) {
            this.interactionWidth = width;
            this.interactionHeight = height;
            return this;
        }

        public Builder pose(String pose) {
            this.pose = pose;
            return this;
        }

        public Builder equipment(Map<EquipmentSlot, ItemStack> equipment) {
            this.equipment.clear();
            if (equipment != null) {
                this.equipment.putAll(equipment);
            }
            return this;
        }

        public VirtualEntitySpec build() {
            return new VirtualEntitySpec(this);
        }
    }

    private static Location validateLocation(Location location) {
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("location must have a world");
        }
        if (!Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())
                || !Float.isFinite(location.getYaw()) || !Float.isFinite(location.getPitch())) {
            throw new IllegalArgumentException("location coordinates and rotation must be finite");
        }
        return location.clone();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return value;
    }

    private static String optionalText(String value) {
        return value == null ? null : (value.trim().isEmpty() ? null : value.trim());
    }

    public SourceType sourceType() { return sourceType; }
    public String definitionId() { return definitionId; }
    public String modelOverride() { return modelOverride; }
    public Location location() { return location.clone(); }
    public AudiencePolicy audience() { return audience; }
    public double renderDistance() { return renderDistance; }
    /** True when the plugin-level default may replace the API's built-in default. */
    public boolean usesDefaultRenderDistance() { return defaultRenderDistance; }
    public AnimationSpec initialAnimation() { return initialAnimation; }
    public Duration expiration() { return expiration; }
    public Plugin owner() { return owner; }
    public java.util.UUID profileUuid() { return profileUuid; }
    public String profileName() { return profileName; }
    public String skinTexture() { return skinTexture; }
    public String skinSignature() { return skinSignature; }
    public Component text() { return text; }
    public double scale() { return scale; }
    public float interactionWidth() { return interactionWidth; }
    public float interactionHeight() { return interactionHeight; }
    public String pose() { return pose; }
    public Map<EquipmentSlot, ItemStack> equipment() {
        EnumMap<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
        equipment.forEach((slot, item) -> copy.put(slot, item.clone()));
        return Collections.unmodifiableMap(copy);
    }
}
