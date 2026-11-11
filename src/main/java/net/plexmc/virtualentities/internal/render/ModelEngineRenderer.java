package net.plexmc.virtualentities.internal.render;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import net.plexmc.virtualentities.api.AnimationSpec;
import net.plexmc.virtualentities.api.FailureReason;
import net.plexmc.virtualentities.api.OperationResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Adapter over ModelEngine 4's official packet-only Dummy API. */
public final class ModelEngineRenderer implements VirtualRenderer {
    private final Dummy<?> dummy;
    private final ModeledEntity modeledEntity;
    private final ActiveModel activeModel;
    private final String modelId;
    private final int protocolId;
    private boolean disposed;

    private ModelEngineRenderer(int protocolId, Dummy<?> dummy, ModeledEntity modeledEntity,
                                ActiveModel activeModel, String modelId) {
        this.protocolId = protocolId;
        this.dummy = dummy;
        this.modeledEntity = modeledEntity;
        this.activeModel = activeModel;
        this.modelId = modelId;
    }

    public static RendererCreation create(UUID logicalId, int protocolId, String modelId, Location location,
                                          AnimationSpec initialAnimation, double scale) {
        try {
            // ModelEngine owns Dummy protocol identities. Its public UUID constructor allocates
            // the protocol id through ModelEngine while preserving our logical identity; the
            // explicit integer constructor is an internal/advanced path and prevents R4.1.x
            // from initializing the Dummy's tracked data correctly.
            Dummy<?> dummy = new Dummy<>(logicalId, null);
            dummy.setLocation(location.clone());
            dummy.setDetectingPlayers(false);
            ActiveModel activeModel = ModelEngineAPI.createActiveModel(modelId);
            if (activeModel == null) {
                dummy.setRemoved(true);
                return RendererCreation.failure(FailureReason.UNAVAILABLE_MODEL,
                        "ModelEngine model is not registered: " + modelId);
            }
            activeModel.setScale(scale);
            ModeledEntity modeledEntity = ModelEngineAPI.createModeledEntity(dummy,
                    entity -> entity.addModel(activeModel, true));
            if (modeledEntity.getModel(activeModel.getBlueprint().getName()).isEmpty()) {
                dummy.setRemoved(true);
                return RendererCreation.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                        "ModelEngine rejected model attachment: " + modelId);
            }
            ModelEngineRenderer renderer = new ModelEngineRenderer(protocolId, dummy, modeledEntity, activeModel, modelId);
            if (initialAnimation != null) {
                OperationResult result = renderer.playAnimation(initialAnimation);
                if (!result.success()) {
                    renderer.dispose();
                    return RendererCreation.failure(result.failureReason(), result.message());
                }
            }
            return RendererCreation.success(renderer);
        } catch (Throwable throwable) {
            return RendererCreation.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Failed to initialize ModelEngine Dummy " + modelId + ": "
                            + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    @Override
    public String providerName() {
        return "MODELENGINE_DUMMY";
    }

    @Override
    public OperationResult show(Player player) {
        if (disposed) {
            return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        }
        try {
            dummy.setForceHidden(player, false);
            dummy.setForceViewing(player, true);
            return OperationResult.ok();
        } catch (Throwable throwable) {
            return OperationResult.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "ModelEngine failed to force-view model " + modelId + ": " + throwable.getMessage());
        }
    }

    @Override
    public OperationResult hide(Player player) {
        if (disposed) {
            return OperationResult.ok();
        }
        try {
            dummy.setForceViewing(player, false);
            dummy.setForceHidden(player, true);
            return OperationResult.ok();
        } catch (Throwable throwable) {
            return OperationResult.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "ModelEngine failed to hide model " + modelId + ": " + throwable.getMessage());
        }
    }

    @Override
    public OperationResult updateLocation(Location location) {
        if (disposed) {
            return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        }
        try {
            dummy.syncLocation(location.clone());
            return OperationResult.ok();
        } catch (Throwable throwable) {
            return OperationResult.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "ModelEngine failed to synchronize model location: " + throwable.getMessage());
        }
    }

    @Override
    public OperationResult playAnimation(AnimationSpec animation) {
        if (disposed) {
            return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        }
        try {
            AnimationHandler handler = activeModel.getAnimationHandler();
            if (handler.getAnimation(animation.id()) == null) {
                return OperationResult.failure(FailureReason.INVALID_DEFINITION,
                        "ModelEngine animation is not registered on " + modelId + ": " + animation.id());
            }
            handler.playAnimation(animation.id(), animation.speed(), animation.transitionSeconds(),
                    animation.blendSeconds(), animation.loop());
            return OperationResult.ok();
        } catch (Throwable throwable) {
            return OperationResult.failure(FailureReason.UNSUPPORTED_OPERATION,
                    "ModelEngine rejected animation " + animation.id() + ": " + throwable.getMessage());
        }
    }

    @Override
    public OperationResult stopAnimation(String animationId) {
        if (disposed) {
            return OperationResult.failure(FailureReason.TERMINATED, "Renderer is disposed");
        }
        Objects.requireNonNull(animationId, "animationId");
        try {
            activeModel.getAnimationHandler().stopAnimation(animationId);
            return OperationResult.ok();
        } catch (Throwable throwable) {
            return OperationResult.failure(FailureReason.UNSUPPORTED_OPERATION,
                    "ModelEngine rejected animation stop " + animationId + ": " + throwable.getMessage());
        }
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
        activeModel.setScale(scale);
        return OperationResult.ok();
    }

    @Override
    public Set<Integer> entityIds() {
        return Set.of(protocolId);
    }

    void releaseProtocolId(ProtocolIdAllocator allocator) {
        allocator.release(protocolId);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        try {
            dummy.setRemoved(true);
            if (!modeledEntity.isDestroyed()) {
                modeledEntity.destroy();
            }
        } catch (Throwable ignored) {
            // Shutdown and repeated disposal must remain idempotent.
        }
    }
}
