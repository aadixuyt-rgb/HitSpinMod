package com.adixuyt.hitspin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.Map;

public class HitSpinScreen extends Screen {
    // Wirtualny rozmiar panelu - całość jest automatycznie skalowana do okna gry.
    private static final int W = 520, H = 418;
    private static final int WHITE = 0xFFEAF0FF, GREY = 0xFF9AA4B8;

    private final Screen parent;
    private float s = 1f, ox, oy;
    private boolean draggingSlider, inputFocused;
    private String input = "";

    private record Sprite(String[] rows, Map<Character, Integer> pal) {}
    private record Theme(String name, int primary, int secondary, int dark, Sprite sprite) {}

    // ---------- Zwierzaki (pixel art 12x12) ----------
    private static final Sprite FISH = new Sprite(new String[]{
            "............",
            "....oooo..o.",
            "..ooMMMMooao",
            ".oMMMMMMMoaa",
            "oMMwwMMMMMoa",
            "oMMweMMMMMoa",
            "oMMMMMMMMMoa",
            "oLLMMMMMMoaa",
            ".oLLLLLLLoo.",
            "..ooLLLLooao",
            "....oooo..o.",
            "............"},
            Map.of('o', 0xFF1E3A8A, 'M', 0xFF38BDF8, 'L', 0xFF7DD3FC, 'w', 0xFFFFFFFF, 'e', 0xFF0B1220, 'a', 0xFF8B5CF6));

    private static final Sprite FROG = new Sprite(new String[]{
            "............",
            ".oo......oo.",
            "oWWooooooWWo",
            "oWEGGGGGGEWo",
            "oGGGGGGGGGGo",
            "oGGGGGGGGGGo",
            "oGGdGGGGdGGo",
            "oGGGGGGGGGGo",
            "ooGmmmmmmGoo",
            ".oGGGGGGGGo.",
            "..oooooooo..",
            "............"},
            Map.of('o', 0xFF14532D, 'W', 0xFFFFFFFF, 'E', 0xFF0B1220, 'G', 0xFF4ADE80, 'd', 0xFF166534, 'm', 0xFFF87171));

    private static final Sprite DRAGON = new Sprite(new String[]{
            "o..........o",
            "oo........oo",
            ".oooooooooo.",
            "oRRRRRRRRRRo",
            "oRYERRRRYERo",
            "oRRRRRRRRRRo",
            "oRRRRttRRRRo",
            ".oRRtttttRRo",
            ".oRRRRRRRRo.",
            "..oRRRRRRo..",
            "...oooooo...",
            "............"},
            Map.of('o', 0xFF7F1D1D, 'R', 0xFFEF4444, 'Y', 0xFFFDE047, 'E', 0xFF0B1220, 't', 0xFFFDBA74));

    private static final Sprite FOX = new Sprite(new String[]{
            "oo........oo",
            "oOo......oOo",
            "oOOooooooOOo",
            "oOOOOOOOOOOo",
            "oOEOOOOOOEOo",
            "oOOOOOOOOOOo",
            "oWWOOOOOOWWo",
            ".oWWWnnWWWo.",
            "..oWWWWWWo..",
            "...oWWWWo...",
            "....oooo....",
            "............"},
            Map.of('o', 0xFF7C2D12, 'O', 0xFFF97316, 'W', 0xFFFFFFFF, 'E', 0xFF0B1220, 'n', 0xFF0B1220));

    private static final Sprite CAT = new Sprite(new String[]{
            "oo........oo",
            "oPo......oPo",
            "oPPooooooPPo",
            "oPPPPPPPPPPo",
            "oPGEPPPPGEPo",
            "oPPPPPPPPPPo",
            "oPPPPnnPPPPo",
            ".oPPPmmPPPo.",
            "..oPPPPPPo..",
            "...oooooo...",
            "............",
            "............"},
            Map.of('o', 0xFF4C1D95, 'P', 0xFFA78BFA, 'G', 0xFF86EFAC, 'E', 0xFF0B1220, 'n', 0xFFF0ABFC, 'm', 0xFF4C1D95));

