package com.example.eyetracking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
public class AppSettingsService {
    private static final Set<String> DATA_RATES = new HashSet<>(Arrays.asList("30", "60", "120"));
    private static final Set<String> DIFFICULTIES = new HashSet<>(Arrays.asList("easy", "medium", "hard"));
    private static final Set<String> EXPORT_FORMATS = new HashSet<>(Arrays.asList("csv", "json", "xml"));
    private static final Set<String> SIMULATION_SPEEDS = new HashSet<>(Arrays.asList("slow", "normal", "fast"));

    @Value("${tobii.glasses.base-url:http://192.168.71.50:8080}")
    private String configuredGlassesBaseUrl;

    public UserSettings load(HttpSession session) {
        UserSettings settings = defaultSettings();
        if (session == null) {
            return settings;
        }

        settings.setGlassesAddress(getString(session, "glassesAddress", settings.getGlassesAddress()));
        settings.setGlassesPort(getInteger(session, "glassesPort", settings.getGlassesPort()));
        settings.setDataRate(getString(session, "dataRate", settings.getDataRate()));
        settings.setTimeout(getInteger(session, "timeout", settings.getTimeout()));
        settings.setDefaultDuration(getInteger(session, "defaultDuration", settings.getDefaultDuration()));
        settings.setDefaultDifficulty(getString(session, "defaultDifficulty", settings.getDefaultDifficulty()));
        settings.setAutoStart(getBoolean(session, "autoStart", settings.isAutoStart()));
        settings.setShowHeatmap(getBoolean(session, "showHeatmap", settings.isShowHeatmap()));
        settings.setKeepDataDays(getInteger(session, "keepDataDays", settings.getKeepDataDays()));
        settings.setExportFormat(getString(session, "exportFormat", settings.getExportFormat()));
        settings.setSimulationMode(getBoolean(session, "simulationMode", settings.isSimulationMode()));
        settings.setSimulationSpeed(getString(session, "simulationSpeed", settings.getSimulationSpeed()));
        return sanitize(settings);
    }

    public void save(HttpSession session, UserSettings settings) {
        if (session == null) {
            return;
        }

        UserSettings sanitized = sanitize(settings);
        session.setAttribute("glassesAddress", sanitized.getGlassesAddress());
        session.setAttribute("glassesPort", sanitized.getGlassesPort());
        session.setAttribute("dataRate", sanitized.getDataRate());
        session.setAttribute("timeout", sanitized.getTimeout());
        session.setAttribute("defaultDuration", sanitized.getDefaultDuration());
        session.setAttribute("defaultDifficulty", sanitized.getDefaultDifficulty());
        session.setAttribute("autoStart", sanitized.isAutoStart());
        session.setAttribute("showHeatmap", sanitized.isShowHeatmap());
        session.setAttribute("keepDataDays", sanitized.getKeepDataDays());
        session.setAttribute("exportFormat", sanitized.getExportFormat());
        session.setAttribute("simulationMode", sanitized.isSimulationMode());
        session.setAttribute("simulationSpeed", sanitized.getSimulationSpeed());
    }

    public String resolveGlassesBaseUrl(HttpSession session) {
        return load(session).getGlassesBaseUrl();
    }

    public UserSettings sanitize(UserSettings settings) {
        UserSettings defaults = defaultSettings();
        UserSettings sanitized = settings == null ? defaults : settings;

        String address = firstText(sanitized.getGlassesAddress(), defaults.getGlassesAddress());
        Endpoint endpoint = parseEndpoint(address, sanitized.getGlassesPort(), defaults.getGlassesPort());
        sanitized.setGlassesAddress(endpoint.host);
        sanitized.setGlassesPort(endpoint.port);
        sanitized.setDataRate(valueIn(sanitized.getDataRate(), DATA_RATES, defaults.getDataRate()));
        sanitized.setTimeout(clamp(sanitized.getTimeout(), 1, 60, defaults.getTimeout()));
        sanitized.setDefaultDuration(clamp(sanitized.getDefaultDuration(), 1, 240, defaults.getDefaultDuration()));
        sanitized.setDefaultDifficulty(valueIn(sanitized.getDefaultDifficulty(), DIFFICULTIES, defaults.getDefaultDifficulty()));
        sanitized.setKeepDataDays(clamp(sanitized.getKeepDataDays(), 1, 3650, defaults.getKeepDataDays()));
        sanitized.setExportFormat(valueIn(sanitized.getExportFormat(), EXPORT_FORMATS, defaults.getExportFormat()));
        sanitized.setSimulationSpeed(valueIn(sanitized.getSimulationSpeed(), SIMULATION_SPEEDS, defaults.getSimulationSpeed()));
        return sanitized;
    }

    private UserSettings defaultSettings() {
        Endpoint endpoint = parseEndpoint(configuredGlassesBaseUrl, null, 8080);
        UserSettings settings = new UserSettings();
        settings.setGlassesAddress(endpoint.host);
        settings.setGlassesPort(endpoint.port);
        settings.setDataRate("60");
        settings.setTimeout(10);
        settings.setDefaultDuration(30);
        settings.setDefaultDifficulty("medium");
        settings.setAutoStart(true);
        settings.setShowHeatmap(true);
        settings.setKeepDataDays(30);
        settings.setExportFormat("json");
        settings.setSimulationMode(false);
        settings.setSimulationSpeed("normal");
        return settings;
    }

