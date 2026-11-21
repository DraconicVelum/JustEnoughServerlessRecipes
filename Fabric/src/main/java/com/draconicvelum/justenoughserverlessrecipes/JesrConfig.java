package com.draconicvelum.justenoughserverlessrecipes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.List;
import java.util.Properties;
import org.slf4j.LoggerFactory;

public final class JesrConfig {
    static final int SCHEMA_VERSION = 1;
    private static final Path CONFIG_PATH = Path.of("config", "justenoughserverlessrecipes.properties");
    private static final List<String> KEYS = List.of("enableJeiTradesTransfer", "enableJeiTradesFallback",
            "enableJustEnoughBreedingFallback", "enableJustEnoughResourcesFallback");
    private static final String COMMENTS = "JESR client settings. Restart Minecraft after changing.\n"
            + "Each addon fallback is independent; missing options are added automatically on upgrade.\n"
            + "Normal click: one trade. Shift+click: maximum complete trades within payment stack limits and stock.\n"
            + "Fallbacks use local data only. Server-only changes are unavailable.";
    private JesrConfig() {}

    public static boolean tradeTransferEnabled() {
        return load().tradeTransfer();
    }

    public record Settings(boolean tradeTransfer, boolean tradesFallback, boolean breedingFallback, boolean resourcesFallback) {}

    public static Settings defaults() { return new Settings(true, true, true, true); }

    public static Settings load() {
        return load(CONFIG_PATH);
    }

    static Settings load(Path path) {
        return load(path, modVersion());
    }

    static synchronized Settings load(Path path, String version) {
        try {
            Properties properties;
            try {
                properties = read(path);
            } catch (IllegalArgumentException e) {
                Files.copy(path, path.resolveSibling(path.getFileName() + ".invalid.bak"), StandardCopyOption.REPLACE_EXISTING);
                properties = new Properties();
                LoggerFactory.getLogger("justenoughserverlessrecipes").warn("Invalid config syntax; backed up original and restored defaults", e);
            }
            if (schema(properties) > SCHEMA_VERSION) return settings(properties);
            boolean changed = false;
            for (String key : KEYS) {
                String value = properties.getProperty(key);
                if (value != null) value = value.trim();
                if (value == null || !(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))) {
                    properties.setProperty(key, "true");
                    changed = true;
                } else if (!value.equals(properties.getProperty(key))) {
                    properties.setProperty(key, value);
                    changed = true;
                }
            }
            if (!Integer.toString(SCHEMA_VERSION).equals(properties.getProperty("configVersion"))
                    || !version.equals(properties.getProperty("modVersion"))) changed = true;
            if (changed) {
                if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".pre-update.bak"), StandardCopyOption.REPLACE_EXISTING);
                stamp(properties, version);
                write(path, properties);
            }
            return settings(properties);
        } catch (IOException | IllegalArgumentException e) {
            LoggerFactory.getLogger("justenoughserverlessrecipes").warn("Unable to read JESR config; optional integrations disabled", e);
            return new Settings(false, false, false, false);
        }
    }

    public static void save(Settings settings) throws IOException { save(CONFIG_PATH, settings, modVersion()); }

    static synchronized void save(Path path, Settings settings, String version) throws IOException {
        Properties properties = read(path);
        if (schema(properties) > SCHEMA_VERSION) throw new IOException("Config belongs to a newer JESR schema");
        boolean[] values = {settings.tradeTransfer(), settings.tradesFallback(), settings.breedingFallback(), settings.resourcesFallback()};
        for (int i = 0; i < KEYS.size(); i++) properties.setProperty(KEYS.get(i), Boolean.toString(values[i]));
        stamp(properties, version);
        write(path, properties);
    }

    private static Settings settings(Properties properties) {
        return new Settings(enabled(properties, KEYS.get(0)), enabled(properties, KEYS.get(1)),
                enabled(properties, KEYS.get(2)), enabled(properties, KEYS.get(3)));
    }

    private static boolean enabled(Properties properties, String key) {
        return Boolean.parseBoolean(properties.getProperty(key, "true").trim());
    }

    private static int schema(Properties properties) {
        try { return Integer.parseInt(properties.getProperty("configVersion", "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    private static void stamp(Properties properties, String version) {
        properties.setProperty("configVersion", Integer.toString(SCHEMA_VERSION));
        properties.setProperty("modVersion", version);
    }

    private static Properties read(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) { properties.load(reader); }
        }
        return properties;
    }

    private static void write(Path path, Properties properties) throws IOException {
        Path absolute = path.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), "jesr-config-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) { properties.store(writer, COMMENTS); }
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static String modVersion() {
        try (var stream = JesrConfig.class.getResourceAsStream("/jesr-version.properties")) {
            if (stream == null) throw new IOException("Missing JESR version resource");
            Properties version = new Properties();
            version.load(stream);
            return version.getProperty("version", "development");
        } catch (IOException e) {
            LoggerFactory.getLogger("justenoughserverlessrecipes").warn("Unable to read JESR version", e);
            return "development";
        }
    }
}
