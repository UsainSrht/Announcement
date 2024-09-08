package me.usainsrht.announcement.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;

public class CommandHandler {

    public static void register(String registrar, Command command) {

        Bukkit.getCommandMap().register(registrar, command);

    }

}