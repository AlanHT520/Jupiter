package net.alan.jupiter.fx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import net.alan.jupiter.ChinanApplication;
import net.alan.jupiter.fx.config.FxContext;
import net.alan.jupiter.fx.config.StageManager;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class FxLauncher extends Application {

    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        notifyPreloader(new SplashPreloader.StageNotification("正在启动 Spring 容器"));
        this.context = new SpringApplicationBuilder(ChinanApplication.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run(desktopArgs(getParameters().getRaw().toArray(new String[0])));

        notifyPreloader(new SplashPreloader.StageNotification("正在装载模块组件"));
        sleepBriefly();

        notifyPreloader(new SplashPreloader.StageNotification("正在初始化数据库连接"));
        sleepBriefly();

        notifyPreloader(new SplashPreloader.StageNotification("正在准备界面资源"));
        sleepBriefly();

        notifyPreloader(new SplashPreloader.StageNotification("正在加载系统配置"));
        sleepBriefly();
    }

    @Override
    public void start(Stage primaryStage) {
        notifyPreloader(new SplashPreloader.StageNotification("启动完成"));
        sleepBriefly();

        StageManager stageManager = context.getBean(StageManager.class);
        FxContext fxContext = context.getBean(FxContext.class);
        fxContext.setAsDesktopUser();
        stageManager.showMain(primaryStage);
    }

    @Override
    public void stop() {
        context.close();
        Platform.exit();
    }

    private static String[] desktopArgs(String[] raw) {
        String[] args = new String[raw.length + 1];
        args[0] = "--Chinan.runtime.mode=desktop";
        System.arraycopy(raw, 0, args, 1, raw.length);
        return args;
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(220);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
