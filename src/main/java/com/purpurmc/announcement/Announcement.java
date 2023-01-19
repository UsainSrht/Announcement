package com.purpurmc.announcement;

import com.purpurmc.announcement.command.AnnouncementCommand;
import com.purpurmc.announcement.command.CommandHandler;
import com.purpurmc.announcement.utils.CenterTag;
import com.purpurmc.bridge.Bridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Announcement extends JavaPlugin {
    public static Announcement instance;
    public static List<Component> componentAnnouncements;
    public static boolean hasBridge;
    public static int announcementIndex = 0;
    public static BukkitTask timer = null;
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        cacheAnnouncements();
        CommandHandler.register(new AnnouncementCommand(getConfig().getString("command.name"),
                getConfig().getString("command.description"),
                getConfig().getString("command.usage"),
                new ArrayList<>()));
        hasBridge = (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null);
        if (timer != null)
            timer.cancel();
        timer = new BukkitRunnable() {
            @Override
            public void run() {
                announce();
            }
        }.runTaskTimer(instance, 0, getConfig().getInt("delay") * 20L);
        CenterTag.centerTagMM.deserialize("<centered>helloğ");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static void reload() {
        getInstance().reloadConfig();
        cacheAnnouncements();
        if (timer != null)
            timer.cancel();
        timer = new BukkitRunnable() {
            @Override
            public void run() {
                announce();
            }
        }.runTaskTimer(instance, 0, getInstance().getConfig().getInt("delay") * 20L);
    }

    public static void cacheAnnouncements() {
        componentAnnouncements = new ArrayList<>();
        List<String> announcements = getInstance().getConfig().getStringList("announcements");
        announcements.forEach((announcement) ->
                componentAnnouncements.add(MiniMessage.miniMessage().deserialize("<gray>" + announcement)));

    }

    public static Announcement getInstance() {
        return instance;
    }

    public static void announce() {
        if (getInstance().getConfig().getBoolean("ordered")) {
            announce(componentAnnouncements.get(announcementIndex % componentAnnouncements.size()));
            announcementIndex++;
        }
        else {
            Random random = new Random();
            announce(componentAnnouncements.get(random.nextInt(componentAnnouncements.size())));
        }
    }
    public static void announce(String message) {
        announce(CenterTag.centerTagMM.deserialize(message));
    }
    public static void announce(Component message) {
        Component announcement = Component.empty();
        for (String line : getInstance().getConfig().getStringList("template")) {
            announcement = announcement.append(CenterTag.centerTagMM.deserialize(line
            , Placeholder.component("announcement", message)));
            announcement = announcement.append(Component.newline());
        }
        if (getInstance().getConfig().getBoolean("broadcast")) {
            Bukkit.broadcast(announcement);
        }
        else if (Bukkit.getOnlinePlayers().size() > 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (hasBridge)
                    if (Bridge.getSetting(player.getName(), "announcement").getAsBoolean())
                        continue;
                player.sendMessage(announcement);
            }
        }
    }
}
