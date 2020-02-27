package me.leoko.advancedban.nukkit;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import me.leoko.advancedban.AdvancedBanCommandSender;
import me.leoko.advancedban.manager.CommandManager;

public class NukkitAdvancedBanCommand extends Command {

    public NukkitAdvancedBanCommand(String commandName) {
        super(commandName);
    }

    @Override
    public boolean execute(CommandSender sender, String cmd, String[] args) {
        final AdvancedBanCommandSender commandSender = new NukkitAdvancedBanCommandSender(sender);
        CommandManager.getInstance().processCommand(commandSender, cmd, args);
        return true;
    }
}
