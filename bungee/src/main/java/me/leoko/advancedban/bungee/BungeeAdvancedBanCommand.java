package me.leoko.advancedban.bungee;

import me.leoko.advancedban.AdvancedBanCommandSender;
import me.leoko.advancedban.manager.CommandManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class BungeeAdvancedBanCommand extends Command {

    public BungeeAdvancedBanCommand(String name) {
        super(name);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        final AdvancedBanCommandSender commandSender = new BungeeAdvancedBanCommandSender(sender);
        CommandManager.getInstance().processCommand(commandSender, this.getName(), args);
    }
}
