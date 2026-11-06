package me.usainsrht.announcement.paper;

import me.usainsrht.announcement.core.NativeRenderer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.List;

/** Full MiniMessage rendering through the Adventure that ships with Paper, including MiniPlaceholders. */
public final class PaperRenderer implements NativeRenderer {
    private Method deserializeWithTarget; // MiniMessage#deserialize(String, Pointered, TagResolver...), newer Adventure
    private boolean targetLookedUp;

    @Override
    public boolean usesMiniPlaceholders() {
        return Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders");
    }

    private Component deserialize(MiniMessage mm, String text, Audience target, TagResolver... resolvers) {
        if (target != null && usesMiniPlaceholders()) {
            try {
                if (MiniPlaceholdersHook.targetsAudience()) {
                    if (!targetLookedUp) {
                        targetLookedUp = true;
                        deserializeWithTarget = MiniMessage.class.getMethod("deserialize", String.class,
                                Class.forName("net.kyori.adventure.pointer.Pointered"), TagResolver[].class);
                    }
                    if (deserializeWithTarget != null) {
                        return (Component) deserializeWithTarget.invoke(mm, text, target, resolvers);
                    }
                }
            } catch (Throwable ignored) {
                // fall through to the plain path
            }
        }
        return mm.deserialize(text, resolvers);
    }

    private TagResolver placeholders(Audience target) {
        if (!usesMiniPlaceholders()) return TagResolver.empty();
        try {
            return MiniPlaceholdersHook.resolver(target);
        } catch (Throwable t) {
            // incompatible MiniPlaceholders version: keep announcing without it
            return TagResolver.empty();
        }
    }

    @Override
    public Object render(Object player, List<String> template, String message, boolean customCenter) {
        MiniMessage mm = customCenter ? CenterTag.centerTagMM : MiniMessage.miniMessage();
        Audience target = player instanceof Audience ? (Audience) player : null;
        TagResolver mp = placeholders(target);
        Component msg = deserialize(mm, message, target, mp);
        Component out = Component.empty();
        for (String line : template) {
            out = out.append(deserialize(mm, line, target,
                    TagResolver.resolver(Placeholder.component("announcement", msg), mp)));
            out = out.append(Component.newline());
        }
        return out;
    }

    @Override
    public void send(Object audience, Object component) {
        ((Audience) audience).sendMessage((Component) component);
    }
}
