package com.purpurmc.announcement.utils;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Modifying;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.minimessage.tree.Node;
import org.jetbrains.annotations.NotNull;

public class CenterTag implements Modifying {
    private static final String CENTER = "centered";
    static final TagResolver RESOLVER = TagResolver.resolver(CENTER, CenterTag::create);

    public static MiniMessage centerTagMM = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolvers(RESOLVER, StandardTags.defaults())
                    .build())
            .build();

    public char centerChar = ' ';
    public int length = 266;
    public boolean visited;

    static Tag create(final ArgumentQueue args, final Context ctx) {

        int length = 266;
        char centerChar = ' ';
        if (args.hasNext())
            length = Integer.parseInt(args.pop().value());

        if (args.hasNext())
            centerChar = args.pop().value().charAt(0);

        return new CenterTag(length, centerChar);
    }

    private CenterTag(int length, char centerChar) {
        this.length = length;
        this.centerChar = centerChar;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.length, this.centerChar);
    }

    @Override
    public Component apply(@NotNull Component current, int depth) {
        if (depth == 0) {
            current = CenterUtil.centeredComponent(current, 266,  ' ');
        }
        else current = Component.empty();
        /*Bukkit.broadcast(Component.text("apply ").append(Component.text(depth))
                .append(Component.space()).append(current));*/
        return current;
    }

    @Override
    public final void visit(final @NotNull Node current, final int depth) {
        /*if (this.visited) {
            throw new IllegalStateException("Color changing tag instances cannot be re-used, return a new one for each resolve");
        }
        Bukkit.broadcast(Component.text("visit ").append(Component.text(depth))
                .append(Component.space()).append(Component.text(current.toString())));*/
    }

    @Override
    public final void postVisit() {
        this.visited = true;
    }
}
