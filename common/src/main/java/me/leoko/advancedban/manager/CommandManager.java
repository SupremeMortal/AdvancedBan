package me.leoko.advancedban.manager;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.leoko.advancedban.AdvancedBan;
import me.leoko.advancedban.AdvancedBanCommandSender;
import me.leoko.advancedban.commands.Command;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandManager {
    private final Set<String> activeCommands = new HashSet<>();

    @Getter
    private static final CommandManager instance = new CommandManager();

    public void onEnable() {
        for (Command command : Command.values()) {
            Collections.addAll(activeCommands, command.getNames());
            AdvancedBan.get().registerCommand(command.getNames()[0]);
        }
    }

    public boolean isAdvancedBanCommand(String name){
        return activeCommands.contains(name.toLowerCase());
    }

    public void processCommand(AdvancedBanCommandSender commandSender, String commandName, String[] args) {
        AdvancedBan.get().runAsyncTask(() -> {
            Command command = Command.getByName(commandName);
            if (command == null)
                return;

            String permission = command.getPermission();
            if (permission != null && !commandSender.hasPermission(permission)) {
                commandSender.sendCustomMessage("General.NoPerms", true);
                return;
            }

            if (!command.validateArguments(args)) {
                commandSender.sendCustomMessage(command.getUsagePath(), true);
                return;
            }

            command.execute(commandSender, args);
        });
    }
}
