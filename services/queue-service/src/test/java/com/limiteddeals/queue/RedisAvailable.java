package com.limiteddeals.queue;

import java.net.InetSocketAddress;
import java.net.Socket;

/** 로컬 Redis(docker compose)가 떠 있을 때만 Redis 테스트를 돌린다. 없으면 건너뛴다. */
public final class RedisAvailable {

    private RedisAvailable() {
    }

    public static boolean isRunning() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 6379), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
