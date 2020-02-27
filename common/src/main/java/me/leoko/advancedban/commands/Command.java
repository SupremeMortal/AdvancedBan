package me.leoko.advancedban.commands;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.leoko.advancedban.AdvancedBan;
import me.leoko.advancedban.AdvancedBanCommandSender;
import me.leoko.advancedban.AdvancedBanLogger;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.MessageManager;
import me.leoko.advancedban.manager.UUIDManager;
import me.leoko.advancedban.punishment.Punishment;
import me.leoko.advancedban.punishment.PunishmentManager;
import me.leoko.advancedban.punishment.PunishmentType;
import me.leoko.advancedban.utils.GeoLocation;
import me.leoko.advancedban.utils.SQLQuery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public enum Command {
    BAN(
            PunishmentType.BAN.getPerms(),
            ".+",
            new PunishmentCommand(PunishmentType.BAN),
            PunishmentType.BAN.getConfSection("Usage"),
            "ban"),

    TEMP_BAN(
            PunishmentType.TEMP_BAN.getPerms(),
            "\\S+ ([1-9][0-9]*([wdhms]|mo)|#\\S+)( .*)?",
            new PunishmentCommand(PunishmentType.TEMP_BAN),
            PunishmentType.TEMP_BAN.getConfSection("Usage"),
            "tempban"),

    IP_BAN(
            PunishmentType.IP_BAN.getPerms(),
            ".+",
            new PunishmentCommand(PunishmentType.IP_BAN),
            PunishmentType.IP_BAN.getConfSection("Usage"),
            "ipban", "banip", "ban-ip"),

    TEMP_IP_BAN(
            PunishmentType.TEMP_IP_BAN.getPerms(),
            ".+",
            new PunishmentCommand(PunishmentType.TEMP_IP_BAN),
            PunishmentType.TEMP_IP_BAN.getConfSection("Usage"),
            "tempipban", "tipban"),

    MUTE(
            PunishmentType.MUTE.getPerms(),
            ".+",
            new PunishmentCommand(PunishmentType.MUTE),
            PunishmentType.MUTE.getConfSection("Usage"),
            "mute"),

    TEMP_MUTE(
            PunishmentType.TEMP_MUTE.getPerms(),
            "\\S+ ([1-9][0-9]*([wdhms]|mo)|#\\S+)( .*)?",
            new PunishmentCommand(PunishmentType.TEMP_MUTE),
            PunishmentType.TEMP_MUTE.getConfSection("Usage"),
            "tempmute"),

    WARN(
            PunishmentType.WARNING.getPerms(),
            ".+",
            new PunishmentCommand(PunishmentType.WARNING),
            PunishmentType.WARNING.getConfSection("Usage"),
            "warn"),

    TEMP_WARN(
            PunishmentType.TEMP_WARNING.getPerms(),
            "\\S+ ([1-9][0-9]*([wdhms]|mo)|#\\S+)( .*)?",
            new PunishmentCommand(PunishmentType.TEMP_WARNING),
            PunishmentType.TEMP_WARNING.getConfSection("Usage"),
            "tempwarn"),

    KICK(
            PunishmentType.KICK.getPerms(),
            ".+",
            input -> {
                if (!AdvancedBan.get().isOnline(input.getPrimaryData())) {
                    input.getSender().sendCustomMessage("Kick.NotOnline", true,
                            "NAME", input.getPrimary());
                    return;
                }

                new PunishmentCommand(PunishmentType.KICK).accept(input);
            },
            PunishmentType.KICK.getConfSection("Usage"),
            "kick"),

    UN_BAN("ab." + PunishmentType.BAN.getName() + ".undo",
            "\\S+",
            new RevokePunishmentCommand(PunishmentType.BAN),
            "Un" + PunishmentType.BAN.getConfSection("Usage"),
            "unban"),

    UN_MUTE("ab." + PunishmentType.MUTE.getName() + ".undo",
            "\\S+",
            new RevokePunishmentCommand(PunishmentType.MUTE),
            "Un" + PunishmentType.MUTE.getConfSection("Usage"),
            "unmute"),

    UN_WARN("ab." + PunishmentType.WARNING.getName() + ".undo",
            "[0-9]+|(?i:clear \\S+)",
            input -> {
                final String confSection = PunishmentType.WARNING.getConfSection();
                if (input.getPrimaryData().equals("clear")) {
                    input.next();
                    String name = input.getPrimary();
                    UUID uuid = CommandUtils.processName(input);
                    if (uuid == null)
                        return;

                    List<Punishment> punishments = PunishmentManager.getInstance().getWarns(uuid);
                    if (!punishments.isEmpty()) {
                        input.getSender().sendCustomMessage("Un" + confSection + ".Clear.Empty",
                                true, "NAME", name);
                        return;
                    }

                    String operator = input.getSender().getName();
                    for (Punishment punishment : punishments) {
                        PunishmentManager.getInstance().deletePunishment(punishment, operator);
                    }
                    input.getSender().sendCustomMessage("Un" + confSection + ".Clear.Done",
                            true, "COUNT", String.valueOf(punishments.size()));
                } else {
                    new RevokePunishmentIDCommand("Un" + confSection, PunishmentManager.getInstance()::getWarn).accept(input);
                }
            },
            "Un" + PunishmentType.WARNING.getConfSection("Usage"),
            "unwarn"),

    UN_PUNISH("ab.all.undo",
            "[0-9]+",
            new RevokePunishmentIDCommand("UnPunish", PunishmentManager.getInstance()::getPunishment),
            "UnPunish.Usage",
            "unpunish"),

    CHANGE_REASON("ab.changeReason",
            "([0-9]+|(ban|mute) \\S+) .+",
            input -> {
                Optional<Punishment> punishment;

                if (input.getPrimaryData().matches("[0-9]*")) {
                    input.next();
                    int id = Integer.parseInt(input.getPrimaryData());

                    punishment = PunishmentManager.getInstance().getPunishment(id);
                } else {
                    PunishmentType type = PunishmentType.valueOf(input.next());

                    Object target;
                    if (!input.getPrimary().matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
                        target = CommandUtils.processName(input);
                        if (target == null)
                            return;
                    } else {
                        try {
                            target = InetAddress.getByName(input.getPrimary());
                        } catch (UnknownHostException e) {
                            AdvancedBanLogger.getInstance().logException(e);
                            return;
                        }
                        input.next();
                    }

                    punishment = CommandUtils.getPunishment(target, type);
                }

                String reason = CommandUtils.processReason(input);
                if (reason == null)
                    return;

                if (punishment.isPresent()) {
                    punishment.get().setReason(reason);
                    PunishmentManager.getInstance().updatePunishment(punishment.get());
                    input.getSender().sendCustomMessage("ChangeReason.Done",
                            true, "ID", punishment.get().getId());
                } else {
                    input.getSender().sendCustomMessage("ChangeReason.NotFound", true);
                }
            },
            "ChangeReason.Usage",
            "change-reason"),

    BAN_LIST("ab.banlist",
            "([1-9][0-9]*)?",
            new ListCommand(
                    target -> PunishmentManager.getInstance().getPunishments(SQLQuery.SELECT_ALL_PUNISHMENTS_LIMIT, 150),
                    "Banlist", false, false),
            "Banlist.Usage",
            "banlist"),

    HISTORY("ab.history",
            "\\S+( [1-9][0-9]*)?",
            new ListCommand(
                    target -> PunishmentManager.getInstance().getPunishments(target, null, false),
                    "History", true, true),
            "Banlist.Usage",
            "history"),

    WARNS(null,
            "\\S+( [1-9][0-9]*)?|\\S+",
            input -> {
                if (input.getPrimary().matches("\\S+")) {
                    if (!input.getSender().hasPermission("ab.warns.other")) {
                        input.getSender().sendCustomMessage("General.NoPerms", true);
                        return;
                    }

                    new ListCommand(
                            target -> PunishmentManager.getInstance().getPunishments(target, PunishmentType.WARNING, true),
                            "Warns", false, true).accept(input);
                } else {
                    if (!input.getSender().hasPermission("ab.warns.own")) {
                        input.getSender().sendCustomMessage("General.NoPerms", true);
                        return;
                    }

                    String name = input.sender.getName();
                    new ListCommand(
                            target -> PunishmentManager.getInstance().getPunishments(name, PunishmentType.WARNING, true),
                            "Warns", false, false).accept(input);
                }
            },
            "Warns.Usage",
            "warns"),

    CHECK("ab.check",
            "\\S+",
            input -> {
                String name = input.getPrimary();

                UUID uuid = CommandUtils.processName(input);
                if (uuid == null)
                    return;


                Optional<InetAddress> address = AdvancedBan.get().getAddress(name.toLowerCase());
                String ip = address.map(InetAddress::getHostAddress).orElse("none cashed");

                String loc = address.flatMap(GeoLocation::getLocation).orElse("failed to fetch!");
                Punishment mute = PunishmentManager.getInstance().getMute(uuid).orElse(null);
                Punishment ban = PunishmentManager.getInstance().getInterimBan(uuid).orElse(null);

                AdvancedBanCommandSender sender = input.getSender();

                sender.sendCustomMessage("Check.Header", true, "NAME", name);
                sender.sendCustomMessage("Check.UUID", false, "UUID", uuid);
                if (sender.hasPermission("ab.check.ip"))
                    sender.sendCustomMessage("Check.IP", false, "IP", ip);

                sender.sendCustomMessage("Check.Geo", false, "LOCATION", loc);
                sender.sendCustomMessage("Check.Mute", false, "DURATION", mute == null ? "§anone"
                        : mute.getType().isTemp() ? "§e" + PunishmentManager.getInstance().getDuration(mute, false) : "§cperma");
                if (mute != null)
                    sender.sendCustomMessage("Check.MuteReason", false, "REASON", mute.getReason());

                sender.sendCustomMessage("Check.Ban", false, "DURATION", ban == null ? "§anone"
                        : ban.getType().isTemp() ? "§e" + PunishmentManager.getInstance().getDuration(ban, false) : "§cperma");
                if (ban != null)
                    sender.sendCustomMessage("Check.BanReason", false, "REASON", ban.getReason());

                sender.sendCustomMessage("Check.Warn", false, "COUNT", PunishmentManager.getInstance().getCurrentWarns(uuid));
            },
            "Check.Usage",
            "check"),

    SYSTEM_PREFERENCES("ab.systemprefs",
            ".*",
            input -> {
                Calendar calendar = new GregorianCalendar();
                AdvancedBanCommandSender sender = input.getSender();
                sender.sendMessage("§c§lAdvancedBan v2 §cSystemPrefs");
                sender.sendMessage("§cServer-Time §8» §7" + calendar.get(Calendar.HOUR_OF_DAY) + ":" + calendar.get(Calendar.MINUTE));
                sender.sendMessage("§cYour UUID (Intern) §8» §7" + AdvancedBan.get().getInternalUUID(sender.getName()));
                if (input.hasNext()) {
                    String target = input.getPrimaryData();
                    sender.sendMessage("§c" + target + "'s UUID (Intern) §8» §7" + AdvancedBan.get().getInternalUUID(target));
                    sender.sendMessage("§c" + target + "'s UUID (Fetched) §8» §7" + UUIDManager.getInstance().getUuid(target));
                }
            },
            null,
            "systempreferences"),

    ADVANCED_BAN(null,
            ".*",
            input -> {
                AdvancedBanCommandSender sender = input.getSender();
                if (input.hasNext()) {
                    if (input.getPrimaryData().equals("reload")) {
                        if (sender.hasPermission("ab.reload")) {
                            try {
                                AdvancedBan.get().loadFiles();
                            } catch (IOException e) {
                                AdvancedBanLogger.getInstance().logException(e);
                                sender.sendMessage("§c§lAdvancedBan §8§l» §7Failed to reload files!");
                            }
                            sender.sendMessage("§a§lAdvancedBan §8§l» §7Reloaded!");
                        } else {
                            sender.sendCustomMessage("General.NoPerms", true);
                        }
                        return;
                    } else if (input.getPrimaryData().equals("help")) {
                        if (sender.hasPermission("ab.help")) {
                            sender.sendMessage("§8");
                            sender.sendMessage("§c§lAdvancedBan §7Command-Help");
                            sender.sendMessage("§8");
                            sender.sendMessage("§c/ban [Name] [Reason/@Layout]");
                            sender.sendMessage("§8» §7Ban a user permanently");
                            sender.sendMessage("§c/banip [Name/IP] [Reason/@Layout]");
                            sender.sendMessage("§8» §7Ban a user by IP");
                            sender.sendMessage("§c/tempban [Name] [Xmo/Xd/Xh/Xm/Xs/#TimeLayout] [Reason/@Layout]");
                            sender.sendMessage("§8» §7Ban a user temporary");
                            sender.sendMessage("§c/mute [Name] [Reason/@Layout]");
                            sender.sendMessage("§8» §7Mute a user permanently");
                            sender.sendMessage("§c/tempmute [Name] [Xmo/Xd/Xh/Xm/Xs/#TimeLayout] [Reason/@Layout]");
                            sender.sendMessage("§8» §7Mute a user temporary");
                            sender.sendMessage("§c/warn [Name] [Reason/@Layout]");
                            sender.sendMessage("§8» §7Warn a user permanently");
                            sender.sendMessage("§c/tempwarn [Name] [Xmo/Xd/Xh/Xm/Xs/#TimeLayout] [Reason/@Layout]");
                            sender.sendMessage("§8» §7Warn a user temporary");
                            sender.sendMessage("§c/kick [Name] [Reason/@Layout]");
                            sender.sendMessage("§8» §7Kick a user");
                            sender.sendMessage("§c/unban [Name/IP]");
                            sender.sendMessage("§8» §7Unban a user");
                            sender.sendMessage("§c/unmute [Name]");
                            sender.sendMessage("§8» §7Unmute a user");
                            sender.sendMessage("§c/unwarn [ID] or /unwarn clear [Name]");
                            sender.sendMessage("§8» §7Deletes a warn");
                            sender.sendMessage("§c/change-reason [ID or ban/mute USER] [New reason]");
                            sender.sendMessage("§8» §7Changes the reason of a punishment");
                            sender.sendMessage("§c/unpunish [ID]");
                            sender.sendMessage("§8» §7Deletes a punishment by ID");
                            sender.sendMessage("§c/banlist <Page>");
                            sender.sendMessage("§8» §7See all punishments");
                            sender.sendMessage("§c/history [Name/IP] <Page>");
                            sender.sendMessage("§8» §7See a users history");
                            sender.sendMessage("§c/warns [Name] <Page>");
                            sender.sendMessage("§8» §7See your or a users wa");
                            sender.sendMessage("§c/check [Name]");
                            sender.sendMessage("§8» §7Get all information about a user");
                            sender.sendMessage("§c/AdvancedBan <reload/help>");
                            sender.sendMessage("§8» §7Reloads the plugin or shows help page");
                            sender.sendMessage("§8");
                        } else {
                            sender.sendCustomMessage("General.NoPerms", true);
                        }
                        return;
                    }
                }


                sender.sendMessage("§8§l§m-=====§r §c§lAdvancedBan v2 §8§l§m=====-§r ");
                sender.sendMessage("  §cDev §8• §7Leoko");
                sender.sendMessage("  §cStatus §8• §a§oStable");
                sender.sendMessage("  §cVersion §8• §7" + AdvancedBan.get().getVersion());
                sender.sendMessage("  §cLicense §8• §7Public");
                sender.sendMessage("  §cStorage §8• §7" + (DatabaseManager.getInstance().isUseMySQL() ? "MySQL (external)" : "HSQLDB (local)"));
                sender.sendMessage("  §cUUID-Mode §8• §7" + UUIDManager.getInstance().getMode());

                sender.sendMessage("  §cPrefix §8• §7" + MessageManager.getPrefix().orElse("§edisabled"));
                sender.sendMessage("§8§l§m-=========================-§r ");
            },
            null,
            "advancedban");

    @Getter
    private final String permission;
    private final Predicate<String[]> syntaxValidator;
    private final Consumer<CommandInput> commandHandler;
    @Getter
    private final String usagePath;
    @Getter
    private final String[] names;

    Command(String permission, Predicate<String[]> syntaxValidator,
            Consumer<CommandInput> commandHandler, String usagePath, String... names) {
        this.permission = permission;
        this.syntaxValidator = syntaxValidator;
        this.commandHandler = commandHandler;
        this.usagePath = usagePath;
        this.names = names;
    }

    Command(String permission, String regex, Consumer<CommandInput> commandHandler,
            String usagePath, String... names) {
        this(permission, (args) -> String.join(" ", args).matches(regex), commandHandler, usagePath, names);
    }

    public boolean validateArguments(String[] args) {
        return syntaxValidator.test(args);
    }

    public void execute(AdvancedBanCommandSender player, String[] args) {
        commandHandler.accept(new CommandInput(player, args));
    }

    public static Command getByName(String name) {
        String lowerCase = name.toLowerCase();
        for (Command command : values()) {
            for (String s : command.names) {
                if (s.equals(lowerCase))
                    return command;
            }
        }
        return null;
    }

    @Getter
    @AllArgsConstructor
    public class CommandInput {
        private AdvancedBanCommandSender sender;
        private String[] args;

        public String getPrimary() {
            return args.length == 0 ? null : args[0];
        }

        String getPrimaryData() {
            return getPrimary().toLowerCase();
        }

        public String removeArgument(int index) {
            String[] temp = new String[args.length - 1];
            byte diff = 0;
            for (int i = 0; i < args.length; i++) {
                if (i == index) {
                    diff = -1;
                    continue;
                }

                temp[i + diff] = args[i];
            }
            String removed = args[index];
            args = temp;
            return removed;
        }

        public String next() {
            return removeArgument(0);
        }

        public boolean hasNext() {
            return args.length > 0;
        }
    }
}
