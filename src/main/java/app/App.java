package app;

import javax.swing.*;
import java.io.File;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame(baseDir()).setVisible(true));
    }

    static File baseDir() {
        try {
            File loc = new File(App.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (loc.isFile()) return loc.getParentFile();
        } catch (Exception ignored) {
        }
        return new File(System.getProperty("user.dir"));
    }
}