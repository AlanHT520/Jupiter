package net.alan.jupiter;

import javafx.application.Application;
import net.alan.jupiter.fx.FxLauncher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.util.Arrays;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class JupiterApplication {

    public static void main(String[] args) {
        String mode = resolveMode(args);
        if (mode == null) {
            mode = "desktop";
        }

        switch (mode) {
            case "desktop" -> {
                    System.setProperty("javafx.preloader", "net.alan.jupiter.fx.SplashPreloader");
                    Application.launch(FxLauncher.class, args);
                }
            case "server" -> SpringApplication.run(JupiterApplication.class, args);
            default -> {
                System.err.println("Unknown mode: " + mode);
                System.err.println("Usage: java -jar jupiter-boot.jar --mode=<desktop|server>");
                System.exit(1);
            }
        }
    }

    private static String resolveMode(String[] args) {
        return Arrays.stream(args)
                .filter(a -> a.startsWith("--mode="))
                .map(a -> a.substring("--mode=".length()))
                .findFirst()
                .orElse(null);
    }
}