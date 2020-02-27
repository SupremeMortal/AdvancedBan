package me.leoko.advancedban.manager;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;
import me.leoko.advancedban.AdvancedBan;
import me.leoko.advancedban.AdvancedBanCommandSender;
import me.leoko.advancedban.AdvancedBanLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@UtilityClass
public class MessageManager {
    private static String replace(String str, Object... parameters) {
        for (int i = 0; i < parameters.length; i += 2) {
            if(parameters[i + 1] == null)
                parameters[i + 1] = "";
            str = str.replaceAll("%" + parameters[i].toString() + '%', parameters[i + 1].toString());
        }
        return str;
    }

    public String getMessage(String path, Object... parameters) {
        JsonNode message = AdvancedBan.get().getMessages().getMessage(path);
        String str;
        if (message.isTextual()) {
            str = replace(message.textValue(), parameters).replace('&', '§');
        } else {
            str = "Failed! See console for details!";
            AdvancedBanLogger.getInstance().warn("Unregistered message used. Please check Message.yml for "+path);
        }
        return str;
    }

    public List<String> getMessageList(String path, Object... parameters) {
        JsonNode messages = AdvancedBan.get().getMessages().getMessage(path);
        if (messages.isArray()) {
            List<String> messageList = new ArrayList<>();
            messages.forEach(element -> messageList.add(replace(element.textValue(), parameters).replace('&', '§')));
            return messageList;
        }
        AdvancedBanLogger.getInstance().warn("Unregistered message used. Please check Message.yml for "+path);
        return Collections.emptyList();
    }

    public List<String> getLayout(String path, Object... parameters) {
        JsonNode layout = AdvancedBan.get().getLayouts().getLayout(path);
        if (layout.isArray()) {
            List<String> messages = new ArrayList<>();
            layout.forEach(element -> messages.add(replace(element.textValue(), parameters).replace('&', '§')));
            return messages;
        }
        AdvancedBanLogger.getInstance().warn("Unregistered layout used. Please check Layouts.yml for "+path);
        return Collections.emptyList();
    }

    public void sendMessage(AdvancedBanCommandSender sender, String path, boolean prefix, Object[] parameters) {
        StringBuilder builder = new StringBuilder();
        if (prefix && !AdvancedBan.get().getConfiguration().isPrefixDisabled()) {
            builder.append(getMessage("General.Prefix"));
            builder.append(' ');
        }
        builder.append(getMessage(path, parameters));
        sender.sendMessage(builder.toString());
    }

    public Optional<String> getPrefix() {
        if (AdvancedBan.get().getConfiguration().isPrefixDisabled())
            return Optional.empty();

        return Optional.of(getMessage("General.Prefix"));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public String getReasonOrDefault(Optional<String> reason) {
        return reason.orElse(AdvancedBan.get().getConfiguration().getDefaultReason());
    }
}
