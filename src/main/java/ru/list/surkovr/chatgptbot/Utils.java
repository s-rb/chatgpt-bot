package ru.list.surkovr.chatgptbot;

import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {

    public static final Set<String> commands = Arrays.stream(Commands.values()).map(Commands::getValue).collect(Collectors.toSet());

    private Utils() {
    }

    public static InlineKeyboardButton getInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        return button;
    }

    public static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    public static String getTgUserName(User user) {
        final String firstName = user.getFirstName();
        final String lastName = user.getLastName();
        final String userName = user.getUserName();
        return !hasText(firstName) && !hasText(lastName) ? userName
                : (Stream.of(firstName, lastName).filter(Utils::hasText).collect(Collectors.joining(" ")));
    }

    public static boolean isCommand(String command) {
        return commands.contains(command.toLowerCase());
    }
}
