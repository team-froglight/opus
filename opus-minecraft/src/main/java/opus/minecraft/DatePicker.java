package opus.minecraft;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import opus.core.DateInput;
import opus.core.UiNode;
import org.lwjgl.glfw.GLFW;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Inline calendar; editing the ISO draft and picking a day share one controlled value. */
final class DatePicker {
    private LocalDate cursor;
    private boolean keyboard;
    static boolean expanded(UiNode node) { return Boolean.TRUE.equals(node.getProps().get("expanded")); }
    void open(UiNode node) {
        cursor = DateInput.parseOrNull((String) node.getProps().get("value"));
        if (cursor == null) cursor = LocalDate.now();
        keyboard = true;
        call(node, "onExpandedChange", true);
    }
    void close(UiNode node) { keyboard = false; call(node, "onExpandedChange", false); }
    void edit() { keyboard = false; }
    boolean navigating(UiNode node) { return keyboard && expanded(node); }

    boolean key(UiNode node, int key) {
        if (!expanded(node) || !keyboard) return false;
        LocalDate month = (LocalDate) node.getProps().get("month");
        if (cursor == null) cursor = month;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            call(node, "onDateSelect", cursor); return true;
        }
        LocalDate next = switch (key) {
            case GLFW.GLFW_KEY_LEFT -> cursor.minusDays(1);
            case GLFW.GLFW_KEY_RIGHT -> cursor.plusDays(1);
            case GLFW.GLFW_KEY_UP -> cursor.minusDays(7);
            case GLFW.GLFW_KEY_DOWN -> cursor.plusDays(7);
            case GLFW.GLFW_KEY_PAGE_UP -> cursor.minusMonths(1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> cursor.plusMonths(1);
            case GLFW.GLFW_KEY_HOME -> cursor.withDayOfMonth(1);
            case GLFW.GLFW_KEY_END -> cursor.withDayOfMonth(cursor.lengthOfMonth());
            default -> cursor;
        };
        if (next.getYear() >= 1 && next.getYear() <= 9999) {
            cursor = next;
            if (!cursor.withDayOfMonth(1).equals(month)) call(node, "onMonthChange", cursor);
        }
        return true;
    }

    boolean click(UiNode node, float x, float y, float width, double mouseX, double mouseY) {
        keyboard = true;
        LocalDate month = (LocalDate) node.getProps().get("month");
        if (mouseY < y + 28) {
            int shift = mouseX < x + 32 ? -1 : mouseX > x + width - 32 ? 1 : 0;
            LocalDate next = month.plusMonths(shift);
            if (shift != 0 && next.getYear() >= 1 && next.getYear() <= 9999) {
                cursor = next; call(node, "onMonthChange", next);
            }
        } else if (mouseY >= y + 44 && mouseY < y + 176) {
            int col = (int) ((mouseX - x - 8) / ((width - 16) / 7f));
            int row = (int) ((mouseY - y - 44) / 22);
            if (mouseX >= x + 8 && mouseX < x + width - 8 && col >= 0 && col < 7) {
                LocalDate date = DateInput.calendarStart(month).plusDays(row * 7L + col);
                if (date.getYear() >= 1 && date.getYear() <= 9999) call(node, "onDateSelect", date);
            }
        }
        return true;
    }

    void draw(UiNode node, GuiGraphics g, Font font, float x, float y, float width, int accent) {
        LocalDate month = (LocalDate) node.getProps().get("month");
        LocalDate selected = DateInput.parseOrNull((String) node.getProps().get("value"));
        LocalDate today = LocalDate.now();
        ShapeRenderer.border(g, x, y, width, DateInput.CALENDAR_HEIGHT, 5, 1, 0xFF435367, 0xFF101924, null);
        String title = month.format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.getDefault()));
        g.drawString(font, font.plainSubstrByWidth(title, Math.max(1, (int) width - 72)), (int) x + 36, (int) y + 10, 0xFFEAF0F7, false);
        arrow(g, x + 16, y + 14, -1); arrow(g, x + width - 16, y + 14, 1);
        float cell = (width - 16) / 7f;
        for (int i = 0; i < 7; i++) {
            String label = java.time.DayOfWeek.of(i + 1).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault());
            label = font.plainSubstrByWidth(label, Math.max(1, (int) cell - 4));
            g.drawString(font, label, (int) (x + 8 + i * cell + (cell - font.width(label)) / 2), (int) y + 32, 0xFF98A8BB, false);
        }
        LocalDate start = DateInput.calendarStart(month);
        for (int i = 0; i < 42; i++) {
            LocalDate date = start.plusDays(i);
            if (date.getYear() < 1 || date.getYear() > 9999) continue;
            float left = x + 8 + i % 7 * cell, top = y + 44 + i / 7 * 22;
            boolean chosen = date.equals(selected), pointed = keyboard && date.equals(cursor);
            if (chosen) ShapeRenderer.rounded(g, left + 1, top + 1, cell - 2, 20, 3, 3, 3, 3, 0xFF286F66);
            if (pointed) ShapeRenderer.border(g, left + 1, top + 1, cell - 2, 20, 3, 1, accent, null, null);
            String label = Integer.toString(date.getDayOfMonth());
            g.drawString(font, label, (int) (left + (cell - font.width(label)) / 2), (int) top + 7,
                chosen ? 0xFFFFFFFF : date.getMonth() == month.getMonth() ? 0xFFEAF0F7 : 0xFF98A8BB, false);
            if (date.equals(today)) g.fill((int) (left + cell / 2 - 2), (int) top + 18, (int) (left + cell / 2 + 2), (int) top + 19, accent);
        }
    }

    static void icon(GuiGraphics g, float x, float y, int color) {
        ShapeRenderer.border(g, x, y + 2, 12, 11, 2, 1, color, null, null);
        ShapeRenderer.line(g, x, y + 6, x + 12, y + 6, 1, color);
        ShapeRenderer.line(g, x + 3, y, x + 3, y + 4, 1.3f, color);
        ShapeRenderer.line(g, x + 9, y, x + 9, y + 4, 1.3f, color);
    }
    private static void arrow(GuiGraphics g, float x, float y, int direction) {
        ShapeRenderer.line(g, x - direction * 2, y - 3, x + direction * 2, y, 1.4f, 0xFFEAF0F7);
        ShapeRenderer.line(g, x + direction * 2, y, x - direction * 2, y + 3, 1.4f, 0xFFEAF0F7);
    }
    @SuppressWarnings("unchecked") private static <T> void call(UiNode node, String name, T value) {
        var callback = (Function1<T, Unit>) node.getProps().get(name);
        if (callback != null) callback.invoke(value);
    }
}
