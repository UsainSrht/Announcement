package com.purpurmc.announcement.command;

import com.purpurmc.announcement.Announcement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AnnouncementCommand extends Command {

    public AnnouncementCommand(@NotNull String name, @NotNull String description, @NotNull String usageMessage, @NotNull List<String> aliases) {
        super(name, description, usageMessage, aliases);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String command, String[] args) {
        if (!sender.hasPermission(Announcement.getInstance().getConfig().getString("command.permission"))) return true;
        if (args.length == 0) return true;
        if (args[0].equalsIgnoreCase("reload")) {
            Announcement.reload();
            sender.sendMessage(Component.text("Config reloaded successfully!").color(NamedTextColor.GREEN));
        }
        else
            Announcement.announce(String.join(" ", args));
        return true;
    }
}
