package me.leoko.advancedban.bungee;

import lombok.RequiredArgsConstructor;
import me.leoko.advancedban.AdvancedBanCommandSender;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;

@RequiredArgsConstructor
public class BungeeAdvancedBanCommandSender implements AdvancedBanCommandSender {
    private final CommandSender sender;

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public void sendMessage(String message) {
        sender.sendMessage(message);
    }

    @Override
    public boolean executeCommand(String command) {
        return ProxyServer.getInstance().getPluginManager().dispatchCommand(sender, command);
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }
}
