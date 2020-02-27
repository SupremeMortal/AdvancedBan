package me.leoko.advancedban.commands;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;
import me.leoko.advancedban.AdvancedBan;
import me.leoko.advancedban.AdvancedBanLogger;
import me.leoko.advancedban.manager.UUIDManager;
import me.leoko.advancedban.punishment.Punishment;
import me.leoko.advancedban.punishment.PunishmentManager;
import me.leoko.advancedban.punishment.PunishmentType;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;

@UtilityClass
public class CommandUtils {
    public Optional<Punishment> getPunishment(Object target, PunishmentType type) {
        return type == PunishmentType.MUTE
                ? PunishmentManager.getInstance().getMute(target)
                : PunishmentManager.getInstance().getInterimBan(target);
    }

    // Removes name argument and returns uuid (null if failed)
    public UUID processName(Command.CommandInput input) {
        String name = input.next();
        UUID uuid = UUIDManager.getInstance().getUuid(name.toLowerCase()).orElse(null);

        if (uuid == null)
            input.getSender().sendCustomMessage("General.FailedFetch", true, "NAME", name);

        return uuid;
    }

    // Removes name/ip argument and returns ip (null if failed)
    public InetAddress processIP(Command.CommandInput input) {
        String name = input.next().toLowerCase();
        if (name.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
            try {
                return InetAddress.getByName(name);
            } catch (UnknownHostException e) {
                AdvancedBanLogger.getInstance().logException(e);
                return null;
            }
        } else {
            InetAddress ip = AdvancedBan.get().getAddress(name).orElse(null);

            if (ip == null)
                input.getSender().sendCustomMessage("Ipban.IpNotCashed", true, "NAME", name);

            return ip;
        }
    }

    // Builds reason from remaining arguments (null if failed)
    public String processReason(Command.CommandInput input) {
        String reason = String.join(" ", input.getArgs());


        if (reason.matches("[~@].+")) {
            JsonNode layout = AdvancedBan.get().getLayouts().getLayout("Message." + reason.substring(1));
            if(layout.isMissingNode()) {
                input.getSender().sendCustomMessage("General.LayoutNotFound",
                        true, "NAME", reason.substring(1));
                return null;
            }
        }

        return reason;
    }
}
