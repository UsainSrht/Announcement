package com.purpurmc.announcement.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public class CenterUtil {

    public static int getWidth(char c) {
        int width;
        switch (c) {
            case 'f', 'k', '<', '>' -> width = 4;
            case 'ı', 'i', '!', ':', ';', '\'', '|', '.', ',' -> width = 1;
            case 'I', 'İ', 't', '*', '"', '(', ')', '{', '}', '[', ']', ' ' -> width = 3;
            case 'l', '`' -> width = 2;
            case '@' -> width = 6;
            default -> width = 5;
        }
        return width;
    }
    public static int getWidth(String text) {
        int width = 0;
        String[] list = text.split("§l");
        int bold = 0;
        for (String string : list) {
            bold++;
            String unformatted = ChatColor.stripColor(string);
            char[] chars = new char[unformatted.length()];
            unformatted.getChars(0, unformatted.length(), chars, 0);
            for (char c : chars) {
                width += getWidth(c);
                if (bold % 2 == 0) {
                    width++;
                }
            }
        }
        return width;
    }
    public static String centeredText(String text, int length, char centerChar) {
        int compensated = 0;
        StringBuilder centered = new StringBuilder();
        int width = getWidth(text);
        while (compensated < ((length - width) / 2)) {
            centered.append(centerChar);
            compensated += getWidth(centerChar) + 1;
        }
        return centered + text;
    }

    public static int getComponentWidth(Component component) {
        int width = getWidth(PlainTextComponentSerializer.plainText().serialize(component));
        for (Component child : getBolds(component)) {
            width += PlainTextComponentSerializer.plainText().serialize(child).length();
        }
        return width;
    }

    public static List<Component> getBolds(Component component) {
        List<Component> newList = new ArrayList<>();
        for (Component child : component.children()) {
            if (child.hasDecoration(TextDecoration.BOLD)) {
                newList.add(child);
            }
        }
        return newList;
    }

    public static Component centeredComponent(Component component, int length, char centerChar) {
        int compensated = 0;
        int width = getComponentWidth(component);
        StringBuilder centered = new StringBuilder();
        while (compensated < ((length - width) / 2)) {
            centered.append(centerChar);
            compensated += getWidth(centerChar) + 1;
        }
        return Component.text(centered.toString()).append(component);
    }
}
