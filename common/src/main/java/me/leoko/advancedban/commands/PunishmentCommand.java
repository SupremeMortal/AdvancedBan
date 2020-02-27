package me.leoko.advancedban.commands;

import com.fasterxml.jackson.databind.JsonNode;
import me.leoko.advancedban.AdvancedBan;
import me.leoko.advancedban.AdvancedBanCommandSender;
import me.leoko.advancedban.AdvancedBanPlayer;
import me.leoko.advancedban.manager.TimeManager;
import me.leoko.advancedban.punishment.Punishment;
import me.leoko.advancedban.punishment.PunishmentManager;
import me.leoko.advancedban.punishment.PunishmentType;

import java.util.function.Consumer;

public class PunishmentCommand implements Consumer<Command.CommandInput> {
    private PunishmentType type;

    public PunishmentCommand(PunishmentType type) {
        this.type = type;
    }

    @Override
    public void accept(Command.CommandInput input) {
        boolean silent = processTag(input, "-s");
        String name = input.getPrimary();

        // is exempted
        if (processExempt(input, type))
            return;

        // extract target
        Object target = type.isIpOrientated()
                ? CommandUtils.processIP(input)
                : CommandUtils.processName(input);
        if (target == null)
            return;



        Long end = -1L;
        String calculation = null;
        // calculate duration if necessary
        if (type.isTemp()) {
            String timeTag = input.next();
            if(timeTag.matches("#.+")){
                calculation = timeTag.substring(1);
                end = processTimeLayout(calculation, input.getSender(), target);
            }else {
                end = processTime(timeTag, input.getSender(), type);
            }

            if (end == null)
                return;
        }


        // build reason
        String reason = CommandUtils.processReason(input);
        if (reason == null)
            return;
        else if (reason.isEmpty())
            reason = null;

        // check if punishment of this type is already active
        if (alreadyPunished(target, type)) {
            input.getSender().sendCustomMessage(type.getBasic().getConfSection() + ".AlreadyDone",
                    true, "NAME", name);
            return;
        }

        String operator = input.getSender().getName();
        final Punishment punishment = new Punishment(target, name, operator, calculation, TimeManager.getTime(), end, type);
        punishment.setReason(reason);

        PunishmentManager.getInstance().addPunishment(punishment, silent);

        input.getSender().sendCustomMessage(type.getBasic().getConfSection() + ".Done",
                true, "NAME", name);
    }

    // Removes time argument and returns timestamp (null if failed)
    private static Long processTime(String time, AdvancedBanCommandSender sender, PunishmentType type) {

        long toAdd = TimeManager.toMilliSec(time);
        if (!sender.hasPermission("ab." + type.getName() + ".dur.max")) {
            long max = -1;
            for (int i = 10; i >= 1; i--) {
                if (sender.hasPermission("ab." + type.getName() + ".dur." + i)) {
                    final Long found = AdvancedBan.get().getConfiguration().getTempPerms().get(i);
                    if (found != null) {
                        max = found;
                        break;
                    }
                }
            }
            if (max != -1 && toAdd > max) {
                sender.sendCustomMessage(type.getConfSection() + ".MaxDuration",
                        true, "MAX", max / 1000);
                return null;
            }
        }
        return TimeManager.getTime() + toAdd;
    }

    private static Long processTimeLayout(String layout, AdvancedBanCommandSender sender, Object target){
        final JsonNode timeLayout = AdvancedBan.get().getLayouts().getLayout("Time." + layout);
        if (timeLayout.isMissingNode() || !timeLayout.isArray()) {
            sender.sendCustomMessage("General.LayoutNotFound",
                    true, "NAME", layout);
            return null;
        }

        int i = PunishmentManager.getInstance().getCalculationLevel(target, layout);


        String timeName = timeLayout.get(Math.min(i, timeLayout.size() - 1)).asText();
        if (timeName.equalsIgnoreCase("perma"))
            return -1L;
        else
            return TimeManager.getTime() + TimeManager.toMilliSec(timeName);
    }

    // Checks whether target is exempted from punishment
    private static boolean processExempt(Command.CommandInput input, PunishmentType type) {
        String name = input.getPrimary();
        String dataName = name.toLowerCase();

        if (!canPunish(input.getSender(), dataName, type.getName())) {
            input.getSender().sendCustomMessage(type.getBasic().getConfSection() + ".Exempt",
                    true, "NAME", name);
            return true;
        }
        return false;
    }

    // Check based on exempt level if some is able to ban a player
    public static boolean canPunish(AdvancedBanCommandSender operator, String target, String path) {
        final boolean offlineExempt = AdvancedBan.get().getConfiguration()
                .getExemptPlayers().stream().anyMatch(target::equalsIgnoreCase);

        if (offlineExempt)
            return false;

        if (!AdvancedBan.get().isOnline(target))
            return true;

        final AdvancedBanPlayer player = AdvancedBan.get().getPlayer(target).get();

        final String perms = "ab." + path + ".exempt";
        if (player.hasPermission(perms))
            return false;

        int targetLevel = permissionLevel(player, perms);
        return targetLevel == 0 || permissionLevel(operator, perms) > targetLevel;
    }

    private static int permissionLevel(AdvancedBanCommandSender subject, String permission) {
        for (int i = 10; i >= 1; i--)
            if (subject.hasPermission(permission + "." + i))
                return i;

        return 0;
    }

    // Checks whether input contains tag and removes it
    private static boolean processTag(Command.CommandInput input, String tag) {
        // Check the first few arguments for the tag
        String[] args = input.getArgs();
        for (int i = 0; i < args.length && i < 4; i++) {
            if (tag.equalsIgnoreCase(args[i])) {
                input.removeArgument(i);
                return true;
            }
        }
        return false;
    }

    private static boolean alreadyPunished(Object target, PunishmentType type) {
        return (type.getBasic() == PunishmentType.MUTE && PunishmentManager.getInstance().isMuted(target))
                || (type.getBasic() == PunishmentType.BAN && PunishmentManager.getInstance().isBanned(target));
    }
}