    private Endpoint parseEndpoint(String value, Integer preferredPort, int fallbackPort) {
        String raw = firstText(value, "192.168.71.50");
        String withScheme = raw.startsWith("http://") || raw.startsWith("https://") ? raw : "http://" + raw;
        try {
            URI uri = URI.create(withScheme);
            String host = firstText(uri.getHost(), raw.replaceFirst("^https?://", "").split("[:/]", 2)[0]);
            int port = preferredPort != null ? preferredPort : uri.getPort();
            if (port < 1) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : fallbackPort;
            }
            return new Endpoint(host, clamp(port, 1, 65535, fallbackPort));
        } catch (IllegalArgumentException ex) {
            return new Endpoint("192.168.71.50", clamp(preferredPort, 1, 65535, fallbackPort));
        }
    }

    private String getString(HttpSession session, String key, String defaultValue) {
        Object value = session.getAttribute(key);
        return value == null ? defaultValue : value.toString();
    }

    private Integer getInteger(HttpSession session, String key, Integer defaultValue) {
        Object value = session.getAttribute(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Boolean getBoolean(HttpSession session, String key, Boolean defaultValue) {
        Object value = session.getAttribute(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    private String valueIn(String value, Set<String> allowed, String defaultValue) {
        String cleaned = firstText(value, defaultValue);
        return allowed.contains(cleaned) ? cleaned : defaultValue;
    }

    private int clamp(Integer value, int min, int max, int defaultValue) {
        int current = value == null ? defaultValue : value;
        return Math.max(min, Math.min(max, current));
    }

    private String firstText(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static class Endpoint {
        private final String host;
        private final int port;

        private Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    public static class UserSettings {
        private String glassesAddress;
        private Integer glassesPort;
        private String dataRate;
        private Integer timeout;
        private Integer defaultDuration;
        private String defaultDifficulty;
        private boolean autoStart;
        private boolean showHeatmap;
        private Integer keepDataDays;
        private String exportFormat;
        private boolean simulationMode;
        private String simulationSpeed;

        public String getGlassesBaseUrl() {
            String address = glassesAddress == null ? "192.168.71.50" : glassesAddress.trim();
            if (address.startsWith("http://") || address.startsWith("https://")) {
                address = address.replaceFirst("^https?://", "").split("[:/]", 2)[0];
            }
            return "http://" + address + ":" + (glassesPort == null ? 8080 : glassesPort);
        }

        public int getDataIntervalMs() {
            int rate = Integer.parseInt(dataRate == null ? "60" : dataRate);
            return Math.max(20, Math.round(1000.0f / rate));
        }

        public int getTargetDurationSeconds() {
            return Math.max(60, (defaultDuration == null ? 30 : defaultDuration) * 60);
        }

        public int getSimulationIntervalMs() {
            if ("slow".equals(simulationSpeed)) {
                return 120;
            }
            if ("fast".equals(simulationSpeed)) {
                return 25;
            }
            return 50;
        }

        public String getGlassesAddress() { return glassesAddress; }
        public void setGlassesAddress(String glassesAddress) { this.glassesAddress = glassesAddress; }
        public Integer getGlassesPort() { return glassesPort; }
        public void setGlassesPort(Integer glassesPort) { this.glassesPort = glassesPort; }
        public String getDataRate() { return dataRate; }
        public void setDataRate(String dataRate) { this.dataRate = dataRate; }
        public Integer getTimeout() { return timeout; }
        public void setTimeout(Integer timeout) { this.timeout = timeout; }
        public Integer getDefaultDuration() { return defaultDuration; }
        public void setDefaultDuration(Integer defaultDuration) { this.defaultDuration = defaultDuration; }
        public String getDefaultDifficulty() { return defaultDifficulty; }
        public void setDefaultDifficulty(String defaultDifficulty) { this.defaultDifficulty = defaultDifficulty; }
        public boolean isAutoStart() { return autoStart; }
        public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
        public boolean isShowHeatmap() { return showHeatmap; }
        public void setShowHeatmap(boolean showHeatmap) { this.showHeatmap = showHeatmap; }
        public Integer getKeepDataDays() { return keepDataDays; }
        public void setKeepDataDays(Integer keepDataDays) { this.keepDataDays = keepDataDays; }
        public String getExportFormat() { return exportFormat; }
        public void setExportFormat(String exportFormat) { this.exportFormat = exportFormat; }
        public boolean isSimulationMode() { return simulationMode; }
        public void setSimulationMode(boolean simulationMode) { this.simulationMode = simulationMode; }
        public String getSimulationSpeed() { return simulationSpeed; }
        public void setSimulationSpeed(String simulationSpeed) { this.simulationSpeed = simulationSpeed; }
    }
}
