package net.alan.jupiter.common.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class IdUtils {

    private IdUtils() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String shortUuid() {
        return uuid().substring(0, 16);
    }

    public static String taskNo() {
        return "T" + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}