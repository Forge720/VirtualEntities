package net.plexmc.virtualentities.internal.render;

import net.plexmc.virtualentities.api.FailureReason;
import net.plexmc.virtualentities.api.SourceType;
import net.plexmc.virtualentities.api.VirtualEntitySpec;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** Resolves a spec to exactly one provider renderer. */
public final class RendererFactory {
    private final boolean modelEngineEnabled;
    private final boolean mythicMobsEnabled;
    private final ProtocolIdAllocator protocolIdAllocator;
    private final Plugin plugin;

    public RendererFactory(boolean modelEngineEnabled, boolean mythicMobsEnabled, ProtocolIdAllocator protocolIdAllocator,
                           Plugin plugin) {
        this.modelEngineEnabled = modelEngineEnabled;
        this.mythicMobsEnabled = mythicMobsEnabled;
        this.protocolIdAllocator = protocolIdAllocator;
        this.plugin = plugin;
    }

    public RendererCreation create(UUID logicalId, VirtualEntitySpec spec) {
        return switch (spec.sourceType()) {
            case VANILLA -> createVanilla(logicalId, spec, spec.definitionId(), null);
            case PLAYER -> createPlayer(logicalId, spec);
            case TEXT_DISPLAY -> createTextDisplay(logicalId, spec);
            case MODELENGINE -> createModel(logicalId, spec, spec.definitionId());
            case MYTHICMOBS -> createMythic(logicalId, spec);
        };
    }

    private RendererCreation createVanilla(UUID logicalId, VirtualEntitySpec spec, String type, String name) {
        int protocolId;
        try {
            protocolId = protocolIdAllocator.allocate();
        } catch (Throwable throwable) {
            return RendererCreation.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Could not allocate a virtual protocol ID: " + throwable.getMessage());
        }
        RendererCreation creation = NmsEntityRenderer.create(protocolId, logicalId, type, name, spec.location(),
                spec.scale(), spec.text(), spec.interactionWidth(), spec.interactionHeight());
        if (!creation.success()) {
            protocolIdAllocator.release(protocolId);
        }
        return creation;
    }

    private RendererCreation createPlayer(UUID logicalId, VirtualEntitySpec spec) {
        int protocolId;
        try {
            protocolId = protocolIdAllocator.allocate();
        } catch (Throwable throwable) {
            return RendererCreation.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Could not allocate a virtual protocol ID: " + throwable.getMessage());
        }
        RendererCreation creation = NmsPlayerRenderer.create(protocolId, logicalId, spec, plugin);
        if (!creation.success()) {
            protocolIdAllocator.release(protocolId);
        }
        return creation;
    }

    private RendererCreation createTextDisplay(UUID logicalId, VirtualEntitySpec spec) {
        return createVanilla(logicalId, spec, "text_display", null);
    }

    private RendererCreation createModel(UUID logicalId, VirtualEntitySpec spec, String modelId) {
        if (!modelEngineEnabled || Bukkit.getPluginManager().getPlugin("ModelEngine") == null) {
            return RendererCreation.failure(FailureReason.MISSING_DEPENDENCY,
                    "ModelEngine is required for MODELENGINE requests");
        }
        int protocolId;
        try {
            protocolId = protocolIdAllocator.allocate();
        } catch (Throwable throwable) {
            return RendererCreation.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Could not allocate a virtual ModelEngine protocol ID: " + throwable.getMessage());
        }
        RendererCreation creation = ModelEngineRenderer.create(logicalId, protocolId, modelId, spec.location(),
                spec.initialAnimation(), spec.scale());
        if (!creation.success()) {
            protocolIdAllocator.release(protocolId);
        }
        return creation;
    }

    private RendererCreation createMythic(UUID logicalId, VirtualEntitySpec spec) {
        if (!mythicMobsEnabled || Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            return RendererCreation.failure(FailureReason.MISSING_DEPENDENCY,
                    "MythicMobs is required for MYTHICMOBS requests");
        }
        MythicMobsResolver.Resolution resolution = MythicMobsResolver.resolve(spec.definitionId(), spec.modelOverride());
        if (!resolution.success()) {
            return RendererCreation.failure(resolution.failureReason(), resolution.message());
        }
        ResolvedVisualDefinition definition = resolution.definition();
        if (definition.modelId() != null) {
            if (!modelEngineEnabled || Bukkit.getPluginManager().getPlugin("ModelEngine") == null) {
                if (spec.modelOverride() != null) {
                    return RendererCreation.failure(FailureReason.MISSING_DEPENDENCY,
                            "Explicit MythicMob model override requires ModelEngine: " + definition.modelId());
                }
            } else {
                return createModel(logicalId, spec, definition.modelId());
            }
        }
        if (definition.vanillaType() == null || definition.vanillaType().isBlank()) {
            return RendererCreation.failure(FailureReason.INVALID_DEFINITION,
                    "MythicMob has neither a supported vanilla base type nor a resolvable ModelEngine association");
        }
        return createVanilla(logicalId, spec, definition.vanillaType(), definition.displayName());
    }

    public void release(VirtualRenderer renderer) {
        if (renderer instanceof NmsEntityRenderer nms) {
            // The ID is released by the service through the renderer's identity hook.
            nms.releaseProtocolId(protocolIdAllocator);
        } else if (renderer instanceof NmsPlayerRenderer player) {
            player.releaseProtocolId(protocolIdAllocator);
        } else if (renderer instanceof ModelEngineRenderer model) {
            model.releaseProtocolId(protocolIdAllocator);
        }
    }
}
