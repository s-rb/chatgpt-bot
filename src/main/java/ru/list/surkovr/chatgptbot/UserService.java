package ru.list.surkovr.chatgptbot;

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
            // файл еще не создан, ничего не делаем
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

    public void approveUser(org.telegram.telegrambots.meta.api.objects.User from) {
        User user = users.getOrDefault(from.getId(), createUser(from));
        user.setStatus(UserStatus.APPROVED);
        users.put(user.getUserId(), user);
        saveToFile();
    }

    public User createUser(org.telegram.telegrambots.meta.api.objects.User user) {
        User u = new User(user.getId());
        u.setUsername(user.getUserName());
        u.setFirstName(user.getFirstName());
        u.setLastName(user.getLastName());
        u.setStatus(UserStatus.PENDING);
        users.put(user.getId(), u);
        return u;
    }

    public void rejectUser(long userId) {
        User user = users.getOrDefault(userId, createUser(userId));
        user.setStatus(UserStatus.DECLINED);
        users.put(userId, user);
        saveToFile();
    }

    private User createUser(long userId) {
        return new User(userId);
    }

    public User get(long userId) {
        return users.get(userId);
    }

    public User updateStatus(long userId, UserStatus status) {
        User user = users.getOrDefault(userId, createUser(userId));
        user.setStatus(status);
        users.put(userId, user);
        saveToFile();
        return user;
    }
}

