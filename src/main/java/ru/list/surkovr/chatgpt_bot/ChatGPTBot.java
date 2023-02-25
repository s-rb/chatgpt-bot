package ru.list.surkovr.chatgpt_bot;

import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.time.Duration;
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
        ResourceBundle rb = ResourceBundle.getBundle("application");
        String openaiApiKey = rb.getString("openaiApiKey");
        String telegramBotToken = rb.getString("telegramBotToken");
        String telegramBotName = rb.getString("telegramBotName");
        String openaiModelId = rb.getString("openaiModelId");
        Long maxTokens = Long.parseLong(rb.getString("maxTokens"));
        Integer openApiTimeoutS = Integer.parseInt(rb.getString("openApiTimeoutS"));
        Long adminChatId = Long.parseLong(rb.getString("adminChatId"));

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
