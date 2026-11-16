package com.jobpilot.web;

import com.jobpilot.service.AiService;
import com.jobpilot.service.SettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    public record SettingsView(String token, String serverUrl, boolean aiConfigured, Long extensionLastSeen) {}

    private final SettingsService settings;
    private final AiService ai;

    @Value("${server.port:8765}")
    private int port;

    public SettingsController(SettingsService settings, AiService ai) {
        this.settings = settings;
        this.ai = ai;
    }

    @GetMapping
    public SettingsView get() {
        return new SettingsView(settings.token(), "http://127.0.0.1:" + port, ai.configured(), settings.lastSeen());
    }

    @PostMapping("/token/regenerate")
    public SettingsView regenerate() {
        settings.regenerate();
        return get();
    }
}
