package ru.list.surkovr.chatgptbot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

public class Utils {

    private Utils() {}

    public static InlineKeyboardButton getInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        return button;
    }

    public static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