    private static final Sprite PANDA = new Sprite(new String[]{
            "BBB......BBB",
            "BBBooooooBBB",
            ".oWWWWWWWWo.",
            "oWWWWWWWWWWo",
            "oWBBWWWWBBWo",
            "oWBEWWWWEBWo",
            "oWWWWnnWWWWo",
            ".oWWWmmWWWo.",
            "..oWWWWWWo..",
            "...oooooo...",
            "............",
            "............"},
            Map.of('W', 0xFFF3F4F6, 'B', 0xFF111827, 'o', 0xFF9CA3AF, 'E', 0xFFFFFFFF, 'n', 0xFF111827, 'm', 0xFF111827));

    private static final Theme[] THEMES = {
            new Theme("Ryba", 0xFF38BDF8, 0xFF8B5CF6, 0xFF0B1730, FISH),
            new Theme("Żaba", 0xFF4ADE80, 0xFFA3E635, 0xFF0A1E12, FROG),
            new Theme("Smok", 0xFFEF4444, 0xFFF97316, 0xFF230C0C, DRAGON),
            new Theme("Lis", 0xFFF59E0B, 0xFFFB7185, 0xFF231406, FOX),
            new Theme("Kot", 0xFFA78BFA, 0xFFF0ABFC, 0xFF170E28, CAT),
            new Theme("Panda", 0xFFE5E7EB, 0xFF94A3B8, 0xFF14171C, PANDA)
    };

    // Kolejność jak w HitSpinClient.Rotation
    private static final String[] ROT_LABELS = {
            "360° obrót", "90° w lewo", "90° w prawo", "90° w górę", "90° w dół",
            "Ukos lewo-góra", "Ukos prawo-góra", "Ukos lewo-dół", "Ukos prawo-dół"};
    private static final String[] ROT_SYMBOLS = {"⟳", "←", "→", "↑", "↓", "↖", "↗", "↙", "↘"};
    private static final int[] ROT_COLORS = {
            0xFFA855F7, 0xFF3B9CFF, 0xFF22C55E, 0xFFF59E0B, 0xFFEC4899,
            0xFF14B8A6, 0xFFF97316, 0xFF6366F1, 0xFFEF4444};

