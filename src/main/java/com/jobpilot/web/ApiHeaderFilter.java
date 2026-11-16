package com.jobpilot.web;

import com.jobpilot.service.SettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Protects the local API:
 * - any state-changing /api call must carry the custom X-JobPilot header (browsers will not let other
 *   websites send it cross-origin), and
 * - requests from the Chrome extension must carry the pairing token (X-JobPilot-Token).
 */
@Component
public class ApiHeaderFilter extends OncePerRequestFilter {

    private final SettingsService settings;

    public ApiHeaderFilter(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (path.startsWith("/api/") && !"OPTIONS".equals(req.getMethod())) {
            String m = req.getMethod();
            boolean safe = m.equals("GET") || m.equals("HEAD");
            String token = req.getHeader("X-JobPilot-Token");
            String origin = req.getHeader("Origin");
            boolean fromExtension = origin != null && origin.startsWith("chrome-extension://");

            if ((token != null || fromExtension) && !settings.matches(token)) {
                deny(res, HttpServletResponse.SC_UNAUTHORIZED, "Wrong or missing token. Copy it from JobPilot > Settings.");
                return;
            }
            if (token != null) settings.touch();
            if (!safe && req.getHeader("X-JobPilot") == null) {
                deny(res, HttpServletResponse.SC_FORBIDDEN, "Missing X-JobPilot header");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private void deny(HttpServletResponse res, int status, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
