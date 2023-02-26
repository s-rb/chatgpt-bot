package ru.list.surkovr.chatgptbot;

import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;

import static java.lang.String.format;

public class ChatGPTBot extends TelegramLongPollingBot {
    private static final Logger log = Logger.getLogger(ChatGPTBot.class.getSimpleName());

    private final String telegramBotName;
    private final String openaiModelId;
    private final Long maxTokens;
    private final Long adminChatId;

    private final UserService userService;
    private final OpenAiService openaiService;

    public ChatGPTBot(String openaiApiKey, String telegramBotToken, String telegramBotName, String openaiModelId,
                      Long maxTokens, String usersFile, Long adminChatId, Integer openApiTimeoutS) {
        super(telegramBotToken);
        this.userService = new UserService(usersFile);
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
        String usersFile = props.getProperty("usersFile");
        Long maxTokens = (long) Integer.parseInt(props.getProperty("maxTokens"));
        Integer openApiTimeoutS = Integer.parseInt(props.getProperty("openApiTimeoutS"));
        Long adminChatId = (long) Integer.parseInt(props.getProperty("adminChatId"));

        ChatGPTBot bot = new ChatGPTBot(openaiApiKey, telegramBotToken, telegramBotName, openaiModelId, maxTokens,
                usersFile, adminChatId, openApiTimeoutS);
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
            if ((!update.hasMessage() || !update.getMessage().hasText()) && (!update.hasCallbackQuery())) {
                log.warning(format("Update has no message: update: [%s], message: [%s]", update, update.getMessage()));
                return;
            }

            if (update.hasCallbackQuery()) {
                onCallbackQueryReceived(update.getCallbackQuery());
                return;
            }

            String messageText = update.getMessage().getText();
            if (isExcludedCommand(messageText)) {
                log.warning(format("Update has unprocessable message: [%s]", messageText));
                return;
            }

            User from = update.getMessage().getFrom();
            Long userId = from.getId();
            Long chatId = update.getMessage().getChatId();
            String chatIdString = chatId.toString();
            log.info(format("Received message from user with ID %s in chat %s : %s", userId, chatIdString, messageText));

            if (isAdmin(chatId) || isApproved(from)) {
                CompletionRequest request = CompletionRequest.builder()
                        .prompt(messageText)
                        .model(openaiModelId)
                        .maxTokens(maxTokens.intValue())
                        .build();

                var response = openaiService.createCompletion(request);

                String generatedText = response.getChoices().get(0).getText();
                log.info(format("Sending response to user with ID %s in chat %s : %s", userId, chatIdString, generatedText));

                SendMessage sendMessage = new SendMessage(chatId.toString(), generatedText);
                execute(sendMessage);

            } else {
                sendMessageToUser(userId, "Вам это действие недоступно", null);
                askForAccessApproval(from);
            }
        } catch (Exception e) {
            log.warning(e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isExcludedCommand(String messageText) {
        return messageText.startsWith("/");
    }

    private boolean isApproved(User from) {
        return userService.isApprovedUser(from.getId());
    }

    private boolean isAdmin(Long adminChatId) {
        return this.adminChatId == null || this.adminChatId.equals(adminChatId);
    }

    private void askForAccessApproval(User user) {
        String messageText = "Вы хотите отправить администратору запрос на доступ?";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(
                List.of(List.of(
                        getInlineButton("Да", "accessApproval:" + user.getId()),
                        getInlineButton("Нет", "accessDecline:" + user.getId())
                )));
        sendMessageToUser(user.getId(), messageText, markup);
    }

    private InlineKeyboardButton getInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        return button;
    }

    public void onCallbackQueryReceived(CallbackQuery callbackQuery) {
        String[] data = callbackQuery.getData().split(":");
        long userId = Long.parseLong(data[1]);
        User tgUser = callbackQuery.getFrom();
        if (data[0].equals("accessApproval")) {
            ru.list.surkovr.chatgptbot.User user = userService.get(userId);
            if (user != null) {
                user = userService.updateStatus(userId, UserStatus.PENDING);
            } else {
                user = userService.createUser(tgUser);
            }

            sendMessageToAdmin("Вы хотите дать доступ пользователю " + "[username=" + user.getUsername()
                            + " userId=" + user.getUserId()
                            + " firstName=" + user.getFirstName() + " lastName=" + user.getLastName() + "]?",
                    new InlineKeyboardMarkup(List.of(List.of(
                            getInlineButton("Подтвердить", "accessApproveConfirmation:" + user.getUserId()),
                            getInlineButton("Отклонить", "accessDeclineConfirmation:" + user.getUserId())))));
        } else if (data[0].equals("accessDecline")) {
            ru.list.surkovr.chatgptbot.User user = userService.get(userId);
            if (user != null) {
                user = userService.updateStatus(userId, UserStatus.PENDING);
            } else {
                user = userService.createUser(tgUser);
            }
            sendMessageToUser(callbackQuery.getMessage().getChatId(), "Нет доступа", null);
        } else if (data[0].equals("accessApproveConfirmation")) {
            long approvedUserId = Long.parseLong(data[1]);
            ru.list.surkovr.chatgptbot.User user = userService.updateStatus(userId, UserStatus.APPROVED);
            sendMessageToUser(approvedUserId, "Вам предоставлен доступ", null);
            sendMessageToAdmin("Вы одобрили запрос на доступ для " + user.getFirstName() + " " +
                    user.getLastName() + " (" + user.getUsername() + ")", null);
        } else if (data[0].equals("accessDeclineConfirmation")) {
            long declinedUserId = Long.parseLong(data[1]);
            ru.list.surkovr.chatgptbot.User user = userService.updateStatus(userId, UserStatus.DECLINED);
            sendMessageToUser(declinedUserId, "Вам отказано в доступе", null);
            sendMessageToAdmin("Вы отказали в доступе для " + user.getFirstName() + " " +
                    user.getLastName() + " (" + user.getUsername() + ")", null);
        } else {
            String errMsg = format("Получена неизвестная, необрабатываемая команда: %s, chatId: %s, userId: %s",
                    Arrays.toString(data), callbackQuery.getMessage().getChatId(), tgUser.getId());
            log.warning(errMsg);
            sendMessageToAdmin(errMsg, null);
        }
    }

    // Метод для отправки сообщения пользователю
    private void sendMessageToUser(long chatId, String message, InlineKeyboardMarkup inlineKeyboardMarkup) {
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), message);
        if (inlineKeyboardMarkup != null) sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Метод для отправки сообщения админу
    private void sendMessageToAdmin(String message, InlineKeyboardMarkup inlineKeyboardMarkup) {
        SendMessage sendMessage = new SendMessage(String.valueOf(adminChatId), message);
        if (inlineKeyboardMarkup != null) sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}

