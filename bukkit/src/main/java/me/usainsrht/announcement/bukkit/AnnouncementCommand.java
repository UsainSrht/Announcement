package me.usainsrht.announcement.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AnnouncementCommand extends Command {
    private final Announcement plugin;

    public AnnouncementCommand(Announcement plugin, String name, String description, String usage) {
        super(name, description, usage, new ArrayList<String>());
        this.plugin = plugin;
        String permission = plugin.commandPermission();
        if (permission != null) setPermission(permission);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        String permission = getPermission();
        if (permission != null && !sender.hasPermission(permission)) return true;
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + getUsage());
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (plugin.reload()) sender.sendMessage(ChatColor.GREEN + "Config reloaded successfully!");
            else sender.sendMessage(ChatColor.RED + "Reload failed, see the console.");
        } else {
            plugin.announce(String.join(" ", args));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) return Collections.singletonList("reload");
        return Collections.emptyList();
    }
}
