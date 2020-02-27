package me.leoko.advancedban.commands;

import me.leoko.advancedban.AdvancedBanLogger;
import me.leoko.advancedban.punishment.Punishment;
import me.leoko.advancedban.punishment.PunishmentManager;
import me.leoko.advancedban.punishment.PunishmentType;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Consumer;

public class RevokePunishmentCommand implements Consumer<Command.CommandInput> {
    private PunishmentType type;

    public RevokePunishmentCommand(PunishmentType type) {
        this.type = type;
    }

    @Override
    public void accept(Command.CommandInput input) {
        String name = input.getPrimary();

        Object target;
        if (name.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
            try {
                target = InetAddress.getByName(name);
            } catch (UnknownHostException e) {
                AdvancedBanLogger.getInstance().logException(e);
                return;
            }
        } else {
            target = CommandUtils.processName(input);
            if (target == null)
                return;
        }

        Punishment punishment = CommandUtils.getPunishment(target, type).orElse(null);
        if (punishment == null) {
            input.getSender().sendCustomMessage("Un" + type.getConfSection() + ".NotPunished",
                    true, "NAME", name);
            return;
        }

        final String operator = input.getSender().getName();
        PunishmentManager.getInstance().deletePunishment(punishment, operator);
        input.getSender().sendCustomMessage("Un" + type.getConfSection() + ".Done",
                true, "NAME", name);
    }
}
