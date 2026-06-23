package com.labreport.server.common;

public class Constants {
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String LOGIN_FAIL_KEY = "login:fail:";
    public static final String TOKEN_BLACKLIST_KEY = "token:blacklist:";
    public static final int MAX_LOGIN_FAIL_COUNT = 5;
    public static final int LOGIN_LOCK_MINUTES = 15;
    public static final long JWT_EXPIRATION_MS = 24 * 60 * 60 * 1000L; // 24h
    public static final int REPORT_POLL_INTERVAL_MS = 2000;
    public static final String AI_CONVERSATION_KEY = "ai:context:";
    public static final int AI_MAX_CONTEXT_MESSAGES = 20;
}
