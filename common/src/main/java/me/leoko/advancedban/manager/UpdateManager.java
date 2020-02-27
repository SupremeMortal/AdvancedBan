package me.leoko.advancedban.manager;

import lombok.experimental.UtilityClass;
import me.leoko.advancedban.AdvancedBan;
import me.leoko.advancedban.AdvancedBanLogger;
import me.leoko.advancedban.utils.SQLQuery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

@UtilityClass
public class UpdateManager {

    private int startsWith(List<String> lines, String startsWith) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(startsWith)) {
                return i;
            }
        }
        return -1;
    }

    public boolean migrateFiles() {
        if (AdvancedBan.get().isUnitTesting()) return false;

        DatabaseManager.getInstance().executeStatement(SQLQuery.FIX_TABLE_PUNISHMENT);
        DatabaseManager.getInstance().executeStatement(SQLQuery.FIX_TABLE_PUNISHMENT_HISTORY);

        boolean checkUndoNotification = false;
        boolean checkMuteReason = false;
        boolean checkBanReason = false;
        boolean checkTempIpBan = false;

        boolean anyChanges = false;

        try {
            checkUndoNotification = AdvancedBan.get().getMessages().getMessage("UnBan.Notification").isMissingNode();
        } catch (Exception e) {
            //ignore
        }

        try {
            checkMuteReason = AdvancedBan.get().getMessages().getMessage("Check.MuteReason").isMissingNode();
        } catch (Exception e) {
            //ignore
        }

        try {
            checkBanReason = AdvancedBan.get().getMessages().getMessage("Check.BanReason").isMissingNode();
        } catch (Exception e) {
            //ignore
        }

        try {
            checkTempIpBan = AdvancedBan.get().getMessages().getMessage("Tempipban").isMissingNode();
        } catch (Exception e) {
            //ignore
        }

        Path messagesPath = AdvancedBan.get().getDataFolderPath().resolve("Messages.yml");

        try {
            List<String> lines = Files.readAllLines(messagesPath);

            if(checkUndoNotification){
                anyChanges = true;
                lines.add(lines.indexOf("UnBan:") + 1, "  Notification: \"&e&o%OPERATOR% &7unbanned &c&o%NAME%\"");
                lines.add(lines.indexOf("UnMute:") + 1, "  Notification: \"&e&o%OPERATOR% &7unmuted &c&o%NAME%\"");
                lines.add(lines.indexOf("UnWarn:") + 1, "  Notification: \"&e&o%OPERATOR% &7unwarned &c&o%NAME%\"");
            }

            if (checkMuteReason) {
                anyChanges = true;
                int index = lines.indexOf("Check:");
                lines.add(index + 1, "  MuteReason: \"  &cReason &8\\xbb &7%REASON%\"");
            }

            if (checkBanReason) {
                anyChanges = true;
                int index = lines.indexOf("Check:");
                lines.add(index + 1, "  BanReason: \"  &cReason &8\\xbb &7%REASON%\"");
            }

            if (checkTempIpBan) {
                anyChanges = true;
                List<String> tempIpBan = Arrays.asList(
                        "",
                        "Tempipban:",
                        "  Usage: \"&cUsage &8\\xbb &7&o/tempipban [Name/IP] [Xmo/Xd/Xh/Xm/Xs/#TimeLayout] [Reason/@Layout]\"",
                        "  MaxDuration: \"&cYou are not able to ban more than %MAX%sec\"",
                        "  Layout:",
                        "  - '%PREFIX% &7Temporarily banned'",
                        "  - '&7'",
                        "  - '&7'",
                        "  - \"&cReason &8\\xbb &7%REASON%\"",
                        "  - \"&cDuration &8\\xbb &7%DURATION%\"",
                        "  - '&7'",
                        "  - '&8Unban application in TS or forum'",
                        "  - \"&eTS-Ip &8\\xbb &c&ncoming soon\"",
                        "  - \"&eForum &8\\xbb &c&ncoming soon\"",
                        "  Notification:",
                        "  - \"&c&o%NAME% &7got banned by &e&o%OPERATOR%\"",
                        "  - \"&7For the reason &o%REASON%\"",
                        "  - \"&7&oThis player got banned for &e&o%DURATION%\"",
                        "",
                        "ChangeReason:",
                        "  Usage: \"&cUsage &8\\xbb &7&o/change-reason [ID or ban/mute USER] [New reason]\"",
                        "  Done: \"&7Punishment &a&o#%ID% &7has successfully been updated!\"",
                        "  NotFound: \"&cSorry we have not been able to find this punishment\"");
                lines.addAll(tempIpBan);
            }

            if(checkBanReason || checkMuteReason || checkTempIpBan || checkUndoNotification)
                Files.write(messagesPath, lines, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            AdvancedBanLogger.getInstance().warn("Unable to update Messages.yml. Check logs for more info");
            AdvancedBanLogger.getInstance().logException(e);
        }

        try {
            Path configPath = AdvancedBan.get().getDataFolderPath().resolve("config.yml");
            List<String> lines = Files.readAllLines(configPath);
            boolean change = false;

            if (startsWith(lines, "EnableAllPermissionNodes:") == -1) {
                lines.remove("  # Disable for cracked servers");

                int indexOf = lines.indexOf("UUID-Fetcher:");
                if (indexOf != -1) {
                    lines.addAll(indexOf + 1, Arrays.asList(
                            "  # If dynamic it set to true it will override the 'enabled' and 'intern' settings",
                            "  # and automatically detect the best possible uuid fetcher settings for your server.",
                            "  # Our recommendation: don't set dynamic to false if you don't have any problems.",
                            "  Dynamic: true"));
                }

                lines.addAll(Arrays.asList("",
                        "# This is useful for bungeecord servers or server with permission systems which do not support *-Perms",
                        "# So if you enable this you can use ab.all instead of ab.* or ab.ban.all instead of ab.ban.*",
                        "# This does not work with negative permissions! e.g. -ab.all would not block all commands for that user.",
                        "EnableAllPermissionNodes: false"));
                change = true;
            }
            if (startsWith(lines, "Debug:") == -1) {
                lines.addAll(Arrays.asList(
                        "",
                        "# With this active will show more information in the console, such as errors, if",
                        "# the plugin works correctly is not recommended to activate it since it is",
                        "# designed to find bugs.",
                        "Debug: false"));
                change = true;
            }
            if (startsWith(lines, "Logs Purge Days:") == -1) {
                lines.removeAll(Arrays.asList(
                        "",
                        "# This is the amount of days that we should keep plugin logs in the plugins/AdvancedBan/logs folder.",
                        "# By default is set to 10 days.",
                        "Logs Purge Days: 10"
                ));
                change = true;
            }
            if (startsWith(lines, "Disable Prefix:") == -1) {
                lines.addAll(Arrays.asList(
                        "",
                        "# Removes the prefix of the plugin in every message.",
                        "Disable Prefix: false"
                ));
                change = true;
            }

            if (change) {
                Files.write(configPath, lines, StandardOpenOption.TRUNCATE_EXISTING);
            }

            anyChanges = anyChanges || change;
        } catch (IOException e) {
            AdvancedBanLogger.getInstance().warn("Unable to update config.yml. Check logs for more info");
            AdvancedBanLogger.getInstance().logException(e);
        }

        return anyChanges;
    }
}
