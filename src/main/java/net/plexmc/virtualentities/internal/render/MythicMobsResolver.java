package net.plexmc.virtualentities.internal.render;

import io.lumine.mythic.api.config.MythicConfig;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.plexmc.virtualentities.api.FailureReason;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads registered MythicMobs definitions without spawning or executing them. */
public final class MythicMobsResolver {
    private static final Pattern MODEL_MECHANIC = Pattern.compile(
            "(?i)\\bmodel\\s*\\{[^}]*\\b(?:mid|model)\\s*=\\s*([^,}\\s]+)");

    private MythicMobsResolver() {
    }

    public static Resolution resolve(String internalId, String explicitModelOverride) {
        MythicMob mob = MythicBukkit.inst().getMobManager().getMythicMob(internalId).orElse(null);
        if (mob == null) {
            return Resolution.failure(FailureReason.INVALID_DEFINITION,
                    "MythicMob is not registered: " + internalId);
        }

        String vanillaType = normalize(mob.getEntityTypeString());
        String displayName = null;
        if (mob.getDisplayName() != null) {
            displayName = mob.getDisplayName().get();
        }
        String modelId = explicitModelOverride == null ? findModelAssociation(mob.getConfig()) : explicitModelOverride;
        return Resolution.success(new ResolvedVisualDefinition(vanillaType, modelId, displayName));
    }

    private static String findModelAssociation(MythicConfig config) {
        if (config == null || !config.isList("Skills")) {
            return null;
        }
        List<String> skills = config.getStringList("Skills");
        for (String skill : skills) {
            Matcher matcher = MODEL_MECHANIC.matcher(skill);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return normalized;
    }

    public record Resolution(ResolvedVisualDefinition definition, FailureReason failureReason, String message) {
        static Resolution success(ResolvedVisualDefinition definition) {
            return new Resolution(definition, FailureReason.NONE, "ok");
        }

        static Resolution failure(FailureReason reason, String message) {
            return new Resolution(null, reason, message);
        }

        boolean success() {
            return definition != null;
        }
    }
}
