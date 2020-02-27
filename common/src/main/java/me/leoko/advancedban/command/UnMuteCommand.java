package me.leoko.advancedban.command;

import me.leoko.advancedban.punishment.PunishmentType;

public class UnMuteCommand extends UnPunishmentTypeCommand {

    public UnMuteCommand() {
        super(PunishmentType.MUTE);
    }
}
