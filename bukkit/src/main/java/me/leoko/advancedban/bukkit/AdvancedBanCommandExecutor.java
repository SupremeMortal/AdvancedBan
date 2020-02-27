package me.leoko.advancedban.bukkit;

import me.leoko.advancedban.AdvancedBanCommandSender;
import me.leoko.advancedban.manager.CommandManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AdvancedBanCommandExecutor implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Automatic case correction for player names
        if (args.length > 0)
            args[0] = (Bukkit.getPlayer(args[0]) != null ? Bukkit.getPlayer(args[0]).getName() : args[0]);

        final AdvancedBanCommandSender commandSender = new BukkitAdvancedBanCommandSender(sender);
        CommandManager.getInstance().processCommand(commandSender, command.getName(), args);
        return true;
    }
}
