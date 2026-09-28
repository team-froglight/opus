package opus.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Font discovery and registration. Mutating operations run on the client/render thread. */
public final class Fonts {
    public enum Source { BUILTIN, SYSTEM, FILE }
    public record FontOption(String id, String label, Source source) {}

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Map<String, SdfFont> CACHE = new HashMap<>();
    private static final Map<String, Path> REGISTERED = new LinkedHashMap<>();
    private static final Set<String> UNAVAILABLE = new HashSet<>();
    private static List<FontDiscovery.Entry> systemFonts;
    private static List<FontDiscovery.Entry> fileFonts;
    private static Path customDirectory;
    private static final List<String> PREFERRED = List.of("segoeui", "segoeuib", "arial", "consola", "cour", "times", "verdana", "tahoma", "dejavusans", "liberationsans-regular");
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("segoeui", "Segoe UI"), Map.entry("segoeuib", "Segoe UI Bold"),
        Map.entry("arial", "Arial"), Map.entry("consola", "Consolas"), Map.entry("cour", "Courier New"),
        Map.entry("times", "Times New Roman"), Map.entry("verdana", "Verdana"), Map.entry("tahoma", "Tahoma"),
        Map.entry("dejavusans", "DejaVu Sans"), Map.entry("liberationsans-regular", "Liberation Sans"));

    private Fonts() {}

    public static List<FontOption> builtins() {
        return List.of(new FontOption("minecraft:default", "Default", Source.BUILTIN),
            new FontOption("minecraft:uniform", "Uniform", Source.BUILTIN),
            new FontOption("minecraft:alt", "Enchantment", Source.BUILTIN));
    }

    /** Cached recursive discovery in system and per-user font directories. */
    public static List<FontOption> system() {
        return systemEntries().stream().map(entry -> new FontOption(entry.id(), label(entry.name()), Source.SYSTEM)).toList();
    }

    public static List<FontOption> files() {
        List<FontOption> result = new ArrayList<>();
        for (var entry : fileEntries()) result.add(new FontOption(entry.id(), label(entry.name()), Source.FILE));
        REGISTERED.forEach((id, path) -> result.add(new FontOption(id, path.getFileName().toString(), Source.FILE)));
        return List.copyOf(result);
    }

    public static List<FontOption> available() {
        List<FontOption> result = new ArrayList<>(builtins());
        result.addAll(system());
        result.addAll(files());
        return List.copyOf(result);
    }

    /** Use an explicit font for consistent typography on different computers. Returns the ID for Text's fontId. */
    public static String register(String name, Path ttf) {
        String id = "opus:custom/" + name;
        if (ResourceLocation.tryParse(id) == null || name.isBlank()) throw new IllegalArgumentException("Invalid font name: " + name);
        Path path = ttf.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !path.toString().toLowerCase(Locale.ROOT).endsWith(".ttf")) {
            throw new IllegalArgumentException("Expected a readable .ttf file: " + path);
        }
        SdfFont previous = CACHE.remove(id);
        if (previous != null) previous.close();
        REGISTERED.put(id, path);
        UNAVAILABLE.remove(id);
        return id;
    }

    public static Path directory() {
        return customDirectory != null ? customDirectory
            : Minecraft.getInstance().gameDirectory.toPath().resolve("config/opus/fonts").toAbsolutePath().normalize();
    }

    public static void setDirectory(Path directory) {
        customDirectory = directory.toAbsolutePath().normalize();
        refresh();
    }

    /** Rescans after fonts are installed or added; also releases old glyph atlases. */
    public static void refresh() {
        clearCache();
        systemFonts = null;
        fileFonts = null;
    }

    private static String label(String name) {
        return LABELS.getOrDefault(name.toLowerCase(Locale.ROOT), name.replace('-', ' ').replace('_', ' '));
    }

    private static List<FontDiscovery.Entry> systemEntries() {
        if (systemFonts == null) {
            var roots = FontDiscovery.systemDirectories(System.getProperty("os.name", ""),
                Path.of(System.getProperty("user.home", ".")), System.getenv());
            systemFonts = FontDiscovery.scan(roots, "opus:sys/").stream()
                .sorted(Comparator.comparingInt((FontDiscovery.Entry entry) -> {
                    int index = PREFERRED.indexOf(entry.name().toLowerCase(Locale.ROOT));
                    return index < 0 ? Integer.MAX_VALUE : index;
                }).thenComparing(FontDiscovery.Entry::name, String.CASE_INSENSITIVE_ORDER)).toList();
        }
        return systemFonts;
    }

    private static List<FontDiscovery.Entry> fileEntries() {
        if (fileFonts == null) fileFonts = FontDiscovery.scan(List.of(directory()), "opus:file/");
        return fileFonts;
    }

    private static Path resolve(String id) {
        if (REGISTERED.containsKey(id)) return REGISTERED.get(id);
        List<FontDiscovery.Entry> entries = id.startsWith("opus:sys/") ? systemEntries() : fileEntries();
        return entries.stream().filter(entry -> entry.id().equals(id)).map(FontDiscovery.Entry::path).findFirst().orElse(null);
    }

    private static SdfFont get(String id) {
        if (UNAVAILABLE.contains(id)) return null;
        SdfFont cached = CACHE.get(id);
        if (cached != null) return cached;
        Path path = resolve(id);
        if (path == null || !Files.isRegularFile(path)) {
            UNAVAILABLE.add(id);
            LOGGER.warn("Opus: font {} unavailable; using the default font", id);
            return null;
        }
        SdfFont font = SdfFont.bake(id, path, Minecraft.getInstance().getTextureManager());
        CACHE.put(id, font);
        return font;
    }

    static float measure(String id, String text, float px, float maxWidth) {
        SdfFont font = get(id);
        return font == null || font.isBroken() ? -1f : font.measure(text, px, maxWidth);
    }

    static boolean draw(GuiGraphics graphics, String id, String text, float x, float y, float px, int color) {
        SdfFont font = get(id);
        return font != null && font.draw(graphics, text, x, y, px, color);
    }

    static boolean isCustom(String id) {
        return id != null && (id.startsWith("opus:sys/") || id.startsWith("opus:file/") || id.startsWith("opus:custom/"));
    }

    static void clearCache() {
        CACHE.values().forEach(SdfFont::close);
        CACHE.clear();
        UNAVAILABLE.clear();
    }
}
