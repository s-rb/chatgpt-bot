package ru.list.surkovr.chatgpt_bot;

import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import static java.lang.String.format;

public class ChatGPTBot extends TelegramLongPollingBot {
    private static final Logger log = Logger.getLogger(ChatGPTBot.class.getSimpleName());

    private final String telegramBotName;
    private final String openaiModelId;
    private final Long maxTokens;
    private final Long adminChatId;

    private final OpenAiService openaiService;

    public ChatGPTBot(String openaiApiKey, String telegramBotToken, String telegramBotName, String openaiModelId,
                      Long maxTokens, Long adminChatId, Integer openApiTimeoutS) {
        super(telegramBotToken);
        this.openaiService = new OpenAiService(openaiApiKey, Duration.ofSeconds(openApiTimeoutS));
        this.telegramBotName = telegramBotName;
        this.openaiModelId = openaiModelId;
        this.maxTokens = maxTokens;
        this.adminChatId = adminChatId;
    }

    public static void main(String[] args) throws TelegramApiException {
        Properties props = new Properties();
        try (InputStream is = ChatGPTBot.class.getClassLoader().getResourceAsStream("application.properties")) {
            props.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String openaiApiKey = props.getProperty("openaiApiKey");
        String telegramBotToken = props.getProperty("telegramBotToken");
        String telegramBotName = props.getProperty("telegramBotName");
        String openaiModelId = props.getProperty("openaiModelId");
        Long maxTokens = Long.parseLong(props.getProperty("maxTokens"));
        Integer openApiTimeoutS = Integer.parseInt(props.getProperty("openApiTimeoutS"));
        Long adminChatId = Long.parseLong(props.getProperty("adminChatId"));

        ChatGPTBot bot = new ChatGPTBot(openaiApiKey, telegramBotToken, telegramBotName, openaiModelId, maxTokens,
                adminChatId, openApiTimeoutS);
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            telegramBotsApi.registerBot(bot);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return telegramBotName;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (!update.hasMessage() || !update.getMessage().hasText()) {
                log.warning(format("Update has no message: update: [%s], message: [%s]", update, update.getMessage()));
                return;
            }

            String messageText = update.getMessage().getText();
            Long userId = update.getMessage().getFrom().getId();
            Long chatId = update.getMessage().getChatId();
            String chatIdString = chatId.toString();

            // Логгирование запросов пользователей
            log.info(format("Received message from user with ID %s in chat %s : %s", userId, chatIdString, messageText));

            if (adminChatId == null || adminChatId.equals(chatId)) {
                CompletionRequest request = CompletionRequest.builder()
                        .prompt(messageText)
                        .model(openaiModelId)
                        .maxTokens(maxTokens.intValue())
                        .build();

                var response = openaiService.createCompletion(request);

                String generatedText = response.getChoices().get(0).getText();
                // Логгирование ответов пользователей
                log.info(format("Sending response to user with ID %s in chat %s : %s", userId, chatIdString, generatedText));

                SendMessage sendMessage = new SendMessage(chatId.toString(), generatedText);
                execute(sendMessage);

            }
        } catch (Exception e) {
            log.warning(e.getMessage());
            e.printStackTrace();
        }
    }
}

