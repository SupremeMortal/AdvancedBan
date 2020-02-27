package me.leoko.advancedban.command;

import me.leoko.advancedban.punishment.PunishmentType;

public class UnBanCommand extends UnPunishmentTypeCommand {

    public UnBanCommand() {
        super(PunishmentType.BAN);
    }
}
