package me.leoko.advancedban.commands;

import me.leoko.advancedban.AdvancedBan;
import me.leoko.advancedban.AdvancedBanLogger;
import me.leoko.advancedban.manager.MessageManager;
import me.leoko.advancedban.punishment.Punishment;
import me.leoko.advancedban.punishment.PunishmentManager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class ListCommand implements Consumer<Command.CommandInput> {
    private Function<Object, List<Punishment>> listSupplier;
    private String config;
    private boolean history;
    private boolean hasTarget;

    public ListCommand(Function<Object, List<Punishment>> listSupplier, String config, boolean history, boolean hasTarget) {
        this.listSupplier = listSupplier;
        this.config = config;
        this.history = history;
        this.hasTarget = hasTarget;
    }

    @Override
    public void accept(Command.CommandInput input) {
        String name = input.getPrimary();
        Object target = null;
        if (hasTarget) {
            if (input.getPrimary().matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
                try {
                    target = InetAddress.getByName(input.getPrimary());
                } catch (UnknownHostException e) {
                    AdvancedBanLogger.getInstance().logException(e);
                    return;
                }
            } else {
                target = CommandUtils.processName(input);
                if (target == null)
                    return;
            }
        }

        final List<Punishment> punishments = listSupplier.apply(target);
        if (punishments.isEmpty()) {
            input.getSender().sendCustomMessage(config + ".NoEntries", true, "NAME", name);
            return;
        }

        for (Punishment punishment : punishments)
            if (punishment.isExpired())
                PunishmentManager.getInstance().deletePunishment(punishment, true);

        int page = input.hasNext() ? Integer.parseInt(input.getPrimary()) : 1;
        if (punishments.size() / 5.0 + 1 <= page) {
            input.getSender().sendCustomMessage(config + ".OutOfIndex", true, "PAGE", page);
            return;
        }

        String prefix = MessageManager.getMessage("General.Prefix");
        List<String> header = MessageManager.getMessageList(config + ".Header",
                "PREFIX", prefix, "NAME", name);

        header.forEach(input.getSender()::sendMessage);


        SimpleDateFormat format = new SimpleDateFormat(AdvancedBan.get().getConfiguration().getDateFormat());

        for (int i = (page - 1) * 5; i < page * 5 && punishments.size() > i; i++) {
            Punishment punishment = punishments.get(i);
            List<String> entryLayout = MessageManager.getMessageList(config + ".Entry",
                    "PREFIX", prefix,
                    "NAME", punishment.getName(),
                    "DURATION", PunishmentManager.getInstance().getDuration(punishment, history),
                    "OPERATOR", punishment.getOperator(),
                    "REASON", punishment.getReason().orElse("-"),
                    "TYPE", punishment.getType().getConfSection(),
                    "ID", punishment.getId().orElse(-1) + "",
                    "DATE", format.format(new Date(punishment.getStart())));

            entryLayout.forEach(input.getSender()::sendMessage);
        }

        input.getSender().sendCustomMessage(config + ".Footer", false,
                "CURRENT_PAGE", page,
                "TOTAL_PAGES", (punishments.size() / 5 + (punishments.size() % 5 != 0 ? 1 : 0)),
                "COUNT", punishments.size());

        if (punishments.size() / 5.0 + 1 > page + 1) {
            input.getSender().sendCustomMessage(config + ".PageFooter", false,
                    "NEXT_PAGE", (page + 1),
                    "NAME", name);
        }
    }
}