    public HitSpinScreen(Screen parent) {
        super(Text.literal("Hit Spin"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        input = fmt(HitSpinClient.CONFIG.durationSeconds);
        inputFocused = false;
    }

    // ---------- Narzędzia ----------
    private static String fmt(double d) { return String.format(Locale.US, "%.2f", d); }
    private static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
    private Theme theme() { return THEMES[HitSpinClient.CONFIG.theme]; }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void layout() {
        s = Math.min(1.6f, Math.min((width - 8f) / W, (height - 8f) / H));
        if (s < 0.2f) s = 0.2f;
        ox = (width - W * s) / 2f;
        oy = (height - H * s) / 2f;
    }

    private double vx(double mx) { return (mx - ox) / s; }
    private double vy(double my) { return (my - oy) / s; }
    private static boolean in(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }

    private void rr(DrawContext c, int x, int y, int w, int h, int r, int col) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
        c.fill(x, y + r, x + w, y + h - r, col);
    }

    private void rrb(DrawContext c, int x, int y, int w, int h, int r, int border, int fill) {
        rr(c, x, y, w, h, r, border);
        rr(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    private void txt(DrawContext c, String str, int x, int y, int col) {
        c.drawText(textRenderer, str, x, y, col, false);
    }

    private void txtShadow(DrawContext c, String str, int x, int y, int col) {
        c.drawText(textRenderer, str, x, y, col, true);
    }

    private void big(DrawContext c, String str, int x, int y, float sc, int col) {
        var m = c.getMatrices();
        m.push();
        m.translate((float) x, (float) y, 0f);
        m.scale(sc, sc, 1f);
        c.drawText(textRenderer, str, 0, 0, col, true);
        m.pop();
    }

    private void sprite(DrawContext c, Sprite sp, int x, int y, int sc) {
        for (int r = 0; r < sp.rows().length; r++) {
            String row = sp.rows()[r];
            for (int q = 0; q < row.length() && q < 12; q++) {
                Integer col = sp.pal().get(row.charAt(q));
                if (col != null) c.fill(x + q * sc, y + r * sc, x + (q + 1) * sc, y + (r + 1) * sc, col);
            }
        }
    }

    private void check(DrawContext c, int x, int y) {
        int[][] p = {{2, 6}, {3, 7}, {4, 8}, {5, 7}, {6, 6}, {7, 5}, {8, 4}, {9, 3}};
        for (int[] q : p) c.fill(x + q[0], y + q[1], x + q[0] + 1, y + q[1] + 2, 0xFFFFFFFF);
    }

    // ---------- Rysowanie ----------
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        c.fill(0, 0, width, height, 0x77000000);
        layout();
        double vmx = vx(mx), vmy = vy(my);
        Theme t = theme();
        HitSpinClient.Config cfg = HitSpinClient.CONFIG;
        int dark = t.dark();
        int card = mix(dark, 0xFFFFFFFF, 0.07f);
        int cardBorder = mix(dark, t.primary(), 0.35f);
        int header = mix(dark, t.primary(), 0.16f);

        var m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0f);
        m.scale(s, s, 1f);

        // Tło panelu
        rrb(c, 0, 0, W, H, 14, mix(dark, t.primary(), 0.55f), dark);

        // Nagłówek
        rr(c, 6, 6, W - 12, 60, 10, header);
        rr(c, 12, 14, 44, 44, 10, mix(dark, t.primary(), 0.28f));
        sprite(c, t.sprite(), 16, 18, 3);
        big(c, "Hit Spin", 62, 9, 2f, t.primary());
        txt(c, "Mod do efektów po trafieniu", 62, 29, mix(t.primary(), 0xFFFFFFFF, 0.35f));
        txt(c, "by Adixu_YT", 62, 39, t.secondary());

        // Motywy (kwadratowe przyciski, lewy górny róg)
        for (int i = 0; i < THEMES.length; i++) {
            int bx = 62 + i * 18, by = 50;
            if (cfg.theme == i) {
                c.fill(bx - 2, by - 2, bx + 14, by + 14, 0xFFFFFFFF);
            } else {
                c.fill(bx - 1, by - 1, bx + 13, by + 13, 0xFF000000);
            }
            c.fill(bx, by, bx + 12, by + 12, THEMES[i].primary());
        }
        txt(c, "Motyw: " + t.name(), 62 + THEMES.length * 18 + 4, 52, GREY);

        // Przycisk zamknięcia
        boolean hoverClose = in(vmx, vmy, W - 44, 16, 26, 26);
        rr(c, W - 44, 16, 26, 26, 8, hoverClose ? 0xFFB91C1C : 0xFF26314D);
        txtShadow(c, "×", W - 44 + 13 - textRenderer.getWidth("×") / 2, 25, 0xFFFFFFFF);

        // ----- Lewa kolumna -----
        // Karta: włącznik
        rrb(c, 10, 72, 246, 58, 10, cardBorder, card);
        rr(c, 22, 82, 38, 20, 10, cfg.enabled ? t.primary() : 0xFF39445A);
        rr(c, cfg.enabled ? 42 : 24, 84, 16, 16, 8, 0xFFFFFFFF);
        String state = cfg.enabled ? "Włączony" : "Wyłączony";
        big(c, state, 68, 85, 1.3f, cfg.enabled ? t.primary() : GREY);
        if (cfg.enabled) {
            int tw = (int) (textRenderer.getWidth(state) * 1.3f);
            rr(c, 68 + tw + 6, 89, 6, 6, 3, t.primary());
        }
        txt(c, "Po trafieniu gracza kamera", 22, 108, GREY);
        txt(c, "wykona wybrany obrót.", 22, 118, GREY);

        // Karta: czas obrotu
        rrb(c, 10, 136, 246, 116, 10, cardBorder, card);
        txtShadow(c, "Czas obrotu", 22, 145, WHITE);
        txt(c, "od 0,01 s do 4,00 s", 22, 157, GREY);
        txt(c, "Wpisz dokładną wartość:", 22, 175, WHITE);
        rrb(c, 150, 170, 72, 18, 5, inputFocused ? t.primary() : 0xFF3A465F, 0xFF0C1226);
        txt(c, input, 156, 175, 0xFFFFFFFF);
        if (inputFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = 156 + textRenderer.getWidth(input);
            c.fill(cx, 173, cx + 1, 185, t.primary());
        }
        txt(c, "s", 210, 175, GREY);

        int knobX = 24 + (int) ((cfg.durationSeconds - 0.01) / 3.99 * 210);
        rr(c, 24, 208, 210, 6, 3, 0xFF26324C);
        rr(c, 24, 208, Math.max(6, knobX - 24), 6, 3, t.primary());
        rr(c, knobX - 8, 203, 16, 16, 8, 0xFFFFFFFF);
        rr(c, knobX - 5, 206, 10, 10, 5, t.primary());
        txt(c, "0,01 s", 22, 224, GREY);
        String maxLabel = "4,00 s";
        txt(c, maxLabel, 236 - textRenderer.getWidth(maxLabel), 224, GREY);
        txt(c, "Wpisz liczbę albo przeciągnij suwak", 22, 238, mix(GREY, dark, 0.35f));

        // Karta: tryb działania
        rrb(c, 10, 258, 246, 120, 10, cardBorder, card);
        txtShadow(c, "Tryb działania", 22, 267, WHITE);
        modeButton(c, 22, 281, 222, "Losowe z wybranych", cfg.randomMode, t, dark);
        modeButton(c, 22, 312, 222, "Wszystkie po kolei", !cfg.randomMode, t, dark);
        txt(c, "Kilka zaznaczonych? Losowy tryb wybierze", 22, 346, GREY);
        txt(c, "jeden z nich po trafieniu gracza.", 22, 356, GREY);

        // ----- Prawa kolumna -----
        rrb(c, 264, 72, 246, 262, 10, cardBorder, card);
        txtShadow(c, "Rodzaje obrotów", 276, 80, WHITE);
        txt(c, "Zaznacz kilka - mod wybierze losowo", 276, 91, GREY);
        for (int i = 0; i < ROT_LABELS.length; i++) {
            drawRotation(c, 272, 104 + i * 25, 230, i, t, dark);
        }

        rrb(c, 264, 340, 246, 38, 10, cardBorder, card);
        txtShadow(c, "Po obrocie", 276, 346, WHITE);
        txt(c, "↩ Kamera wraca w tym samym czasie", 276, 357, GREY);
        txt(c, "360° nie wraca - to pełny obrót", 276, 367, GREY);

        // ----- Dolny pasek -----
        rrb(c, 10, 384, 500, 28, 10, cardBorder, mix(dark, t.primary(), 0.10f));
        String key = HitSpinClient.OPEN_GUI == null ? "N" : HitSpinClient.OPEN_GUI.getBoundKeyLocalizedText().getString();
        int kw = Math.max(16, textRenderer.getWidth(key) + 10);
        rrb(c, 20, 390, kw, 16, 4, t.primary(), mix(dark, t.primary(), 0.35f));
        txtShadow(c, key, 20 + kw / 2 - textRenderer.getWidth(key) / 2, 394, 0xFFFFFFFF);
        txt(c, "Keybind - otwiera GUI moda (zmiana: Opcje > Sterowanie)", 20 + kw + 8, 394, WHITE);
        sprite(c, t.sprite(), 478, 386, 2);

        m.pop();
    }

    private void modeButton(DrawContext c, int x, int y, int w, String label, boolean selected, Theme t, int dark) {
        rrb(c, x, y, w, 26, 8, selected ? t.primary() : mix(dark, 0xFFFFFFFF, 0.18f),
                selected ? mix(dark, t.primary(), 0.32f) : mix(dark, 0xFFFFFFFF, 0.10f));
        rr(c, x + 8, y + 8, 10, 10, 5, selected ? t.primary() : 0xFF5B6784);
        rr(c, x + 10, y + 10, 6, 6, 3, selected ? 0xFFFFFFFF : mix(dark, 0xFFFFFFFF, 0.10f));
        txtShadow(c, label, x + 26, y + 9, selected ? 0xFFFFFFFF : WHITE);
    }

    private void drawRotation(DrawContext c, int x, int y, int w, int i, Theme t, int dark) {
        boolean sel = HitSpinClient.CONFIG.selected[i];
        int col = ROT_COLORS[i];
        rrb(c, x, y, w, 23, 8, sel ? col : 0xFF283048, sel ? mix(dark, col, 0.20f) : mix(dark, 0xFFFFFFFF, 0.05f));

        rr(c, x + 3, y + 2, 19, 19, 9, sel ? col : 0xFF39445A);
        String sym = ROT_SYMBOLS[i];
        txtShadow(c, sym, x + 3 + 9 - textRenderer.getWidth(sym) / 2, y + 8, 0xFFFFFFFF);
        txtShadow(c, ROT_LABELS[i], x + 30, y + 8, sel ? 0xFFFFFFFF : GREY);

        int px = x + w - 44;
        int cbx = px - 18;
        if (i == 0) {
            // Ikonka: 360° nie wraca (strzałka powrotu przekreślona)
            int bx = x + 92;
            rr(c, bx, y + 5, 72, 13, 6, mix(dark, t.secondary(), 0.30f));
            txt(c, "↩", bx + 4, y + 8, 0xFFFFFFFF);
            for (int k = 0; k < 8; k++) c.fill(bx + 3 + k, y + 16 - k, bx + 4 + k, y + 17 - k, 0xFFEF4444);
            txt(c, "bez powrotu", bx + 14, y + 8, 0xFFFFFFFF);
        }
        rr(c, cbx, y + 6, 12, 12, 3, sel ? col : 0xFF39445A);
        if (sel) check(c, cbx, y + 6);

        rr(c, px, y + 3, 40, 17, 8, mix(dark, col, sel ? 0.45f : 0.15f));
        sprite(c, t.sprite(), px + 14, y + 5, 1);
    }

    // ---------- Obsługa myszy ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        layout();
        double x = vx(mx), y = vy(my);
        HitSpinClient.Config cfg = HitSpinClient.CONFIG;

        boolean hitInput = in(x, y, 150, 170, 72, 18);
        if (!hitInput && inputFocused) commitInput();
        if (hitInput) { inputFocused = true; return true; }

        if (in(x, y, W - 44, 16, 26, 26)) { close(); return true; }
        if (in(x, y, 22, 80, 110, 24)) { cfg.enabled = !cfg.enabled; HitSpinClient.saveConfig(); return true; }
        if (in(x, y, 20, 198, 218, 26)) { draggingSlider = true; setDurationFromMouse(x); return true; }
        if (in(x, y, 22, 281, 222, 26)) { cfg.randomMode = true; HitSpinClient.saveConfig(); return true; }
        if (in(x, y, 22, 312, 222, 26)) { cfg.randomMode = false; HitSpinClient.saveConfig(); return true; }
        for (int i = 0; i < THEMES.length; i++) {
            if (in(x, y, 61 + i * 18, 49, 14, 14)) { cfg.theme = i; HitSpinClient.saveConfig(); return true; }
        }
        for (int i = 0; i < ROT_LABELS.length; i++) {
            if (in(x, y, 272, 104 + i * 25, 230, 23)) {
                cfg.selected[i] = !cfg.selected[i];
                HitSpinClient.saveConfig();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void setDurationFromMouse(double x) {
        double p = clamp((x - 24) / 210.0, 0, 1);
        double v = Math.round((0.01 + p * 3.99) * 100.0) / 100.0;
        HitSpinClient.CONFIG.durationSeconds = clamp(v, 0.01, 4.0);
        input = fmt(HitSpinClient.CONFIG.durationSeconds);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSlider) {
            layout();
            setDurationFromMouse(vx(mx));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingSlider) {
            draggingSlider = false;
            HitSpinClient.saveConfig();
        }
        return super.mouseReleased(mx, my, button);
    }

    // ---------- Wpisywanie czasu ----------
    private void applyInput() {
        String v = input;
        if (v.isEmpty() || v.equals(".")) return;
        try {
            HitSpinClient.CONFIG.durationSeconds = clamp(Double.parseDouble(v), 0.01, 4.0);
        } catch (NumberFormatException ignored) {
        }
    }

    private void commitInput() {
        applyInput();
        input = fmt(HitSpinClient.CONFIG.durationSeconds);
        inputFocused = false;
        HitSpinClient.saveConfig();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inputFocused) {
            char ch = chr == ',' ? '.' : chr;
            if ((ch >= '0' && ch <= '9') || ch == '.') {
                String candidate = input + ch;
                if (candidate.matches("[0-9]?(\\.[0-9]{0,2})?")) {
                    input = candidate;
                    applyInput();
                }
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inputFocused) {
            if (keyCode == 259) { // Backspace
                if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
                applyInput();
                return true;
            }
            if (keyCode == 257 || keyCode == 335 || keyCode == 256) { // Enter / Esc - zatwierdź i wyjdź z pola
                commitInput();
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        HitSpinClient.saveConfig();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
