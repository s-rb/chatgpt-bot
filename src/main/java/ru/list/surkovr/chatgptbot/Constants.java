package ru.list.surkovr.chatgptbot;

public class Constants {
    public static final int ATTEMPTS_TO_CALL_OPEN_AI_API = 5;

    private Constants() {
    }

    public static final int COMPLETION_TOKENS = 500;
    public static final int MAX_MESSAGES_TO_STORE_FOR_USER = 20;
    public static final String OPENAI_MODEL_ID = "gpt-3.5-turbo";
    public static final Long MAX_TOKENS = 4000L;
}
