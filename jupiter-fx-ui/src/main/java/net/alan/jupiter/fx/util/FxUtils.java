package net.alan.jupiter.fx.util;

import javafx.application.Platform;
import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public final class FxUtils {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "fx-worker");
        t.setDaemon(true);
        return t;
    });

    private FxUtils() {}

    public static <T> void runAsync(Supplier<T> work, Consumer<T> onSuccess) {
        runAsync(work, onSuccess, null);
    }

    public static <T> void runAsync(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            log.error("异步任务执行失败", ex);
            if (onError != null) {
                Platform.runLater(() -> onError.accept(ex));
            } else {
                Platform.runLater(() -> DialogUtils.showError("操作失败", "系统异常，请稍后再试"));
            }
        });
        EXECUTOR.execute(task);
    }

    public static void runAsync(Runnable work) {
        runAsync(() -> { work.run(); return null; }, v -> {});
    }

    public static void runOnUi(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }
}