package com.cachelab.server;

import com.cachelab.server.service.RunManager;

public class CacheLabApplication {

    public static void main(String[] args) {
        int port = 8080;
        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        try {
            RunManager runManager = new RunManager();
            EmbeddedHttpServer server = new EmbeddedHttpServer(port, runManager);
            server.start();

            // Keep main thread alive
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Failed to start CacheLab server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
