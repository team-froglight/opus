package opus.minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Filesystem discovery, kept independent of Minecraft and native font loading. */
final class FontDiscovery {
    record Entry(String id, String name, Path path) {}

    static List<Path> systemDirectories(String os, Path home, Map<String, String> environment) {
        String platform = os.toLowerCase(Locale.ROOT);
        if (platform.startsWith("windows")) {
            List<Path> roots = new ArrayList<>();
            String windows = environment.get("SystemRoot");
            if (windows != null) roots.add(Path.of(windows, "Fonts"));
            String local = environment.get("LOCALAPPDATA");
            if (local != null) roots.add(Path.of(local, "Microsoft", "Windows", "Fonts"));
            return List.copyOf(roots);
        }
        if (platform.contains("mac") || platform.contains("darwin")) {
            return List.of(home.resolve("Library/Fonts"), Path.of("/Library/Fonts"), Path.of("/System/Library/Fonts"));
        }
        String dataHome = environment.get("XDG_DATA_HOME");
        Path userData = dataHome == null || dataHome.isBlank() ? home.resolve(".local/share") : Path.of(dataHome);
        return List.of(userData.resolve("fonts"), home.resolve(".fonts"), Path.of("/usr/local/share/fonts"), Path.of("/usr/share/fonts"));
    }

    static List<Entry> scan(List<Path> directories, String prefix) {
        Map<String, Entry> found = new LinkedHashMap<>();
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) continue;
            try (var paths = Files.walk(directory, 8)) {
                paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ttf"))
                    .sorted().forEach(path -> {
                        String filename = path.getFileName().toString();
                        String name = filename.substring(0, filename.length() - 4);
                        String baseId = prefix + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
                        Path absolute = path.toAbsolutePath().normalize();
                        String id = baseId;
                        for (int suffix = 2; found.containsKey(id) && !found.get(id).path().equals(absolute); suffix++) {
                            id = baseId + "_" + suffix;
                        }
                        found.putIfAbsent(id, new Entry(id, name, absolute));
                    });
            } catch (IOException | SecurityException | java.io.UncheckedIOException ignored) {
                // A missing or unreadable system directory does not disable other fonts.
            }
        }
        return List.copyOf(found.values());
    }
}
