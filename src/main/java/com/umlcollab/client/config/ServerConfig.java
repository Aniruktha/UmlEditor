package com.umlcollab.client.config;

public class ServerConfig {
    private static final int API_PORT = 8080;
    private static final int WS_PORT = 8887;

    private static String getEnvOrDefault(String key, String defaultVal) {
        String val = System.getenv(key);
        return (val == null || val.isBlank()) ? defaultVal : val;
    }

    public static String getApiAddress() {
        String addr = getEnvOrDefault("UML_SERVER_ADDRESS", "localhost");
        String baseAddr = extractBaseAddress(addr);
        return baseAddr + ":" + API_PORT;
    }

    public static String getWebSocketAddress() {
        String addr = getEnvOrDefault("UML_SERVER_ADDRESS", "localhost");
        String baseAddr = extractBaseAddress(addr);
        String wsAddr = baseAddr + ":" + WS_PORT;
        if (!wsAddr.startsWith("ws://") && !wsAddr.startsWith("wss://")) {
            wsAddr = "ws://" + wsAddr;
        }
        return wsAddr;
    }

    private static String extractBaseAddress(String addr) {
        if (addr == null || addr.isBlank()) {
            return "localhost";
        }
        addr = addr.trim();
        if (addr.startsWith("http://")) {
            addr = addr.substring(7);
        } else if (addr.startsWith("https://")) {
            addr = addr.substring(8);
        } else if (addr.startsWith("ws://")) {
            addr = addr.substring(3);
        } else if (addr.startsWith("wss://")) {
            addr = addr.substring(4);
        }
        int colonIndex = addr.lastIndexOf(':');
        if (colonIndex > 0) {
            return addr.substring(0, colonIndex);
        }
        int slashIndex = addr.indexOf('/');
        if (slashIndex > 0) {
            return addr.substring(0, slashIndex);
        }
        return addr;
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