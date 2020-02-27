package me.leoko.advancedban.punishment;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Created by Leoko @ dev.skamps.eu on 30.05.2016.
 */
public enum PunishmentType {
    BAN("Ban", null, "ab.ban.perma", false, false, new String[]{"permaban", "banhammer"}, new String[]{"unpermaban", "unhammer"}),
    TEMP_BAN("Tempban", BAN, "ab.ban.temp", true, false, new String[]{"tban"}, new String[]{}),
    IP_BAN("Ipban", BAN, "ab.ipban.perma", false, true, new String[]{}, new String[]{}),
    TEMP_IP_BAN("Tempipban", BAN, "ab.ipban.temp", true, true, new String[]{"tipban"}, new String[]{"untipban"}),
    MUTE("Mute", null, "ab.mute.perma", false, false, new String[]{"silence"}, new String[]{"unsilence"}),
    TEMP_MUTE("Tempmute", MUTE, "ab.mute.temp", true, false, new String[]{"tmute", "tempsilence", "tsilence"}, new String[]{"untmute", "untempsilence", "untsilence"}),
    WARNING("Warn", null, "ab.warn.perma", false, false, new String[]{"caution"}, new String[]{"uncaution"}),
    TEMP_WARNING("Tempwarn", WARNING, "ab.warn.temp", true, false, new String[]{"twarn", "tempcaution", "tcaution"}, new String[]{"untwarn", "untempcaution", "untcaution"}),
    KICK("Kick", null, "ab.kick.use", false, false, new String[]{"boot"}, new String[]{});

    private final String name;
    private final String command;
    private final String perms;
    private final PunishmentType basic;
    private final boolean temp;
    private final boolean ip;
    private String[] aliases;
    private String[] unaliases;

    PunishmentType(String name, PunishmentType basic, String perms, boolean temp, boolean ip, String[] aliases, String[] unaliases) {
        this.name = name;
        this.command = name.toLowerCase();
        this.basic = basic;
        this.perms = perms;
        this.temp = temp;
        this.ip = ip;
        this.aliases = aliases;
        this.unaliases = unaliases;
    }

    public String getName() {
        return name.toLowerCase();
    }

    public String getCommand() {
        return command;
    }

    public String[] getAliases() {
        return aliases;
    }

    public String[] getUnaliases() {
        return unaliases;
    }

    public String getPerms() {
        return perms;
    }

    public String getConfSection() {
        return name;
    }

    public PunishmentType getBasic() {
        return basic == null ? this : basic;
    }

    public boolean isTemp() {
        return temp;
    }

    public boolean isIp() {
        return ip;
    }

    @JsonValue
    public int getValue() {
        return ordinal();
    }
}