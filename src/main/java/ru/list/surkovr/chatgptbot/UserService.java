package ru.list.surkovr.chatgptbot;

import javax.ws.rs.NotFoundException;
import java.io.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class UserService {
    private static final Logger log = Logger.getLogger(UserService.class.getName());

    private final String filePath;
    private ConcurrentHashMap<Long, User> users;

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
        try (FileInputStream in = new FileInputStream(getFile());
             ObjectInputStream ois = new ObjectInputStream(in)) {
            this.users = (ConcurrentHashMap<Long, User>) ois.readObject();
        } catch (FileNotFoundException e) {
            log.warning("File not found!");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void saveToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(getFile()))) {
            oos.writeObject(users);
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
        users.put(user.getId(), u);
        return u;
    }

    public User updateStatus(org.telegram.telegrambots.meta.api.objects.User tgUser, long chatId, UserStatus status) {
        User user = users.getOrDefault(tgUser.getId(), createUser(tgUser, chatId));
        user.setStatus(status);
        users.put(tgUser.getId(), user);
        saveToFile();
        return user;
    }

    public User updateStatus(long userId, UserStatus status) throws NotFoundException {
        User user = Optional.ofNullable(users.get(userId))
                .orElseThrow(() -> new NotFoundException("User not found with userId: " + userId));
        user.setStatus(status);
        users.put(userId, user);
        saveToFile();
        return user;
    }
}

