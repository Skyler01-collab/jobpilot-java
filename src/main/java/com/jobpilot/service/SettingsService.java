package com.jobpilot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Holds the pairing token the Chrome extension must present. Stored in data/token.txt. */
@Service
public class SettingsService {

    @Value("${jobpilot.data-dir:./data}")
    private String dataDir;

    private String token;
    private volatile long lastSeen = 0;

    public synchronized String token() {
        if (token != null) return token;
        try {
            Path f = Paths.get(dataDir, "token.txt");
            if (Files.exists(f)) token = Files.readString(f, StandardCharsets.UTF_8).trim();
            if (token == null || token.isBlank()) token = generate(f);
            return token;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read or create the token file: " + e.getMessage(), e);
        }
    }

    public synchronized String regenerate() {
        try {
            token = generate(Paths.get(dataDir, "token.txt"));
            return token;
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the token file: " + e.getMessage(), e);
        }
    }

    /** Called whenever a request arrives with the correct token (i.e. the extension is alive). */
    public void touch() {
        lastSeen = System.currentTimeMillis();
    }

    /** Epoch milliseconds of the last extension request, or null if none yet. */
    public Long lastSeen() {
        return lastSeen == 0 ? null : lastSeen;
    }

    public boolean matches(String given) {
        return given != null && MessageDigest.isEqual(token().getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    private String generate(Path file) throws IOException {
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        String t = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        Files.createDirectories(file.getParent());
        Files.writeString(file, t, StandardCharsets.UTF_8);
        return t;
    }
}
