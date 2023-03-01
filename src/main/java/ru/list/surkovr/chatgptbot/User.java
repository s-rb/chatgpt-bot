package ru.list.surkovr.chatgptbot;

import java.io.Serializable;

public class User implements Serializable {
    private long userId;
    private long chatId;
    private String firstName;
    private String lastName;
    private String username;
    private UserStatus status;
    private String openAiUser;

    public User(long userId, long chatId) {
        this.userId = userId;
        this.chatId = chatId;
    }

    public User(long userId, long chatId, String firstName, String lastName, String username, UserStatus status, String openAiUser) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.chatId = chatId;
        this.status = status;
        this.openAiUser = openAiUser;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public String getOpenAiUser() {
        return openAiUser;
    }

    public void setOpenAiUser(String openAiUser) {
        this.openAiUser = openAiUser;
    }
}