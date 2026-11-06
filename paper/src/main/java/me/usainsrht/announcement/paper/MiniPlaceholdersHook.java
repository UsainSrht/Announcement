package me.usainsrht.announcement.paper;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.lang.reflect.Method;

/**
 * MiniPlaceholders through reflection, so one jar works with both of its API generations:
 * <ul>
 *   <li>2.x: {@code getGlobalPlaceholders()} and {@code getAudienceGlobalPlaceholders(Audience)}, the audience is
 *       baked into the returned resolver;</li>
 *   <li>3.x: {@code globalPlaceholders()} and {@code audienceGlobalPlaceholders()}, the audience is handed to
 *       {@code MiniMessage#deserialize(String, Pointered, TagResolver...)} as the target.</li>
 * </ul>
 * Only touched when the MiniPlaceholders plugin is enabled.
 */
final class MiniPlaceholdersHook {
    private static boolean resolved;
    private static boolean v3;
    private static Method global;
    private static Method audience;

    private MiniPlaceholdersHook() {
    }

    private static synchronized void resolve() throws Exception {
        if (resolved) return;
        Class<?> api = Class.forName("io.github.miniplaceholders.api.MiniPlaceholders");
        try {
            global = api.getMethod("globalPlaceholders");
            audience = api.getMethod("audienceGlobalPlaceholders");
            v3 = true;
        } catch (NoSuchMethodException e) {
            global = api.getMethod("getGlobalPlaceholders");
            audience = api.getMethod("getAudienceGlobalPlaceholders", Audience.class);
            v3 = false;
        }
        resolved = true;
    }

    /** true for the 3.x generation: the audience must be passed to MiniMessage as the deserialization target. */
    static boolean targetsAudience() throws Exception {
        resolve();
        return v3;
    }

    /** Global placeholders plus (when audience != null) that player's own placeholders. */
    static TagResolver resolver(Audience who) throws Exception {
        resolve();
        if (who == null) return (TagResolver) global.invoke(null);
        return (TagResolver) (v3 ? audience.invoke(null) : audience.invoke(null, who));
    }
}
