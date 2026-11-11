package net.plexmc.virtualentities.api;

@FunctionalInterface
public interface VirtualInteractionListener {
    void onInteraction(VirtualInteraction interaction);
}
