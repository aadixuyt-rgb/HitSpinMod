package com.adixuyt.hitspin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HitSpinScreen extends Screen {
    private static final int WHITE = 0xFFEAF0FF, GREY = 0xFF9AA4B8;

    private final Screen parent;
    private float s = 1f, ox, oy;
    private int W = 580, H = 472;

    private enum Field { NONE, DURATION, SEARCH }
    private Field focused = Field.NONE;
    private String durationInput = "";
    private String searchQuery = "";
    private boolean draggingDuration;
    private int draggingRotation = -1;
    private boolean autostackingExpanded;
    private int scrollOffset;

    private String cachedQuery = null;
    private List<Item> cachedResults = new ArrayList<>();
    private List<Item> allItemsSorted;

    private record Theme(String name, int primary, int secondary, int dark) {}

    private static final Theme[] THEMES = {
            new Theme("Niebieski", 0xFF38BDF8, 0xFF8B5CF6, 0xFF0B1730),
            new Theme("Zielony", 0xFF4ADE80, 0xFFA3E635, 0xFF0A1E12),
            new Theme("Czerwony", 0xFFEF4444, 0xFFF97316, 0xFF230C0C),
            new Theme("Pomarańczowy", 0xFFF59E0B, 0xFFFB7185, 0xFF231406),
            new Theme("Fioletowy", 0xFFA78BFA, 0xFFF0ABFC, 0xFF170E28),
            new Theme("Biały", 0xFFE5E7EB, 0xFF94A3B8, 0xFF14171C)
    };

    private static final String[] ROT_LABELS = {
            "360°", "Lewo", "Prawo", "Góra", "Dół", "Skos G-L", "Skos G-P", "Skos D-L", "Skos D-P"};
    private static final String[] ROT_SYMBOLS = {"⟳", "←", "→", "↑", "↓", "↖", "↗", "↙", "↘"};
    private static final int[] ROT_COLORS = {
            0xFFA855F7, 0xFF3B9CFF, 0xFF22C55E, 0xFFF59E0B, 0xFFEC4899,
            0xFF14B8A6, 0xFFF97316, 0xFF6366F1, 0xFFEF4444};

    // Geometria siatki rodzajów obrotów (wirtualne jednostki).
    private static final int GRID_X = 244, GRID_Y = 106, CELL_W = 104, CELL_H = 74, GAP = 6, COLS = 3;

    public HitSpinScreen(Screen parent) {
        super(Text.literal("Hit Spin"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        durationInput = fmt(HitSpinClient.CONFIG.durationSeconds);
        focused = Field.NONE;
    }

    // ---------- Narzędzia ----------
    private static String fmt(double d) { return String.format(Locale.US, "%.2f", d); }
    private static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
    private static int clampI(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }
    private Theme theme() { return THEMES[HitSpinClient.CONFIG.theme]; }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void layout() {
        H = 472 + (autostackingExpanded ? 112 : 0);
        s = Math.min(1.5f, Math.min((width - 8f) / W, (height - 8f) / H));
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

    private void circle(DrawContext c, int cx, int cy, int radius, int col) {
        rr(c, cx - radius, cy - radius, radius * 2, radius * 2, radius, col);
    }

    private void circleRing(DrawContext c, int cx, int cy, int radius, int ring, int fill) {
        circle(c, cx, cy, radius, ring);
        circle(c, cx, cy, radius - 2, fill);
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

    private void check(DrawContext c, int x, int y) {
        int[][] p = {{-3, 0}, {-2, 1}, {-1, 2}, {0, 1}, {1, 0}, {2, -1}, {3, -2}};
        for (int[] q : p) c.fill(x + q[0], y + q[1], x + q[0] + 1, y + q[1] + 1, 0xFFFFFFFF);
    }

    private int cellX(int i) { return GRID_X + (i % COLS) * (CELL_W + GAP); }
    private int cellY(int i) { return GRID_Y + (i / COLS) * (CELL_H + GAP); }

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

        rrb(c, 0, 0, W, H, 22, mix(dark, t.primary(), 0.55f), dark);

        // Nagłówek
        rr(c, 6, 6, W - 12, 60, 18, header);
        circle(c, 36, 36, 22, mix(dark, t.primary(), 0.30f));
        txtShadow(c, "⟳", 36 - textRenderer.getWidth("⟳") / 2, 30, t.primary());
        big(c, "Hit Spin", 66, 9, 2f, t.primary());
        txt(c, "by Adixu_YT", 66, 29, mix(t.primary(), 0xFFFFFFFF, 0.35f));

        for (int i = 0; i < THEMES.length; i++) {
            int bx = 66 + i * 20, by = 41;
            boolean on = cfg.theme == i;
            circleRing(c, bx + 6, by + 6, on ? 9 : 7, on ? 0xFFFFFFFF : 0xFF000000, THEMES[i].primary());
        }

        boolean hoverClose = in(vmx, vmy, W - 42, 14, 26, 26);
        circle(c, W - 29, 27, 13, hoverClose ? 0xFFB91C1C : 0xFF26314D);
        txtShadow(c, "×", W - 29 - textRenderer.getWidth("×") / 2, 21, 0xFFFFFFFF);

        // ----- Lewa kolumna -----
        rrb(c, 10, 72, 224, 54, 16, cardBorder, card);
        int togW = 40, togH = 20;
        rr(c, 22, 89, togW, togH, togH / 2, cfg.enabled ? t.primary() : 0xFF39445A);
        circle(c, cfg.enabled ? 22 + togW - 10 : 22 + 10, 89 + togH / 2, 8, 0xFFFFFFFF);
        String state = cfg.enabled ? "Włączony" : "Wyłączony";
        big(c, state, 70, 90, 1.15f, cfg.enabled ? t.primary() : GREY);
        txt(c, "Kamera obraca się po trafieniu gracza", 22, 112, GREY);

        rrb(c, 10, 132, 224, 112, 16, cardBorder, card);
        txtShadow(c, "Czas obrotu", 22, 141, WHITE);
        txt(c, "od 0,01 s do 2,00 s", 22, 153, GREY);
        rrb(c, 128, 148, 66, 18, 9, focused == Field.DURATION ? t.primary() : 0xFF3A465F, 0xFF0C1226);
        txt(c, durationInput, 137, 153, 0xFFFFFFFF);
        if (focused == Field.DURATION && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = 137 + textRenderer.getWidth(durationInput);
            c.fill(cx, 151, cx + 1, 163, t.primary());
        }
        txt(c, "s", 198, 153, GREY);

        int durKnobX = 24 + (int) ((cfg.durationSeconds - 0.01) / 1.99 * 200);
        rr(c, 24, 186, 200, 6, 3, 0xFF26324C);
        rr(c, 24, 186, Math.max(6, durKnobX - 24), 6, 3, t.primary());
        circle(c, durKnobX, 189, 8, 0xFFFFFFFF);
        circle(c, durKnobX, 189, 5, t.primary());
        txt(c, "0,01 s", 22, 202, GREY);
        String maxLabel = "2,00 s";
        txt(c, maxLabel, 226 - textRenderer.getWidth(maxLabel), 202, GREY);
        txt(c, "Wpisz liczbę albo przeciągnij suwak", 22, 216, mix(GREY, dark, 0.35f));

        rrb(c, 10, 254, 224, 96, 16, cardBorder, card);
        txtShadow(c, "Tryb działania", 22, 263, WHITE);
        modeButton(c, 22, 277, 200, "Losowe z wybranych", cfg.randomMode, t, dark);
        modeButton(c, 22, 306, 200, "Wszystkie po kolei", !cfg.randomMode, t, dark);
        txt(c, "Kilka zaznaczonych obrotów?", 22, 336, GREY);
        txt(c, "Losowy tryb wybierze jeden z nich.", 22, 344, GREY);

        // ----- Prawa kolumna -----
        int gridBottom = GRID_Y + 3 * (CELL_H + GAP) - GAP;
        rrb(c, 244, 72, 326, gridBottom - 72 + 10, 16, cardBorder, card);
        txtShadow(c, "Rodzaje obrotów", 256, 80, WHITE);
        txt(c, "Zaznacz kilka i ustaw kąt suwakiem", 256, 91, GREY);
        for (int i = 0; i < ROT_LABELS.length; i++) drawRotationCell(c, i, t, dark);

        int stackY = gridBottom + 18;
        int stackHeaderH = 40, expandRowH = 20;
        int stackCardH = stackHeaderH + expandRowH + 12 + (autostackingExpanded ? 112 : 0);
        rrb(c, 244, stackY, 326, stackCardH, 16, cardBorder, card);
        txtShadow(c, "Autostacking", 256, stackY + 10, WHITE);
        txt(c, "Auto-dopełnianie hotbara i off-handu", 256, stackY + 22, GREY);
        int hbTogW = 34, hbTogH = 18;
        int hbTogX = 244 + 326 - 12 - hbTogW, hbTogY = stackY + 9;
        boolean hb = cfg.hotbarRefillEnabled;
        rr(c, hbTogX, hbTogY, hbTogW, hbTogH, hbTogH / 2, hb ? t.primary() : 0xFF39445A);
        circle(c, hb ? hbTogX + hbTogW - 9 : hbTogX + 9, hbTogY + hbTogH / 2, 7, 0xFFFFFFFF);

        int expY = stackY + stackHeaderH;
        String arrow = autostackingExpanded ? "▾" : "▸";
        txtShadow(c, arrow + " Dodatkowe bloki", 256, expY + 6, t.secondary());
        String countStr = cfg.extraRefillItems.size() + " wybrane";
        txt(c, countStr, 244 + 326 - 12 - textRenderer.getWidth(countStr), expY + 6, GREY);

        if (autostackingExpanded) {
            int searchY = expY + expandRowH + 4;
            rrb(c, 256, searchY, 302, 18, 9, focused == Field.SEARCH ? t.primary() : 0xFF3A465F, 0xFF0C1226);
            drawMagnifier(c, 264, searchY + 9);
            String shown = searchQuery.isEmpty() ? "Przewiń lub wpisz nazwę..." : searchQuery;
            txt(c, shown, 276, searchY + 5, searchQuery.isEmpty() ? GREY : 0xFFFFFFFF);
            if (focused == Field.SEARCH && (System.currentTimeMillis() / 500) % 2 == 0 && !searchQuery.isEmpty()) {
                int cx = 276 + textRenderer.getWidth(searchQuery);
                c.fill(cx, searchY + 3, cx + 1, searchY + 15, t.primary());
            }

            int listY = searchY + 22, listH = 88;
            rr(c, 256, listY, 302, listH, 8, mix(dark, 0xFFFFFFFF, 0.04f));
            List<Item> results = computeResults();
            int rowH = 18;
            int visible = listH / rowH;
            scrollOffset = clampI(scrollOffset, 0, Math.max(0, results.size() - visible));
            if (results.isEmpty()) {
                txt(c, "Brak wyników", 262, listY + 6, GREY);
            } else {
                for (int row = 0; row < visible && scrollOffset + row < results.size(); row++) {
                    Item item = results.get(scrollOffset + row);
                    int ry = listY + row * rowH;
                    Identifier id = Registries.ITEM.getId(item);
                    boolean sel = id != null && cfg.extraRefillItems.contains(id.toString());
                    if (sel) c.fill(258, ry, 556, ry + rowH, mix(dark, t.primary(), 0.18f));
                    c.drawItem(new ItemStack(item), 262, ry + 1);
                    circleRing(c, 302, ry + 9, 6, sel ? t.primary() : 0xFF39445A, sel ? mix(dark, t.primary(), 0.4f) : mix(dark, 0xFFFFFFFF, 0.06f));
                    if (sel) check(c, 302, ry + 9);
                    String name = item.getName().getString();
                    txt(c, name, 316, ry + 5, WHITE);
                }
            }
        }

        m.pop();
    }

    private void drawMagnifier(DrawContext c, int cx, int cy) {
        circleRing(c, cx, cy - 1, 4, GREY, 0x00000000);
        for (int k = 0; k < 3; k++) c.fill(cx + 2 + k, cy + 2 + k, cx + 3 + k, cy + 3 + k, GREY);
    }

    private List<Item> computeResults() {
        if (allItemsSorted == null) {
            List<Item> all = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds()) all.add(Registries.ITEM.get(id));
            all.sort(java.util.Comparator.comparing(i -> i.getName().getString().toLowerCase(Locale.ROOT)));
            allItemsSorted = all;
        }
        if (cachedQuery != null && cachedQuery.equals(searchQuery)) return cachedResults;
        cachedQuery = searchQuery;
        String q = searchQuery.toLowerCase(Locale.ROOT).trim();
        List<Item> out = new ArrayList<>();
        for (Item item : allItemsSorted) {
            if (q.isEmpty()) { out.add(item); continue; }
            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            Identifier id = Registries.ITEM.getId(item);
            String path = id == null ? "" : id.getPath().replace('_', ' ');
            if (name.contains(q) || path.contains(q)) out.add(item);
        }
        cachedResults = out;
        scrollOffset = 0;
        return out;
    }

    private void modeButton(DrawContext c, int x, int y, int w, String label, boolean selected, Theme t, int dark) {
        rrb(c, x, y, w, 24, 12, selected ? t.primary() : mix(dark, 0xFFFFFFFF, 0.18f),
                selected ? mix(dark, t.primary(), 0.32f) : mix(dark, 0xFFFFFFFF, 0.10f));
        circleRing(c, x + 13, y + 12, 6, selected ? t.primary() : 0xFF5B6784, selected ? mix(dark, t.primary(), 0.32f) : mix(dark, 0xFFFFFFFF, 0.10f));
        if (selected) circle(c, x + 13, y + 12, 3, 0xFFFFFFFF);
        txtShadow(c, label, x + 26, y + 8, selected ? 0xFFFFFFFF : WHITE);
    }

    private void drawRotationCell(DrawContext c, int i, Theme t, int dark) {
        int x = cellX(i), y = cellY(i);
        HitSpinClient.Config cfg = HitSpinClient.CONFIG;
        boolean sel = cfg.selected[i];
        int col = ROT_COLORS[i];
        rrb(c, x, y, CELL_W, CELL_H, 14, sel ? col : 0xFF283048, sel ? mix(dark, col, 0.20f) : mix(dark, 0xFFFFFFFF, 0.05f));

        circle(c, x + 15, y + 15, 11, sel ? col : 0xFF39445A);
        String sym = ROT_SYMBOLS[i];
        txtShadow(c, sym, x + 15 - textRenderer.getWidth(sym) / 2, y + 10, 0xFFFFFFFF);
        txt(c, ROT_LABELS[i], x + 30, y + 6, sel ? 0xFFFFFFFF : GREY);

        circleRing(c, x + CELL_W - 12, y + 12, 7, sel ? col : 0xFF39445A, sel ? mix(dark, col, 0.4f) : mix(dark, 0xFFFFFFFF, 0.06f));
        if (sel) check(c, x + CELL_W - 12, y + 12);

        int max = HitSpinClient.maxDegrees(i);
        int deg = cfg.degrees[i];
        String degStr = deg + "°";
        big(c, degStr, x + CELL_W / 2 - (int) (textRenderer.getWidth(degStr) * 0.6f), y + 30, 1.2f, sel ? col : GREY);

        int sx = x + 10, sy = y + 58, sw = CELL_W - 20;
        int knobX = sx + (int) ((deg - 1) / (float) Math.max(1, max - 1) * sw);
        rr(c, sx, sy, sw, 5, 2, 0xFF26324C);
        rr(c, sx, sy, Math.max(5, knobX - sx), 5, 2, sel ? col : 0xFF5B6784);
        circle(c, knobX, sy + 2, 6, 0xFFFFFFFF);
        circle(c, knobX, sy + 2, 4, sel ? col : 0xFF5B6784);
    }

    // ---------- Obsługa myszy ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        layout();
        double x = vx(mx), y = vy(my);
        HitSpinClient.Config cfg = HitSpinClient.CONFIG;

        boolean hitDuration = in(x, y, 128, 148, 66, 18);
        boolean hitSearch = autostackingExpanded && in(x, y, 256, searchBoxY(), 302, 18);
        if (!hitDuration && focused == Field.DURATION) commitDuration();
        if (!hitSearch && focused == Field.SEARCH) focused = Field.NONE;
        if (hitDuration) { focused = Field.DURATION; return true; }
        if (hitSearch) { focused = Field.SEARCH; return true; }

        if (in(x, y, W - 42, 14, 26, 26)) { close(); return true; }
        if (in(x, y, 22, 89, 110, 20)) { cfg.enabled = !cfg.enabled; HitSpinClient.saveConfig(); return true; }
        if (in(x, y, 24, 178, 200, 22)) { draggingDuration = true; setDurationFromMouse(x); return true; }
        if (in(x, y, 22, 277, 200, 24)) { cfg.randomMode = true; HitSpinClient.saveConfig(); return true; }
        if (in(x, y, 22, 306, 200, 24)) { cfg.randomMode = false; HitSpinClient.saveConfig(); return true; }
        for (int i = 0; i < THEMES.length; i++) {
            if (in(x, y, 66 + i * 20, 41, 12, 12)) { cfg.theme = i; HitSpinClient.saveConfig(); return true; }
        }

        for (int i = 0; i < ROT_LABELS.length; i++) {
            int cx = cellX(i), cy = cellY(i);
            if (in(x, y, cx + 10, cy + 52, CELL_W - 20, 16)) { draggingRotation = i; setDegreeFromMouse(i, x); return true; }
            if (in(x, y, cx, cy, CELL_W, CELL_H)) { cfg.selected[i] = !cfg.selected[i]; HitSpinClient.saveConfig(); return true; }
        }

        int gridBottom = GRID_Y + 3 * (CELL_H + GAP) - GAP;
        int stackY = gridBottom + 18;
        if (in(x, y, 244 + 326 - 12 - 34, stackY + 9, 34, 18)) { cfg.hotbarRefillEnabled = !cfg.hotbarRefillEnabled; HitSpinClient.saveConfig(); return true; }
        if (in(x, y, 256, stackY + 40, 300, 20)) {
            autostackingExpanded = !autostackingExpanded;
            focused = autostackingExpanded ? Field.SEARCH : Field.NONE;
            return true;
        }

        if (autostackingExpanded) {
            int listY = searchBoxY() + 22, listH = 88, rowH = 18;
            if (in(x, y, 256, listY, 302, listH)) {
                int row = (int) ((y - listY) / rowH);
                List<Item> results = computeResults();
                int idx = scrollOffset + row;
                if (idx >= 0 && idx < results.size()) {
                    Item item = results.get(idx);
                    Identifier id = Registries.ITEM.getId(item);
                    if (id != null) {
                        String key = id.toString();
                        if (!cfg.extraRefillItems.remove(key)) cfg.extraRefillItems.add(key);
                        HitSpinClient.saveConfig();
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private int searchBoxY() {
        int gridBottom = GRID_Y + 3 * (CELL_H + GAP) - GAP;
        int stackY = gridBottom + 18;
        return stackY + 40 + 20 + 4;
    }

    private void setDurationFromMouse(double x) {
        double p = clamp((x - 24) / 200.0, 0, 1);
        double v = Math.round((0.01 + p * 1.99) * 100.0) / 100.0;
        HitSpinClient.CONFIG.durationSeconds = clamp(v, 0.01, 2.0);
        durationInput = fmt(HitSpinClient.CONFIG.durationSeconds);
    }

    private void setDegreeFromMouse(int i, double x) {
        int cx = cellX(i);
        int sx = cx + 10, sw = CELL_W - 20;
        double p = clamp((x - sx) / (double) sw, 0, 1);
        int max = HitSpinClient.maxDegrees(i);
        int deg = clampI((int) Math.round(1 + p * (max - 1)), 1, max);
        HitSpinClient.CONFIG.degrees[i] = deg;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        layout();
        if (draggingDuration) { setDurationFromMouse(vx(mx)); return true; }
        if (draggingRotation >= 0) { setDegreeFromMouse(draggingRotation, vx(mx)); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingDuration) { draggingDuration = false; HitSpinClient.saveConfig(); }
        if (draggingRotation >= 0) { draggingRotation = -1; HitSpinClient.saveConfig(); }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (autostackingExpanded) {
            layout();
            double x = vx(mx), y = vy(my);
            int listY = searchBoxY() + 22, listH = 88;
            if (in(x, y, 256, listY, 302, listH)) {
                scrollOffset = clampI(scrollOffset - (int) Math.signum(vertical) * 3, 0, Math.max(0, computeResults().size() - listH / 18));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    // ---------- Wpisywanie tekstu ----------
    private void applyDurationInput() {
        if (durationInput.isEmpty() || durationInput.equals(".")) return;
        try {
            HitSpinClient.CONFIG.durationSeconds = clamp(Double.parseDouble(durationInput), 0.01, 2.0);
        } catch (NumberFormatException ignored) {}
    }

    private void commitDuration() {
        applyDurationInput();
        durationInput = fmt(HitSpinClient.CONFIG.durationSeconds);
        focused = Field.NONE;
        HitSpinClient.saveConfig();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focused == Field.DURATION) {
            char ch = chr == ',' ? '.' : chr;
            if ((ch >= '0' && ch <= '9') || ch == '.') {
                String candidate = durationInput + ch;
                if (candidate.matches("[0-9]?(\\.[0-9]{0,2})?")) {
                    durationInput = candidate;
                    applyDurationInput();
                }
            }
            return true;
        }
        if (focused == Field.SEARCH) {
            if (searchQuery.length() < 40 && chr >= 32) searchQuery += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focused == Field.DURATION) {
            if (keyCode == 259) { if (!durationInput.isEmpty()) durationInput = durationInput.substring(0, durationInput.length() - 1); applyDurationInput(); return true; }
            if (keyCode == 257 || keyCode == 335 || keyCode == 256) { commitDuration(); return true; }
            return true;
        }
        if (focused == Field.SEARCH) {
            if (keyCode == 259) { if (!searchQuery.isEmpty()) searchQuery = searchQuery.substring(0, searchQuery.length() - 1); return true; }
            if (keyCode == 257 || keyCode == 335 || keyCode == 256) { focused = Field.NONE; return true; }
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
