package ru.list.surkovr.chatgptbot;

import javax.ws.rs.NotFoundException;
import java.io.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static ru.list.surkovr.chatgptbot.Utils.hasText;

public class UserService {
    private static final Logger log = Logger.getLogger(UserService.class.getName());

    private final String filePath;
    private final ConcurrentHashMap<Long, User> users;

    public UserService(String filePath) {
        this.users = new ConcurrentHashMap<>();
        this.filePath = filePath;
        readFromFile();
    }

    private File getFile() {
        File file = new File(filePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
                saveToFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file;
    }

    private void readFromFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(getFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(";");
                long userId = Long.parseLong(parts[0]);
                long chatId = Long.parseLong(parts[1]);
                String firstName = parts[2];
                String lastName = parts[3];
                String username = parts[4];
                UserStatus status = UserStatus.valueOf(parts[5]);
                String openAiUser = parts.length > 6 && !parts[6].equals("null") ? parts[6] : null;
                User user = new User(userId, chatId, firstName, lastName, username, status, openAiUser);
                users.put(userId, user);
            }
        } catch (FileNotFoundException e) {
            log.warning("File not found!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveToFile() {
        File file = getFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            new PrintWriter(file).close();
            for (User user : users.values()) {
                StringBuilder sb = new StringBuilder();
                sb.append(user.getUserId()).append(";")
                        .append(user.getChatId()).append(";")
                        .append(user.getFirstName()).append(";")
                        .append(user.getLastName()).append(";")
                        .append(user.getUsername()).append(";")
                        .append(user.getStatus().name()).append(";")
                        .append(user.getOpenAiUser()).append("\n");
                bw.write(sb.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isApprovedUser(long userId) {
        return Optional.ofNullable(users.get(userId))
                .filter(o -> UserStatus.APPROVED.equals(o.getStatus()))
                .isPresent();
    }

    public User createUser(org.telegram.telegrambots.meta.api.objects.User user, long chatId) {
        User u = new User(user.getId(), chatId);
        u.setUsername(user.getUserName());
        u.setFirstName(user.getFirstName());
        u.setLastName(user.getLastName());
        u.setStatus(UserStatus.PENDING);
        u.setOpenAiUser(String.valueOf(UUID.randomUUID()));
        users.put(user.getId(), u);
        return u;
    }

    public User updateStatus(org.telegram.telegrambots.meta.api.objects.User tgUser, long chatId, UserStatus status) {
        User user = users.getOrDefault(tgUser.getId(), createUser(tgUser, chatId));
        user.setStatus(status);
        if (!hasText(user.getOpenAiUser())) user.setOpenAiUser(UUID.randomUUID().toString());
        if (user.getMessages() == null) user.setMessages(new ArrayList<>());
        users.put(tgUser.getId(), user);
        saveToFile();
        return user;
    }

    public User updateStatus(long userId, UserStatus status) throws NotFoundException {
        User user = Optional.ofNullable(users.get(userId))
                .orElseThrow(() -> new NotFoundException("User not found with userId: " + userId));
        user.setStatus(status);
        if (user.getMessages() == null) user.setMessages(new ArrayList<>());
        users.put(userId, user);
        saveToFile();
        return user;
    }

    public User updateData(org.telegram.telegrambots.meta.api.objects.User tgUser, Long chatId) {
        User user = users.get(tgUser.getId());
        if (user == null) {
            User usr = createUser(tgUser, chatId);
            saveToFile();
            return usr;
        }

        boolean isUpdated = false;
        if (!Objects.equals(tgUser.getUserName(), user.getUsername())) {
            user.setUsername(tgUser.getUserName());
            isUpdated = true;
        }
        if (!Objects.equals(tgUser.getFirstName(), user.getFirstName())) {
            user.setFirstName(tgUser.getFirstName());
            isUpdated = true;
        }
        if (!Objects.equals(tgUser.getLastName(), user.getLastName())) {
            user.setLastName(tgUser.getLastName());
            isUpdated = true;
        }
        if (!Objects.equals(chatId, user.getChatId())) {
            user.setChatId(chatId);
            isUpdated = true;
        }
        if (!hasText(user.getOpenAiUser())) {
            user.setOpenAiUser(UUID.randomUUID().toString());
            isUpdated = true;
        }
        if (user.getMessages() == null) {
            user.setMessages(new ArrayList<>());
            isUpdated = true;
        }
        if (isUpdated) {
            users.put(tgUser.getId(), user);
            saveToFile();
        }
        return user;
    }
}

