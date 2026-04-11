package com.umlcollab.client.config;

public class ServerConfig {
    private static String getEnvOrDefault(String key, String defaultVal) {
        String val = System.getenv(key);
        return (val == null || val.isBlank()) ? defaultVal : val;
    }

    public static String getApiAddress() {
        return getEnvOrDefault("UML_SERVER_ADDRESS", "localhost:8888");
    }

    public static String getWebSocketAddress() {
        String wsAddr = getEnvOrDefault("UML_SERVER_ADDRESS", "localhost:8887");
        if (!wsAddr.startsWith("ws://") && !wsAddr.startsWith("wss://")) {
            wsAddr = "ws://" + wsAddr;
        }
        return wsAddr;
    }

    public static boolean useRemoteApi() {
        String val = System.getenv("UML_USE_REMOTE_API");
        // Default to true - use remote server unless explicitly set to false
        if (val == null || val.isBlank()) {
            return true;
        }
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }
}