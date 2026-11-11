package net.plexmc.virtualentities.api;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable initial audience policy. */
public final class AudiencePolicy {
    public enum Mode {
        PLAYER,
        PLAYERS,
        WORLD
    }

    private final Mode mode;
    private final Set<UUID> playerIds;

    private AudiencePolicy(Mode mode, Collection<UUID> playerIds) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.playerIds = Collections.unmodifiableSet(new LinkedHashSet<>(playerIds));
        if (mode == Mode.PLAYER && this.playerIds.size() != 1) {
            throw new IllegalArgumentException("PLAYER audience requires exactly one player");
        }
        if (mode == Mode.PLAYERS && this.playerIds.isEmpty()) {
            throw new IllegalArgumentException("PLAYERS audience requires at least one player");
        }
        if (mode == Mode.WORLD && !this.playerIds.isEmpty()) {
            throw new IllegalArgumentException("WORLD audience cannot contain designated players");
        }
    }

    public static AudiencePolicy player(Player player) {
        Objects.requireNonNull(player, "player");
        return new AudiencePolicy(Mode.PLAYER, Set.of(player.getUniqueId()));
    }

    public static AudiencePolicy player(UUID playerId) {
        return new AudiencePolicy(Mode.PLAYER, Set.of(Objects.requireNonNull(playerId, "playerId")));
    }

    public static AudiencePolicy players(Collection<? extends Player> players) {
        Objects.requireNonNull(players, "players");
        return new AudiencePolicy(Mode.PLAYERS, players.stream().map(Player::getUniqueId).toList());
    }

    public static AudiencePolicy playersById(Collection<UUID> playerIds) {
        return new AudiencePolicy(Mode.PLAYERS, playerIds);
    }

    public static AudiencePolicy world() {
        return new AudiencePolicy(Mode.WORLD, Set.of());
    }

    public Mode mode() {
        return mode;
    }

    public Set<UUID> playerIds() {
        return playerIds;
    }
}
