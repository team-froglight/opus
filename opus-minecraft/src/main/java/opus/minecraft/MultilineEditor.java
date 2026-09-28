package opus.minecraft;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.util.StringUtil;
import opus.core.TextEdits;

/** Corrects selection-aware capacity and Unicode cursor movement in the host's multiline model. */
final class MultilineEditor extends MultilineTextField {
    private int maximum = Integer.MAX_VALUE;
    MultilineEditor(Font font, int width) { super(font, width); }
    int maximum() { return maximum; }
    @Override public void setCharacterLimit(int limit) {
        maximum = Math.max(0, limit);
        if (value().length() > maximum) setValue(value());
    }
    @Override public void setValue(String value) { super.setValue(TextEdits.limit(value, maximum)); }
    @Override public void insertText(String text) {
        var selected = getSelected();
        super.insertText(TextEdits.insertion(StringUtil.filterText(text, true), value().length(),
            selected.endIndex() - selected.beginIndex(), maximum));
    }
    @Override public void seekCursor(Whence whence, int position) {
        int target = whence == Whence.ABSOLUTE ? position : whence == Whence.END ? value().length() + position : cursor() + position;
        if (whence == Whence.RELATIVE && Math.abs(position) == 1) {
            if (position < 0 && cursor() > 0 || position > 0 && cursor() < value().length())
                target = value().offsetByCodePoints(cursor(), position);
        }
        target = Math.clamp(target, 0, value().length());
        if (target > 0 && target < value().length() && Character.isLowSurrogate(value().charAt(target))
            && Character.isHighSurrogate(value().charAt(target - 1))) target--;
        super.seekCursor(Whence.ABSOLUTE, target);
    }
    @Override public void deleteText(int length) {
        if (!hasSelection()) {
            setSelecting(true);
            seekCursor(Whence.RELATIVE, length);
            setSelecting(false);
        }
        insertText("");
    }
    int anchor() { var selection = getSelected(); return cursor() == selection.beginIndex() ? selection.endIndex() : selection.beginIndex(); }
    void restoreSelection(int anchor, int cursor) {
        setSelecting(false); seekCursor(Whence.ABSOLUTE, anchor);
        setSelecting(true); seekCursor(Whence.ABSOLUTE, cursor); setSelecting(false);
    }

    void draw(GuiGraphics g, Font font, int x, int y, int width, boolean focused) {
        int lineY = y;
        var selected = getSelected();
        for (var line : iterateLines()) {
            int from = line.beginIndex(), to = line.endIndex();
            if (hasSelection() && selected.beginIndex() <= to && selected.endIndex() > from) {
                int left = font.width(value().substring(from, Math.max(from, selected.beginIndex())));
                int right = selected.endIndex() > to ? width : font.width(value().substring(from, selected.endIndex()));
                g.fill(x + left, lineY - 1, x + right, lineY + 9, 0xFF365E6B);
            }
            g.drawString(font, value().substring(from, to), x, lineY, 0xFFEAF0F7, false);
            if (focused && getLineAtCursor() == (lineY - y) / 9 && (System.nanoTime() / 500_000_000L) % 2 == 0) {
                int caret = x + font.width(value().substring(from, Math.clamp(cursor(), from, to)));
                g.fill(caret, lineY - 1, caret + 1, lineY + 9, 0xFFB8F3E8);
            }
            lineY += 9;
        }
    }
}
