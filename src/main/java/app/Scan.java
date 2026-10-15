package app;

import java.io.File;

public record Scan(File file, String text) {
    @Override
    public String toString() {
        return file.getName();
    }
}