package me.usainsrht.announcement.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Platform independent view of config.yml (same file format on every platform). */
public final class AnnConfig {
    private Map<String, Object> root = Collections.emptyMap();

    /** Copies the bundled config.yml into dir when there is none yet. */
    public static void saveDefault(Path dir, Class<?> resourceOwner) throws IOException {
        Path target = dir.resolve("config.yml");
        if (Files.exists(target)) return;
        Files.createDirectories(dir);
        try (InputStream in = resourceOwner.getResourceAsStream("/config.yml")) {
            if (in == null) throw new IOException("bundled config.yml missing");
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @SuppressWarnings("unchecked")
    public void load(Path file) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Object parsed;
        try {
            parsed = MiniYaml.parse(text);
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid YAML: " + e.getMessage(), e);
        }
        root = parsed instanceof Map ? (Map<String, Object>) parsed : Collections.<String, Object>emptyMap();
    }

    private Object get(String path) {
        Object cur = root;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<?, ?>) cur).get(part);
        }
        return cur;
    }

    public boolean getBoolean(String path, boolean def) {
        Object o = get(path);
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof String) return Boolean.parseBoolean((String) o);
        return def;
    }

    public int getInt(String path, int def) {
        Object o = get(path);
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try {
                return Integer.parseInt(((String) o).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public String getString(String path, String def) {
        Object o = get(path);
        return o == null ? def : String.valueOf(o);
    }

    public List<String> getStringList(String path) {
        Object o = get(path);
        List<String> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object e : (List<?>) o) out.add(e == null ? "" : String.valueOf(e));
        } else if (o instanceof String) {
            out.add((String) o);
        }
        return out;
    }

    /** Announcements may be plain strings or maps like {message: "...", per_player: false}. */
    public List<Entry> getAnnouncements() {
        List<Entry> out = new ArrayList<>();
        Object o = get("announcements");
        if (!(o instanceof List)) return out;
        for (Object e : (List<?>) o) {
            if (e instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) e;
                Object msg = m.get("message");
                if (msg == null) continue;
                Object pp = m.get("per_player");
                out.add(new Entry(String.valueOf(msg), pp instanceof Boolean ? (Boolean) pp : null));
            } else if (e != null) {
                out.add(new Entry(String.valueOf(e), null));
            }
        }
        return out;
    }

    // ---- typed accessors -------------------------------------------------

    public boolean broadcast() {
        return getBoolean("broadcast", false);
    }

    public boolean ordered() {
        return getBoolean("ordered", false);
    }

    public int delaySeconds() {
        return Math.max(1, getInt("delay", 300));
    }

    public boolean perPlayer() {
        return getBoolean("placeholders.per_player", true);
    }

    public boolean customMiniMessage() {
        return getBoolean("use_custom_minimessage", true);
    }

    public List<String> template() {
        return getStringList("template");
    }

    public boolean clientEnabled() {
        return getBoolean("client.enabled", true);
    }
}
