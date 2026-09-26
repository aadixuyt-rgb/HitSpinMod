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

// Uwaga: obrót kamery po trafieniu ("Hit Spin") został całkowicie usunięty z moda,
// bo jest niedozwolony na serwerze autora. To GUI zawiera już tylko moduł Autostacking.
public class HitSpinScreen extends Screen {
    private static final int WHITE = 0xFFEAF0FF, GREY = 0xFF9AA4B8;
    private static final int PAD = 20, CW = 520;

    private final Screen parent;
    private float s = 1f, ox, oy;
    private int W = CW + PAD * 2;

    private enum Field { NONE, SEARCH }
    private Field focused = Field.NONE;
    private String searchQuery = "";
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

    public HitSpinScreen(Screen parent) {
        super(Text.literal("Hit Spin"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        focused = Field.NONE;
    }

    // ---------- Narzędzia ----------
    private static int clampI(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }
    private Theme theme() { return THEMES[HitSpinClient.CONFIG.theme]; }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ---------- Geometria ----------
    private record Geo(int emptyY, int emptyH, int autoY, int autoHeaderH, int expandY, int searchY, int listY, int listH, int footerY, int totalH) {}

    private Geo geo() {
        int y = 10;
        y += 76 + 14;               // nagłówek

        // Tu wcześniej były ustawienia obrotu kamery (Hit Spin) - celowo puste miejsce.
        int emptyY = y, emptyH = 40;
        y += emptyH + 14;

        int autoY = y;
        int autoHeaderH = 40, expandY = autoY + autoHeaderH;
        y += autoHeaderH + 22;
        int searchY = expandY + 22, listY = searchY + 24, listH = 130;
        if (autostackingExpanded) y += 24 + listH;
        y += 14;
        int footerY = y; y += 30;
        int totalH = y + 10;
        return new Geo(emptyY, emptyH, autoY, autoHeaderH, expandY, searchY, listY, listH, footerY, totalH);
    }

    private void layout() {
        int H = geo().totalH();
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

    private void vgrad(DrawContext c, int x, int y, int w, int h, int r, int top, int bottom) {
        rr(c, x, y, w, h, r, top);
        for (int i = 0; i < h; i++) {
            float t = i / (float) Math.max(1, h - 1);
            int col = mix(top, bottom, t);
            int inset = i < r ? r - (int) Math.round(Math.sqrt(r * r - Math.pow(r - i - 0.5, 2))) : (i > h - r ? r - (int) Math.round(Math.sqrt(r * r - Math.pow(r - (h - i) - 0.5, 2))) : 0);
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
        }
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

    // ---------- Rysowanie ----------
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        c.fill(0, 0, width, height, 0x77000000);
        layout();
        Geo g = geo();
        double vmx = vx(mx), vmy = vy(my);
        Theme t = theme();
        HitSpinClient.Config cfg = HitSpinClient.CONFIG;
        int dark = t.dark();
        int card = mix(dark, 0xFFFFFFFF, 0.06f);
        int cardBorder = mix(dark, t.primary(), 0.30f);

        var m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0f);
        m.scale(s, s, 1f);

        rrb(c, 0, 0, W, g.totalH(), 26, mix(dark, t.primary(), 0.5f), dark);

        // Nagłówek
        vgrad(c, 10, 10, W - 20, 76, 22, mix(dark, t.primary(), 0.30f), mix(dark, t.primary(), 0.08f));
        circle(c, 46, 48, 22, mix(dark, t.primary(), 0.35f));
        txtShadow(c, "⚙", 46 - textRenderer.getWidth("⚙") / 2, 42, t.primary());
        big(c, "Hit Spin", 78, 20, 2f, t.primary());
        txt(c, "by Adixu_YT", 78, 40, mix(t.primary(), 0xFFFFFFFF, 0.35f));
        for (int i = 0; i < THEMES.length; i++) {
            int bx = 78 + i * 20, by = 55;
            boolean on = cfg.theme == i;
            circleRing(c, bx + 6, by + 6, on ? 9 : 7, on ? 0xFFFFFFFF : 0x55000000, THEMES[i].primary());
        }
        boolean hoverClose = in(vmx, vmy, W - 46, 20, 26, 26);
        circle(c, W - 33, 33, 13, hoverClose ? 0xFFB91C1C : 0x33000000);
        txtShadow(c, "×", W - 33 - textRenderer.getWidth("×") / 2, 27, 0xFFFFFFFF);

        // Puste miejsce - tu wcześniej było ustawianie obrotu kamery (usunięte, niedozwolone na serwerze).
        rr(c, PAD, g.emptyY(), CW, g.emptyH(), 16, mix(dark, 0xFFFFFFFF, 0.03f));

        // Autostacking
        rrb(c, PAD, g.autoY(), CW, g.footerY() - g.autoY() - 14, 20, cardBorder, card);
        txtShadow(c, "Autostacking", PAD + 16, g.autoY() + 10, WHITE);
        txt(c, "Auto-dopełnianie hotbara i off-handu", PAD + 16, g.autoY() + 22, GREY);
        int hbTogW = 38, hbTogH = 20;
        int hbTogX = PAD + CW - 14 - hbTogW, hbTogY = g.autoY() + 12;
        boolean hb = cfg.hotbarRefillEnabled;
        rr(c, hbTogX, hbTogY, hbTogW, hbTogH, hbTogH / 2, hb ? t.primary() : 0xFF39445A);
        circle(c, hb ? hbTogX + hbTogW - 10 : hbTogX + 10, hbTogY + hbTogH / 2, 8, 0xFFFFFFFF);

        String arrow = autostackingExpanded ? "▾" : "▸";
        txtShadow(c, arrow + " Dodatkowe bloki", PAD + 16, g.expandY() + 6, t.secondary());
        String countStr = cfg.extraRefillItems.size() + " wybrane";
        txt(c, countStr, PAD + CW - 14 - textRenderer.getWidth(countStr), g.expandY() + 6, GREY);

        if (autostackingExpanded) {
            rrb(c, PAD + 14, g.searchY(), CW - 28, 20, 10, focused == Field.SEARCH ? t.primary() : 0xFF3A465F, 0xFF0C1226);
            drawMagnifier(c, PAD + 26, g.searchY() + 10);
            String shown = searchQuery.isEmpty() ? "Przewiń lub wpisz nazwę..." : searchQuery;
            txt(c, shown, PAD + 40, g.searchY() + 6, searchQuery.isEmpty() ? GREY : 0xFFFFFFFF);
            if (focused == Field.SEARCH && (System.currentTimeMillis() / 500) % 2 == 0 && !searchQuery.isEmpty()) {
                int cx = PAD + 40 + textRenderer.getWidth(searchQuery);
                c.fill(cx, g.searchY() + 4, cx + 1, g.searchY() + 16, t.primary());
            }

            rr(c, PAD + 14, g.listY(), CW - 28, g.listH(), 12, mix(dark, 0xFFFFFFFF, 0.04f));
            List<Item> results = computeResults();
            int rowH = 20;
            int visible = g.listH() / rowH;
            scrollOffset = clampI(scrollOffset, 0, Math.max(0, results.size() - visible));
            if (results.isEmpty()) {
                txt(c, "Brak wyników", PAD + 26, g.listY() + 8, GREY);
            } else {
                for (int row = 0; row < visible && scrollOffset + row < results.size(); row++) {
                    Item item = results.get(scrollOffset + row);
                    int ry = g.listY() + row * rowH;
                    Identifier id = Registries.ITEM.getId(item);
                    boolean sel = id != null && cfg.extraRefillItems.contains(id.toString());
                    if (sel) rr(c, PAD + 16, ry + 1, CW - 32, rowH - 2, 8, mix(dark, t.primary(), 0.18f));
                    c.drawItem(new ItemStack(item), PAD + 20, ry + 2);
                    circleRing(c, PAD + 48, ry + 10, 7, sel ? t.primary() : 0xFF39445A, sel ? mix(dark, t.primary(), 0.4f) : mix(dark, 0xFFFFFFFF, 0.06f));
                    if (sel) check(c, PAD + 48, ry + 10);
                    txt(c, item.getName().getString(), PAD + 64, ry + 6, WHITE);
                }
            }
        }

        // Stopka - keybind
        rrb(c, PAD, g.footerY(), CW, 30, 15, cardBorder, mix(dark, t.primary(), 0.08f));
        String key = HitSpinClient.OPEN_GUI == null ? "N" : HitSpinClient.OPEN_GUI.getBoundKeyLocalizedText().getString();
        int kw = Math.max(18, textRenderer.getWidth(key) + 12);
        rrb(c, PAD + 10, g.footerY() + 5, kw, 18, 9, t.primary(), mix(dark, t.primary(), 0.35f));
        txtShadow(c, key, PAD + 10 + kw / 2 - textRenderer.getWidth(key) / 2, g.footerY() + 9, 0xFFFFFFFF);
        txt(c, "otwiera GUI moda (zmiana: Opcje > Sterowanie)", PAD + 10 + kw + 10, g.footerY() + 9, GREY);

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
            all.sort(java.util.Comparator.comparing(it -> it.getName().getString().toLowerCase(Locale.ROOT)));
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

    // ---------- Obsługa myszy ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        layout();
        Geo g = geo();
        double x = vx(mx), y = vy(my);
        HitSpinClient.Config cfg = HitSpinClient.CONFIG;

        boolean hitSearch = autostackingExpanded && in(x, y, PAD + 14, g.searchY(), CW - 28, 20);
        if (!hitSearch && focused == Field.SEARCH) focused = Field.NONE;
        if (hitSearch) { focused = Field.SEARCH; return true; }

        if (in(x, y, W - 46, 20, 26, 26)) { close(); return true; }
        for (int i = 0; i < THEMES.length; i++) {
            if (in(x, y, 78 + i * 20, 55, 14, 14)) { cfg.theme = i; HitSpinClient.saveConfig(); return true; }
        }

        int hbTogW = 38;
        int hbTogX = PAD + CW - 14 - hbTogW, hbTogY = g.autoY() + 12;
        if (in(x, y, hbTogX, hbTogY, hbTogW, 20)) { cfg.hotbarRefillEnabled = !cfg.hotbarRefillEnabled; HitSpinClient.saveConfig(); return true; }
        if (in(x, y, PAD + 14, g.expandY(), CW - 28, 20)) {
            autostackingExpanded = !autostackingExpanded;
            focused = autostackingExpanded ? Field.SEARCH : Field.NONE;
            return true;
        }

        if (autostackingExpanded && in(x, y, PAD + 14, g.listY(), CW - 28, g.listH())) {
            int rowH = 20;
            int row = (int) ((y - g.listY()) / rowH);
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
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (autostackingExpanded) {
            layout();
            Geo g = geo();
            double x = vx(mx), y = vy(my);
            if (in(x, y, PAD + 14, g.listY(), CW - 28, g.listH())) {
                scrollOffset = clampI(scrollOffset - (int) Math.signum(vertical) * 3, 0, Math.max(0, computeResults().size() - g.listH() / 20));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    // ---------- Wpisywanie tekstu (wyszukiwarka) ----------
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focused == Field.SEARCH) {
            if (searchQuery.length() < 40 && chr >= 32) searchQuery += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
