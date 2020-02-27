package me.leoko.advancedban.command;

import me.leoko.advancedban.AdvancedBanCommandSender;
import me.leoko.advancedban.punishment.Punishment;
import me.leoko.advancedban.punishment.PunishmentType;
import me.leoko.advancedban.utils.CommandUtils;

import java.util.List;
import java.util.Optional;

public abstract class UnPunishmentTypeCommand extends PunishmentTypeCommand {

    UnPunishmentTypeCommand(PunishmentType type) {
        super(type, type.getUnaliases());
    }

    @Override
    public boolean onCommand(AdvancedBanCommandSender sender, String[] args) {
        if (args.length == 1) {
            Optional identifier = CommandUtils.getIdentifier(sender.getAdvancedBan(), args[0]);

            if (!identifier.isPresent()) {
                sender.sendCustomMessage("General.FailedFetch", true, "NAME", args[0]);
                return true;
            }

            List<Punishment> punishments = sender.getAdvancedBan().getPunishmentManager().getPunishments(identifier.get(), getType(), true);
            if (!punishments.isEmpty()) {
                for (Punishment punishment : punishments) {
                    sender.getAdvancedBan().getPunishmentManager().deletePunishment(punishment, true);
                }
                sender.sendCustomMessage(getConfigSection() + ".Done", true, "NAME", args[0]);
            } else {
                sender.sendCustomMessage(getConfigSection() + ".NotPunished", true, "NAME", args[0]);
            }
            return true;
        }
        return false;
    }
}
