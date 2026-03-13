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
import me.clip.placeholderapi.PlaceholderAPI;
import space.arim.morepaperlib.MorePaperLib;
import space.arim.morepaperlib.scheduling.ScheduledTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Announcement extends JavaPlugin {
    public static Announcement instance;
    public static int announcementIndex = 0;
    public static ScheduledTask timer = null;
    public static MiniMessage miniMessage;
    public static AnnouncementCommand command;
    public static MorePaperLib morePaperLib;
    public static boolean papiEnabled = false;

    @Override
    public void onEnable() {
        instance = this;
        morePaperLib = new MorePaperLib(this);

        saveDefaultConfig();

        papiEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        command = new AnnouncementCommand(getConfig().getString("command.name"),
                getConfig().getString("command.description"),
                getConfig().getString("command.usage"),
                new ArrayList<>());
        CommandHandler.register("announcement", command);

        setupMiniMessage();

        if (timer != null)
            timer.cancel();

        timer = morePaperLib.scheduling().globalRegionalScheduler()
                .runAtFixedRate(() -> announce(), 1L, getConfig().getInt("delay") * 20L);

    }

    @Override
    public void onDisable() {}

    public static void reload() {
        getInstance().reloadConfig();
        getInstance().setupMiniMessage();
        if (timer != null)
            timer.cancel();
        timer = morePaperLib.scheduling().globalRegionalScheduler()
                .runAtFixedRate(() -> announce(), 1L, getInstance().getConfig().getInt("delay") * 20L);
    }

    public static Announcement getInstance() {
        return instance;
    }

    public void setupMiniMessage() {
        if (getConfig().getBoolean("use_custom_minimessage", true)) {
            miniMessage = CenterTag.centerTagMM;
        } else {
            miniMessage = MiniMessage.miniMessage();
        }
    }

    public static void announce() {
        List<String> announcements = getInstance().getConfig().getStringList("announcements");
        if (getInstance().getConfig().getBoolean("ordered")) {
            announce(announcements.get(announcementIndex % announcements.size()));
            announcementIndex++;
        }
        else {
            announce(announcements.get(ThreadLocalRandom.current().nextInt(announcements.size())));
        }
    }

    public static void announce(String message) {
        if (papiEnabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String papiMessage = PlaceholderAPI.setPlaceholders(player, message);
                Component msgComponent = miniMessage.deserialize(papiMessage);
                Component announcement = Component.empty();
                for (String line : getInstance().getConfig().getStringList("template")) {
                    line = PlaceholderAPI.setPlaceholders(player, line);
                    announcement = announcement.append(miniMessage.deserialize(line,
                            Placeholder.component("announcement", msgComponent)));
                    announcement = announcement.append(Component.newline());
                }
                player.sendMessage(announcement);
            }
            if (getInstance().getConfig().getBoolean("broadcast")) {
                Component msgComponent = miniMessage.deserialize(message);
                Component announcement = Component.empty();
                for (String line : getInstance().getConfig().getStringList("template")) {
                    announcement = announcement.append(miniMessage.deserialize(line,
                            Placeholder.component("announcement", msgComponent)));
                    announcement = announcement.append(Component.newline());
                }
                Bukkit.getConsoleSender().sendMessage(announcement);
            }
        } else {
            announce(miniMessage.deserialize(message));
        }
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
