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
        // Default to true if UML_SERVER_ADDRESS is set to a non-default value
        String serverAddr = getEnvOrDefault("UML_SERVER_ADDRESS", "localhost:8888");
        if (!serverAddr.equals("localhost:8888") && !serverAddr.equals("8888")) {
            return true;
        }
        // Also check explicit setting
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }
}