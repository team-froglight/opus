package opus.minecraft;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FontDiscoveryTest {
    @Test void usesWindowsSystemAndUserDirectories() {
        var roots = FontDiscovery.systemDirectories("Windows 11", Path.of("home"),
            Map.of("SystemRoot", "windows", "LOCALAPPDATA", "local"));
        assertEquals(List.of(Path.of("windows/Fonts"), Path.of("local/Microsoft/Windows/Fonts")), roots);
    }

    @Test void darwinUsesMacDirectoriesRatherThanWindows() {
        var roots = FontDiscovery.systemDirectories("Darwin", Path.of("home"), Map.of());
        assertEquals(Path.of("home/Library/Fonts"), roots.getFirst());
        assertTrue(roots.contains(Path.of("/System/Library/Fonts")));
    }

    @Test void linuxHonorsTheUserDataDirectory() {
        var roots = FontDiscovery.systemDirectories("Linux", Path.of("home"), Map.of("XDG_DATA_HOME", "data"));
        assertEquals(Path.of("data/fonts"), roots.getFirst());
        assertTrue(roots.contains(Path.of("/usr/share/fonts")));
    }

    @Test void discoveryPreservesPathsAndSeparatesDuplicateNames() throws Exception {
        Path root = Files.createTempDirectory("opus-font-discovery-");
        Path nested = Files.createDirectory(root.resolve("nested"));
        Path first = Files.createFile(root.resolve("Sample.TTF"));
        Path second = Files.createFile(nested.resolve("Sample.ttf"));
        Path ignored = Files.createFile(root.resolve("notes.txt"));
        try {
            var fonts = FontDiscovery.scan(List.of(root, root.resolve("missing")), "opus:sys/");
            assertEquals(2, fonts.size());
            assertEquals(2, fonts.stream().map(FontDiscovery.Entry::id).distinct().count());
            assertTrue(fonts.stream().anyMatch(entry -> entry.path().equals(first.toAbsolutePath())));
            assertTrue(fonts.stream().anyMatch(entry -> entry.path().equals(second.toAbsolutePath())));
        } finally {
            Files.deleteIfExists(ignored);
            Files.deleteIfExists(second);
            Files.deleteIfExists(first);
            Files.deleteIfExists(nested);
            Files.deleteIfExists(root);
        }
    }
}
