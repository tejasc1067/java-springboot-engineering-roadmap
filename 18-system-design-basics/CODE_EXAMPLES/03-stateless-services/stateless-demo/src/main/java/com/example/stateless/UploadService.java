package com.example.stateless;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Saves uploaded avatars under a directory: the instance's own disk, or storage every instance shares. */
public class UploadService {

    private final Path directory;

    public UploadService(Path directory) {
        this.directory = directory;
    }

    public void save(String fileName, byte[] content) throws IOException {
        Files.createDirectories(directory);
        Files.write(directory.resolve(fileName), content);
    }

    public Optional<byte[]> load(String fileName) throws IOException {
        Path file = directory.resolve(fileName);
        return Files.exists(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
    }
}
