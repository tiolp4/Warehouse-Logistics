package ru.warehouse.logistics.util;

import ru.warehouse.logistics.model.User;

public final class Session {

    private static volatile User currentUser;
    private static volatile String token;

    private Session() {}

    public static void login(User user, String jwt) {
        currentUser = user;
        token = jwt;
    }
    public static void logout()         { currentUser = null; token = null; }
    public static User getUser()        { return currentUser; }
    public static String getToken()     { return token; }
    public static boolean isLoggedIn()  { return currentUser != null && token != null; }
}
