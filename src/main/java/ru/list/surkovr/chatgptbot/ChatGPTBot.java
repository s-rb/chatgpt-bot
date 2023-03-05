package ru.list.surkovr.chatgptbot;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
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

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static java.lang.String.format;
import static ru.list.surkovr.chatgptbot.Utils.hasText;
import static ru.list.surkovr.chatgptbot.Utils.isCommand;

public class ChatGPTBot extends TelegramLongPollingBot {
    private final Logger log = Logger.getLogger(ChatGPTBot.class.getSimpleName());

    private final String telegramBotName;
    private final Long adminChatId;

    private final UserService userService;
    private final OpenAiService openaiService;

    public ChatGPTBot(String openaiApiKey, String telegramBotToken, String telegramBotName, String usersFile,
                      Long adminChatId, Integer openApiTimeoutS) {
        super(telegramBotToken);
        this.userService = new UserService(usersFile);
        this.openaiService = new OpenAiService(openaiApiKey, Duration.ofSeconds(openApiTimeoutS));
        this.telegramBotName = telegramBotName;
        this.adminChatId = adminChatId;
    }

    @Override
    public String getBotUsername() {
        return telegramBotName;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (!hasTextQuery(update) && (!update.hasCallbackQuery())) {
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

    private boolean hasTextQuery(Update update) {
        return update.hasMessage() && update.getMessage().hasText();
    }

    private void onTextMessageReceived(Update update) {
        final Message message = update.getMessage();
        final Integer messageId = message.getMessageId();
        String messageText = message.getText();
        User from = message.getFrom();
        Long userId = from.getId();
        Long chatId = message.getChatId();
        String chatIdString = chatId.toString();
        log.info(format("Received message from user with ID %s in chat %s : %s", userId, chatIdString, messageText));

        if (isExcludedCommand(messageText)) {
            log.info(format("Update has unprocessable message: [%s]", messageText));
            sendAnswerToUser(chatId, messageId, "You are trying to execute command, because you message starts with '/'. I don't know such command");
        } else if (isCommand(messageText)) {
            onCommandReceived(update);
        } else if (isMessageTooLong(messageText)) {
            log.info(format("Received too long input message [%s] length", messageText.length()));
            sendMessageToUser(chatId, "You have entered too long message, please make it shorter", null);
        } else if (isAdmin(chatId) || isApproved(from)) {
            final var user = userService.updateData(from, chatId);

            List<ChatMessage> messages = prepareMessages(user.getMessages(), messageText);
            ChatCompletionRequest request = getChatCompletionRequest(user, messages);
            var response = getChatCompletionSafely(request);
            if (response == null) {
                sendAnswerToUser(chatId, messageId, "Sorry, I don't have answer now. Try again later");
                return;
            }
            user.setMessages(messages);

            response.getChoices().forEach(choice -> {
                ChatMessage chatMessage = choice.getMessage();
                final String generatedText = chatMessage.getContent();
                final String role = chatMessage.getRole();
                log.info(format("Sending response to user with ID [%s] in chat [%s] role[%s]: message[%s]", userId, chatIdString, role, generatedText));
                user.setMessages(prepareMessages(user.getMessages(), chatMessage));
                sendAnswerToUser(chatId, messageId, hasText(generatedText) ? generatedText : "Sorry, I don't have answer");
            });
        } else {
            sendMessageToUser(userId, "Access denied", null);
            askForAccessApproval(from);
        }
    }

    private boolean isMessageTooLong(String messageText) {
        return Constants.MAX_TOKENS - messageText.length() < Constants.COMPLETION_TOKENS;
    }

    private ChatCompletionRequest getChatCompletionRequest(ru.list.surkovr.chatgptbot.User user, List<ChatMessage> messages) {
        var builder = ChatCompletionRequest.builder()
                .messages(messages)
                .model(Constants.OPENAI_MODEL_ID)
                .maxTokens(Constants.MAX_TOKENS.intValue() -
                        messages.stream().map(o -> o.getContent().length()).reduce(Integer::sum).orElse(0));
        if (hasText(user.getOpenAiUser())) builder = builder.user(user.getOpenAiUser());
        var request = builder.build();
        return request;
    }

    private ChatCompletionResult getChatCompletionSafely(ChatCompletionRequest request) {
        int attemptsRemain = Constants.ATTEMPTS_TO_CALL_OPEN_AI_API;
        ChatCompletionResult res = null;
        while (attemptsRemain-- > 0 && res == null) {
            try {
                res = openaiService.createChatCompletion(request);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return res;
    }

    private void onCommandReceived(Update update) {
        try {
            final Message message = update.getMessage();
            String messageText = message.getText();
            Commands command = Commands.getCommand(messageText.toLowerCase());
            if (command == null) return;
            switch (command) {
                case START -> sendMessageToUser(update.getMessage().getChatId(),
                        format("Welcome %s!\nThis bot lets you ask questions to ChatGpt from OpenAI.\nSend any message to start",
                                Utils.getTgUserName(update.getMessage().getFrom())),
                        null);
                default -> log.warning(format("Command [%s] not recognized", command));
            }
        } catch (Exception e) {
            log.throwing(getClass().getSimpleName(), "onCommandReceived", e);
        }
    }

    private boolean isExcludedCommand(String command) {
        return command.startsWith("/") && !isCommand(command);
    }

    private boolean isApproved(User user) {
        return userService.isApprovedUser(user.getId());
    }

    private boolean isAdmin(Long chatId) {
        return this.adminChatId == null || this.adminChatId.equals(chatId);
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

    private void sendAnswerToUser(Long chatId, Integer srcMessageId, String text) {
        SendMessage sendMsg = SendMessage.builder().text(text).chatId(chatId).replyToMessageId(srcMessageId).build();
        executeMsgAction(sendMsg);
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

    private List<ChatMessage> prepareMessages(List<ChatMessage> messages, String messageText) {
        return prepareMessages(messages, new ChatMessage(ChatMessageRole.USER.value(), messageText));
    }

    /*private List<ChatMessage> prepareMessages(List<ChatMessage> messages, ChatMessage chatMessage) {
        if (messages == null) messages = new ArrayList<>();
        if (!messages.isEmpty() && messages.size() >= Constants.MAX_MESSAGES_TO_STORE_FOR_USER) {
            messages = messages.subList(messages.size() - Constants.MAX_MESSAGES_TO_STORE_FOR_USER + 1, messages.size());
        }
        messages.add(chatMessage);

        if (!messages.isEmpty()) {
            int i = messages.size() - 1;
            int sum = 0;
            while (i >= 0) {
                sum += messages.get(i).getContent().length();
                if (sum > (Constants.MAX_TOKENS - Constants.COMPLETION_TOKENS) || i == 0) break;
                i--;
            }
            messages = messages.subList(i < messages.size() ? i + 1 : i, messages.size());
        }
        return messages;
    }*/

    public List<ChatMessage> prepareMessages(List<ChatMessage> messages, ChatMessage chatMessage) {
        int MAX_MESSAGES = Constants.MAX_MESSAGES_TO_STORE_FOR_USER;
        long MAX_CONTENT = Constants.MAX_TOKENS - Constants.COMPLETION_TOKENS;
        List<ChatMessage> newMessages = new ArrayList<>(messages);

        // Добавляем новое сообщение в конец списка
        newMessages.add(chatMessage);

        // Проверяем, не превышает ли общее количество сообщений максимально допустимое значение
        while (newMessages.size() > MAX_MESSAGES) {
            newMessages.remove(0);
        }

        int totalContentLength = newMessages.stream().mapToInt(m -> m.getContent().length()).sum();

        // Проверяем, не превышает ли общая длина контента максимально допустимое значение
        while (totalContentLength > MAX_CONTENT) {
            ChatMessage removedMessage = newMessages.remove(0);
            totalContentLength -= removedMessage.getContent().length();
        }

        return newMessages;
    }


}

