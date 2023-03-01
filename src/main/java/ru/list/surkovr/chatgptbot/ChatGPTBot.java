package ru.list.surkovr.chatgptbot;

import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static java.lang.String.format;
import static ru.list.surkovr.chatgptbot.Utils.hasText;

public class ChatGPTBot extends TelegramLongPollingBot {
    private static final Logger log = Logger.getLogger(ChatGPTBot.class.getSimpleName());
    public static final int DELTA_TOKENS = 200;

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
            } else {
                onTextMessageReceived(update);
            }
        } catch (Exception e) {
            log.warning(e.getMessage());
            e.printStackTrace();
        }
    }

    private void onTextMessageReceived(Update update) {
        final Message message = update.getMessage();
        final Integer messageId = message.getMessageId();
        String messageText = message.getText();
        if (isExcludedCommand(messageText)) {
            log.info(format("Update has unprocessable message: [%s]", messageText));
            return;
        }

        User from = message.getFrom();
        Long userId = from.getId();
        Long chatId = message.getChatId();
        String chatIdString = chatId.toString();
        log.info(format("Received message from user with ID %s in chat %s : %s", userId, chatIdString, messageText));

        if (maxTokens - messageText.length() < DELTA_TOKENS) {
            log.info(format("Received too long input message [%s] length", messageText.length()));
            sendMessageToUser(chatId, "You have entered too long message, please make it shorter", null);
            return;
        }

        if (isAdmin(chatId) || isApproved(from)) {
            final ru.list.surkovr.chatgptbot.User user = userService.updateData(from, chatId);

            CompletionRequest.CompletionRequestBuilder builder = CompletionRequest.builder()
                    .prompt(messageText).model(openaiModelId)
                    .maxTokens(maxTokens.intValue() - messageText.length());
            if (hasText(user.getOpenAiUser())) builder = builder.user(user.getOpenAiUser());
            CompletionRequest request = builder.build();

            var response = openaiService.createCompletion(request);

            response.getChoices().forEach(choice -> {
                String generatedText = choice.getText();
                log.info(format("Sending response to user with ID %s in chat %s : %s", userId, chatIdString, generatedText));
                sendAnswerToUser(chatId, messageId, hasText(generatedText) ? generatedText : "Sorry, I don't have answer");
            });
        } else {
            sendMessageToUser(userId, "Access denied", null);
            askForAccessApproval(from);
        }
    }

    private void sendAnswerToUser(Long chatId, Integer srcMessageId, String text) {
        SendMessage sendMsg = SendMessage.builder().text(text).chatId(chatId).replyToMessageId(srcMessageId).build();
        executeMsgAction(sendMsg);
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
        String messageText = "Do you want to request the access?";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(
                List.of(List.of(
                        Utils.getInlineButton("Yes", "accessApproval:" + user.getId()),
                        Utils.getInlineButton("No", "accessDecline:" + user.getId())
                )));
        sendMessageToUser(user.getId(), messageText, markup);
    }

    private void askAdminForAccessConfirmation(ru.list.surkovr.chatgptbot.User user) {
        long userId = user.getUserId();
        String message = "Do you want to give access for user " + "[username=" + user.getUsername()
                + " userId=" + userId + " firstName=" + user.getFirstName() + " lastName=" + user.getLastName() + "]?";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(List.of(
                Utils.getInlineButton("Approve", "accessApproveConfirmation:" + userId),
                Utils.getInlineButton("Decline", "accessDeclineConfirmation:" + userId))));
        sendMessageToAdmin(message, markup);
    }

    public void onCallbackQueryReceived(CallbackQuery callbackQuery) {
        String[] data = callbackQuery.getData().split(":");
        User tgUser = callbackQuery.getFrom();
        final Long chatId = callbackQuery.getMessage().getChatId();
        switch (data[0]) {
            case "accessApproval" -> {
                ru.list.surkovr.chatgptbot.User user = userService.updateStatus(tgUser, chatId, UserStatus.PENDING);
                editMessage("Access request sent", chatId, callbackQuery.getMessage().getMessageId());
                askAdminForAccessConfirmation(user);
            }
            case "accessDecline" -> {
                ru.list.surkovr.chatgptbot.User user = userService.updateStatus(tgUser, chatId, UserStatus.PENDING);
                deleteMessage(chatId, callbackQuery.getMessage().getMessageId());
                sendMessageToUser(user.getChatId(), "Access denied", null);
            }
            case "accessApproveConfirmation" -> {
                long approvedUserId = Long.parseLong(data[1]);
                ru.list.surkovr.chatgptbot.User user = userService.updateStatus(approvedUserId, UserStatus.APPROVED);
                sendMessageToUser(user.getChatId(), "Access granted. Welcome! Now you can ask your questions", null);
                editMessage("You have approved access for user " + user.getFirstName() + " " +
                        user.getLastName() + " (" + user.getUsername() + ")", adminChatId, callbackQuery.getMessage().getMessageId());
            }
            case "accessDeclineConfirmation" -> {
                long declinedUserId = Long.parseLong(data[1]);
                ru.list.surkovr.chatgptbot.User user = userService.updateStatus(declinedUserId, UserStatus.DECLINED);
                sendMessageToUser(user.getChatId(), "Your request for the access was declined", null);
                editMessage("You have declined access request from user " + user.getFirstName() + " " +
                        user.getLastName() + " (" + user.getUsername() + ")", adminChatId, callbackQuery.getMessage().getMessageId());
            }
            default -> {
                String errMsg = format("Got unknown, unprocessable command: %s, chatId: %s, userId: %s",
                        Arrays.toString(data), chatId, tgUser.getId());
                sendErrorToAdmin(errMsg, null);
            }
        }
    }

    private void editMessage(String newText, Long chatId, Integer messageId) {
        final EditMessageText msg = new EditMessageText();
        msg.setChatId(chatId);
        msg.setMessageId(messageId);
        msg.setText(newText);
        msg.setReplyMarkup(null);
        executeMsgAction(msg);
    }

    private void deleteMessage(Long chatId, Integer messageId) {
        executeMsgAction(new DeleteMessage(String.valueOf(chatId), messageId));
    }

    private void sendMessageToUser(long chatId, String message, InlineKeyboardMarkup inlineKeyboardMarkup) {
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), message);
        if (inlineKeyboardMarkup != null) sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        executeMsgAction(sendMessage);
    }

    private <T extends Serializable, Method extends BotApiMethod<T>> void executeMsgAction(Method method) {
        try {
            execute(method);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageToAdmin(String message, InlineKeyboardMarkup inlineKeyboardMarkup) {
        sendMessageToUser(adminChatId, message, inlineKeyboardMarkup);
    }

    private void sendErrorToAdmin(String message, InlineKeyboardMarkup inlineKeyboardMarkup) {
        log.warning(message);
        sendMessageToUser(adminChatId, "#ERROR\n\n" + message, inlineKeyboardMarkup);
    }
}

