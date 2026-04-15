package com.umlcollab.client.config;

public class ServerConfig {
    private static String getEnvOrDefault(String key, String defaultVal) {
        String val = System.getenv(key);
        return (val == null || val.isBlank()) ? defaultVal : val;
    }

    public static String getApiAddress() {
        String addr = getEnvOrDefault("UML_SERVER_ADDRESS", "localhost");
        if (!addr.contains(":")) {
            addr = addr + ":8080";
        }
        return addr;
    }

    public static String getWebSocketAddress() {
        String wsAddr = getEnvOrDefault("UML_SERVER_ADDRESS", "localhost");
        if (!wsAddr.contains(":")) {
            wsAddr = wsAddr + ":8887";
        } else {
            wsAddr = wsAddr.replace(":8080", ":8887").replace(":8081", ":8887");
        }
        if (!wsAddr.startsWith("ws://") && !wsAddr.startsWith("wss://")) {
            wsAddr = "ws://" + wsAddr;
        }
        return wsAddr;
    }

    public static boolean useRemoteApi() {
        String val = System.getenv("UML_USE_REMOTE_API");
        String serverAddr = getEnvOrDefault("UML_SERVER_ADDRESS", "localhost");
        if (!serverAddr.equals("localhost")) {
            return true;
        }
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }
}