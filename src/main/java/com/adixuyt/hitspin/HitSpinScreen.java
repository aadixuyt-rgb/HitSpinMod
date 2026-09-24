package com.adixuyt.hitspin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

public class HitSpinScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget durationField;
    private int left, top, panelW, panelH;
    private boolean draggingSlider;

    private static final Theme[] THEMES = {
            new Theme("Ryba", 0xFF38BDF8, 0xFF8B5CF6, 0xFF071B35),
            new Theme("Żaba", 0xFF4ADE80, 0xFF22C55E, 0xFF082014),
            new Theme("Smok", 0xFFEF4444, 0xFFF97316, 0xFF260A0A),
            new Theme("Lis", 0xFFF59E0B, 0xFFFB7185, 0xFF251504),
            new Theme("Kot", 0xFFA78BFA, 0xFFF0ABFC, 0xFF180D2B),
            new Theme("Panda", 0xFFE5E7EB, 0xFF94A3B8, 0xFF101317)
    };

    public HitSpinScreen(Screen parent) { super(Text.literal("Hit Spin")); this.parent = parent; }

    @Override protected void init() {
        panelW = Math.min(900, width - 28); panelH = Math.min(570, height - 24);
        left = (width - panelW) / 2; top = (height - panelH) / 2;
        durationField = new TextFieldWidget(textRenderer, left + 214, top + 137, 100, 28, Text.literal("sekundy"));
        durationField.setMaxLength(5);
        durationField.setText(String.format(Locale.US, "%.2f", HitSpinClient.CONFIG.durationSeconds));
        durationField.setTextPredicate(s -> s.matches("[0-9]*([.,][0-9]{0,2})?"));
        durationField.setChangedListener(this::applyDurationText);
        addDrawableChild(durationField);
    }

    private void applyDurationText(String s) {
        try { if (!s.isBlank()) HitSpinClient.CONFIG.durationSeconds = clamp(Double.parseDouble(s.replace(',', '.')), .01, 4.0); }
        catch (Exception ignored) {}
    }

    private static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
    private Theme theme() { return THEMES[HitSpinClient.CONFIG.theme]; }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0x88000000);
        Theme t = theme();
        round(c, left, top, left + panelW, top + panelH, 0xF20B1020, 20);
        round(c, left + 3, top + 3, left + panelW - 3, top + 92, 0xDD121B35, 18);
        c.drawTextWithShadow(textRenderer, "◉  Hit Spin", left + 24, top + 20, t.primary);
        c.drawTextWithShadow(textRenderer, "Mod do efektów po trafieniu", left + 26, top + 47, 0xFFE7EEFF);
        c.drawTextWithShadow(textRenderer, "by Adixu_YT", left + 26, top + 67, t.secondary);
        round(c, left + panelW - 54, top + 18, left + panelW - 22, top + 50, 0xFF26314D, 9);
        c.drawText(textRenderer, "×", left + panelW - 45, top + 23, 0xFFFFFFFF, true);

        int split = left + panelW / 2;
        round(c, left + 14, top + 106, split - 8, top + panelH - 64, 0xAA101A31, 16);
        round(c, split + 6, top + 106, left + panelW - 14, top + panelH - 64, 0xAA101A31, 16);

        // Left controls
        c.drawTextWithShadow(textRenderer, "EFEKT", left + 30, top + 124, 0xFFFFFFFF);
        drawToggle(c, left + 30, top + 146, HitSpinClient.CONFIG.enabled, t);
        c.drawText(textRenderer, HitSpinClient.CONFIG.enabled ? "Włączony" : "Wyłączony", left + 78, top + 154, HitSpinClient.CONFIG.enabled ? t.primary : 0xFF9AA4B8, true);
        c.drawText(textRenderer, "Po trafieniu w gracza", left + 30, top + 182, 0xFF9AA4B8, false);

        c.drawTextWithShadow(textRenderer, "CZAS OBROTU", left + 30, top + 211, 0xFFFFFFFF);
        c.drawText(textRenderer, "0,01 s – 4,00 s", left + 30, top + 232, 0xFF9AA4B8, false);
        int sx = left + 30, sy = top + 258, sw = split - left - 60;
        round(c, sx, sy, sx + sw, sy + 7, 0xFF26324C, 4);
        int knobX = sx + (int)((HitSpinClient.CONFIG.durationSeconds - .01) / 3.99 * sw);
        round(c, sx, sy, knobX, sy + 7, t.primary, 4);
        c.fill(knobX - 6, sy - 5, knobX + 6, sy + 17, t.secondary);
        c.drawText(textRenderer, String.format(Locale.US, "%.2f s", HitSpinClient.CONFIG.durationSeconds), sx + sw - 58, sy + 19, 0xFFFFFFFF, false);
        c.drawText(textRenderer, "Wpisz dokładną wartość:", left + 30, top + 286, 0xFFD9E2F2, false);
        c.drawText(textRenderer, "np. 0.35", left + 30, top + 304, 0xFF7F8BA3, false);

        drawMode(c, left + 30, top + 342, split - left - 60, "Losowe z wybranych", HitSpinClient.CONFIG.randomMode, t);
        drawMode(c, left + 30, top + 381, split - left - 60, "Wszystkie po kolei", !HitSpinClient.CONFIG.randomMode, t);

        c.drawTextWithShadow(textRenderer, "MOTYW", left + 30, top + 430, 0xFFFFFFFF);
        int tx = left + 30;
        for (int i = 0; i < THEMES.length; i++) {
            Theme th = THEMES[i];
            int bx = tx + i * 44;
            round(c, bx, top + 452, bx + 34, top + 486, th.primary, 7);
            if (HitSpinClient.CONFIG.theme == i) {
                c.fill(bx + 4, top + 456, bx + 30, top + 460, 0xFFFFFFFF);
                c.drawText(textRenderer, "✓", bx + 10, top + 464, 0xFFFFFFFF, true);
            }
        }

        // Right rotations
        c.drawTextWithShadow(textRenderer, "RODZAJE OBROTÓW", split + 24, top + 124, 0xFFFFFFFF);
        c.drawText(textRenderer, "Zaznacz kilka → mod wybierze losowo", split + 24, top + 145, 0xFF9AA4B8, false);
        HitSpinClient.Rotation[] rs = HitSpinClient.Rotation.values();
        for (int i = 0; i < rs.length; i++) drawRotation(c, split + 24, top + 163 + i * 34, panelW / 2 - 56, rs[i], i, t);

        c.drawTextWithShadow(textRenderer, "PO OBROCIE", split + 24, top + 489, 0xFFFFFFFF);
        c.drawText(textRenderer, "↩  Wszystkie oprócz 360° wracają", split + 24, top + 512, 0xFFB8C4D9, false);
        c.drawText(textRenderer, "     w tym samym czasie, co obrót.", split + 24, top + 528, 0xFFB8C4D9, false);

        round(c, left + 14, top + panelH - 52, left + panelW - 14, top + panelH - 14, 0xCC121B35, 12);
        c.drawText(textRenderer, "N", left + 30, top + panelH - 43, t.primary, true);
        c.drawText(textRenderer, "Keybind — zmienisz w Opcje → Sterowanie → Klawisze", left + 60, top + panelH - 41, 0xFFE7EEFF, false);
        c.drawText(textRenderer, "Motyw: " + t.name, left + panelW - 120, top + panelH - 41, t.secondary, false);
        super.render(c, mx, my, delta);
    }

    private void drawToggle(DrawContext c, int x, int y, boolean on, Theme t) {
        round(c, x, y, x + 40, y + 22, on ? t.primary : 0xFF39445A, 11);
        c.fill(on ? x + 21 : x + 3, y + 3, on ? x + 37 : x + 19, y + 19, 0xFFFFFFFF);
    }

    private void drawMode(DrawContext c, int x, int y, int w, String label, boolean selected, Theme t) {
        round(c, x, y, x + w, y + 32, selected ? t.primary : 0xFF26324C, 9);
        c.drawText(textRenderer, (selected ? "✓  " : "○  ") + label, x + 10, y + 10, 0xFFFFFFFF, selected);
    }

    private void drawRotation(DrawContext c, int x, int y, int w, HitSpinClient.Rotation r, int index, Theme t) {
        boolean selected = HitSpinClient.CONFIG.selected[index];
        round(c, x, y, x + w, y + 28, selected ? 0xCC1D2C52 : 0x88202A40, 8);
        c.fill(x, y, x + 4, y + 28, selected ? t.primary : 0xFF3A465F);
        c.drawText(textRenderer, symbol(r), x + 12, y + 8, selected ? t.secondary : 0xFF9BA7BA, false);
        c.drawText(textRenderer, r.label, x + 42, y + 8, 0xFFEAF0FF, false);
        c.drawText(textRenderer, selected ? "✓" : "□", x + w - 24, y + 7, selected ? t.primary : 0xFF76829A, true);
        if (r == HitSpinClient.Rotation.FULL_360) c.drawText(textRenderer, "nie wraca — pełny obrót", x + w - 145, y - 11, t.secondary, false);
    }

    private String symbol(HitSpinClient.Rotation r) {
        return switch (r) {
            case FULL_360 -> "⟳"; case LEFT -> "←"; case RIGHT -> "→"; case UP -> "↑"; case DOWN -> "↓";
            case UP_LEFT -> "↖"; case UP_RIGHT -> "↗"; case DOWN_LEFT -> "↙"; case DOWN_RIGHT -> "↘";
        };
    }

    private static void round(DrawContext c, int x1, int y1, int x2, int y2, int color, int r) {
        c.fill(x1 + r, y1, x2 - r, y2, color); c.fill(x1, y1 + r, x2, y2 - r, color);
        c.fill(x1 + 2, y1 + 2, x1 + r, y1 + r, color); c.fill(x2 - r, y1 + 2, x2 - 2, y1 + r, color);
        c.fill(x1 + 2, y2 - r, x1 + r, y2 - 2, color); c.fill(x2 - r, y2 - r, x2 - 2, y2 - 2, color);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (mx >= left + 30 && mx <= left + 70 && my >= top + 146 && my <= top + 170) {
            HitSpinClient.CONFIG.enabled = !HitSpinClient.CONFIG.enabled; HitSpinClient.saveConfig(); return true;
        }
        int split = left + panelW / 2;
        int sx = left + 30, sw = split - left - 60, sy = top + 258;
        if (mx >= sx && mx <= sx + sw && my >= sy - 8 && my <= sy + 20) { draggingSlider = true; setDurationFromMouse(mx, sx, sw); return true; }
        if (mx >= left + 30 && mx <= split - 20 && my >= top + 342 && my <= top + 374) { HitSpinClient.CONFIG.randomMode = true; HitSpinClient.saveConfig(); return true; }
        if (mx >= left + 30 && mx <= split - 20 && my >= top + 381 && my <= top + 413) { HitSpinClient.CONFIG.randomMode = false; HitSpinClient.saveConfig(); return true; }
        for (int i = 0; i < THEMES.length; i++) {
            int bx = left + 30 + i * 44;
            if (mx >= bx && mx <= bx + 34 && my >= top + 452 && my <= top + 486) { HitSpinClient.CONFIG.theme = i; HitSpinClient.saveConfig(); return true; }
        }
        HitSpinClient.Rotation[] rs = HitSpinClient.Rotation.values();
        for (int i = 0; i < rs.length; i++) {
            int y = top + 163 + i * 34;
            if (mx >= split + 24 && mx <= left + panelW - 30 && my >= y && my <= y + 28) {
                HitSpinClient.CONFIG.selected[i] = !HitSpinClient.CONFIG.selected[i]; HitSpinClient.saveConfig(); return true;
            }
        }
        if (mx >= left + panelW - 54 && my >= top + 18 && my <= top + 55) { close(); return true; }
        return super.mouseClicked(mx, my, button);
    }

    private void setDurationFromMouse(double mx, int sx, int sw) {
        double p = clamp((mx - sx) / sw, 0, 1);
        HitSpinClient.CONFIG.durationSeconds = .01 + p * 3.99;
        durationField.setText(String.format(Locale.US, "%.2f", HitSpinClient.CONFIG.durationSeconds));
        HitSpinClient.saveConfig();
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSlider) { setDurationFromMouse(mx, left + 30, panelW / 2 - 60); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) { draggingSlider = false; return super.mouseReleased(mx, my, button); }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { HitSpinClient.saveConfig(); MinecraftClient.getInstance().setScreen(parent); }

    private record Theme(String name, int primary, int secondary, int dark) {}
}
