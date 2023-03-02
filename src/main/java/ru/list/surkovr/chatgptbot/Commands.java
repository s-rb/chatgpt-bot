package ru.list.surkovr.chatgptbot;

import java.util.Arrays;

public enum Commands {

    START("/start");

    private final String value;

    Commands(String value) {
        this.value = value;
    }

    public static Commands getCommand(String val) {
        return Arrays.stream(values()).filter(o -> o.value.equalsIgnoreCase(val)).findFirst().orElse(null);
    }

    public String getValue() {
        return value;
    }
}
