package me.usainsrht.announcement;

import me.usainsrht.announcement.command.AnnouncementCommand;
import me.usainsrht.announcement.command.CommandHandler;
import me.usainsrht.announcement.utils.CenterTag;
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
import java.util.concurrent.ThreadLocalRandom;

public final class Announcement extends JavaPlugin {
    public static Announcement instance;
    public static int announcementIndex = 0;
    public static BukkitTask timer = null;
    public static MiniMessage miniMessage;
    public static AnnouncementCommand command;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        command = new AnnouncementCommand(getConfig().getString("command.name", "announcement"),
                getConfig().getString("command.description"),
                getConfig().getString("command.usage"),
                new ArrayList<>());
        CommandHandler.register("announcement", command);

        if (getConfig().getBoolean("use_custom_minimessage", true)) {
            miniMessage = CenterTag.centerTagMM;
        } else {
            miniMessage = MiniMessage.miniMessage();
        }

        if (timer != null)
            timer.cancel();

        timer = new BukkitRunnable() {
            @Override
            public void run() {
                announce();
            }
        }.runTaskTimer(instance, 0, getConfig().getInt("delay", 300) * 20L);

    }

    @Override
    public void onDisable() {}

    public static void reload() {
        getInstance().reloadConfig();
        if (timer != null)
            timer.cancel();
        timer = new BukkitRunnable() {
            @Override
            public void run() {
                announce();
            }
        }.runTaskTimer(instance, 0, getInstance().getConfig().getInt("delay") * 20L);
    }

    public static Announcement getInstance() {
        return instance;
    }

    public static void announce() {
        List<String> announcements;
        if (getInstance().getConfig().isSet("announcements")) {
            announcements = getInstance().getConfig().getStringList("announcements");
        } else {
            announcements = new ArrayList<>();
        }

        if (announcements.isEmpty()) return;

        if (getInstance().getConfig().getBoolean("ordered")) {
            announce(announcements.get(announcementIndex % announcements.size()));
            announcementIndex++;
        }
        else {
            announce(announcements.get(ThreadLocalRandom.current().nextInt(announcements.size())));
        }
    }

    public static void announce(String message) {
        announce(miniMessage.deserialize(message));
    }

    public static void announce(Component message) {
        Component announcement = Component.empty();
        for (String line : getInstance().getConfig().getStringList("template")) {
            announcement = announcement.append(miniMessage.deserialize(line
            , Placeholder.component("announcement", message)));
            announcement = announcement.append(Component.newline());
        }

        if (getInstance().getConfig().getBoolean("broadcast")) {
            Bukkit.broadcast(announcement);
        }
        else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(announcement);
            }
        }
    }

}
