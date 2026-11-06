package me.usainsrht.announcement.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Dependency free MiniMessage subset used on Spigot and on every mod loader (Paper uses the real MiniMessage).
 * <p>
 * Supported: colors (named, #rrggbb, &lt;color:..&gt;), bold/italic/underlined/strikethrough/obfuscated (and !negation),
 * reset, newline, gradient, rainbow, centered, click, hover:show_text, selector, and the announcement placeholder.
 * Output is a flat list of fully styled segments which can be rendered to chat JSON, legacy section codes or plain text.
 */
public final class MiniText {
    private MiniText() {
    }

    // ------------------------------------------------------------------ model

    public static final class Style {
        String color;
        Boolean bold, italic, underlined, strike, obfuscated;
        String clickAction, clickValue;
        List<Seg> hover;

        Style copy() {
            Style s = new Style();
            s.color = color;
            s.bold = bold;
            s.italic = italic;
            s.underlined = underlined;
            s.strike = strike;
            s.obfuscated = obfuscated;
            s.clickAction = clickAction;
            s.clickValue = clickValue;
            s.hover = hover;
            return s;
        }

        /** child's explicit fields win over this (parent) style. */
        Style overlay(Style child) {
            Style s = copy();
            if (child.color != null) s.color = child.color;
            if (child.bold != null) s.bold = child.bold;
            if (child.italic != null) s.italic = child.italic;
            if (child.underlined != null) s.underlined = child.underlined;
            if (child.strike != null) s.strike = child.strike;
            if (child.obfuscated != null) s.obfuscated = child.obfuscated;
            if (child.clickAction != null) {
                s.clickAction = child.clickAction;
                s.clickValue = child.clickValue;
            }
            if (child.hover != null) s.hover = child.hover;
            return s;
        }
    }

    public static final class Seg {
        public final String text;
        public final String selector;
        public final Style style;

        Seg(String text, String selector, Style style) {
            this.text = text;
            this.selector = selector;
            this.style = style;
        }

        boolean bold() {
            return Boolean.TRUE.equals(style.bold);
        }
    }

    // ------------------------------------------------------------------ colors

    private static final String[][] NAMED = {
            {"black", "000000", "0"}, {"dark_blue", "0000AA", "1"}, {"dark_green", "00AA00", "2"},
            {"dark_aqua", "00AAAA", "3"}, {"dark_red", "AA0000", "4"}, {"dark_purple", "AA00AA", "5"},
            {"gold", "FFAA00", "6"}, {"gray", "AAAAAA", "7"}, {"dark_gray", "555555", "8"},
            {"blue", "5555FF", "9"}, {"green", "55FF55", "a"}, {"aqua", "55FFFF", "b"},
            {"red", "FF5555", "c"}, {"light_purple", "FF55FF", "d"}, {"yellow", "FFFF55", "e"},
            {"white", "FFFFFF", "f"}};

    private static String namedColor(String n) {
        n = n.toLowerCase(Locale.ROOT);
        if (n.equals("grey")) n = "gray";
        if (n.equals("dark_grey")) n = "dark_gray";
        for (String[] c : NAMED) if (c[0].equals(n)) return c[0];
        return null;
    }

    /** Returns a canonical color (vanilla name or #rrggbb) or null if it is not a color. */
    private static String parseColor(String s) {
        if (s == null) return null;
        if (s.startsWith("#") && s.length() == 7) {
            try {
                Integer.parseInt(s.substring(1), 16);
                return s.toLowerCase(Locale.ROOT);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return namedColor(s);
    }

    private static int rgb(String color) {
        if (color.startsWith("#")) return Integer.parseInt(color.substring(1), 16);
        for (String[] c : NAMED) if (c[0].equals(color)) return Integer.parseInt(c[1], 16);
        return 0xFFFFFF;
    }

    private static String hex(int rgb) {
        return String.format("#%06x", rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ parsing

    private static final class Frame {
        final String kind;
        final Style style;       // resolved style while this frame is open
        final int start;         // index into out where this frame's content begins
        final List<String> args;

        Frame(String kind, Style style, int start, List<String> args) {
            this.kind = kind;
            this.style = style;
            this.start = start;
            this.args = args;
        }
    }

    public static List<Seg> parse(String input, boolean allowCenter) {
        return parse(input, allowCenter, new HashMap<String, List<Seg>>());
    }

    public static List<Seg> parse(String in, boolean allowCenter, Map<String, List<Seg>> inserts) {
        List<Seg> out = new ArrayList<>();
        Deque<Frame> stack = new ArrayDeque<>();
        Style base = new Style();
        StringBuilder text = new StringBuilder();
        int n = in.length();
        int i = 0;
        while (i < n) {
            char c = in.charAt(i);
            if (c == '\\' && i + 1 < n && (in.charAt(i + 1) == '<' || in.charAt(i + 1) == '\\')) {
                text.append(in.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '<') {
                int end = tagEnd(in, i);
                if (end > 0) {
                    String tag = in.substring(i + 1, end);
                    flush(text, out, current(stack, base));
                    if (handleTag(tag, out, stack, base, allowCenter, inserts)) {
                        i = end + 1;
                        continue;
                    }
                }
            }
            text.append(c);
            i++;
        }
        flush(text, out, current(stack, base));
        while (!stack.isEmpty()) close(stack.pop(), out);
        return out;
    }

    private static int tagEnd(String s, int from) {
        char quote = 0;
        for (int i = from + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '>') {
                return i;
            } else if (c == '<') {
                return -1;
            }
        }
        return -1;
    }

    private static Style current(Deque<Frame> stack, Style base) {
        return stack.isEmpty() ? base : stack.peek().style;
    }

    private static void flush(StringBuilder text, List<Seg> out, Style style) {
        if (text.length() == 0) return;
        out.add(new Seg(text.toString(), null, style));
        text.setLength(0);
    }

    private static List<String> split(String tag) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                else sb.append(c);
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ':') {
                parts.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        parts.add(sb.toString());
        return parts;
    }

    private static String decoKind(String name) {
        switch (name) {
            case "bold": case "b": return "bold";
            case "italic": case "i": case "em": return "italic";
            case "underlined": case "u": return "underlined";
            case "strikethrough": case "st": return "strikethrough";
            case "obfuscated": case "obf": return "obfuscated";
            default: return null;
        }
    }

    private static boolean handleTag(String tag, List<Seg> out, Deque<Frame> stack, Style base,
                                     boolean allowCenter, Map<String, List<Seg>> inserts) {
        Style cur = current(stack, base);
        // closing tag
        if (tag.startsWith("/")) {
            List<String> parts = split(tag.substring(1));
            String name = parts.get(0).toLowerCase(Locale.ROOT);
            String kind = kindOf(name);
            if (kind == null) return false;
            Frame found = null;
            for (Frame f : stack) {
                if (f.kind.equals(kind)) {
                    found = f;
                    break;
                }
            }
            if (found == null) return true; // stray closing tag: swallow it like MiniMessage does
            while (true) {
                Frame f = stack.pop();
                close(f, out);
                if (f == found) break;
            }
            return true;
        }

        boolean negate = tag.startsWith("!");
        List<String> parts = split(negate ? tag.substring(1) : tag);
        String name = parts.get(0).toLowerCase(Locale.ROOT);
        List<String> args = parts.subList(1, parts.size());

        String deco = decoKind(name);
        if (deco != null) {
            Style s = cur.copy();
            Boolean v = !negate;
            switch (deco) {
                case "bold": s.bold = v; break;
                case "italic": s.italic = v; break;
                case "underlined": s.underlined = v; break;
                case "strikethrough": s.strike = v; break;
                default: s.obfuscated = v; break;
            }
            stack.push(new Frame(deco, s, out.size(), args));
            return true;
        }
        if (negate) return false;

        String color = parseColor(name);
        if (color == null && (name.equals("color") || name.equals("c") || name.equals("colour")) && !args.isEmpty()) {
            color = parseColor(args.get(0));
        }
        if (color != null) {
            Style s = cur.copy();
            s.color = color;
            stack.push(new Frame("color", s, out.size(), args));
            return true;
        }

        switch (name) {
            case "reset":
                stack.push(new Frame("reset", base, out.size(), args));
                return true;
            case "newline": case "br":
                out.add(new Seg("\n", null, cur));
                return true;
            case "gradient": case "rainbow":
                stack.push(new Frame(name, cur, out.size(), args));
                return true;
            case "centered":
                if (!allowCenter) return false;
                stack.push(new Frame("centered", cur, out.size(), args));
                return true;
            case "selector":
                if (args.isEmpty()) return false;
                out.add(new Seg(null, args.get(0), cur));
                return true;
            case "announcement": {
                List<Seg> ins = inserts.get("announcement");
                if (ins == null) return false;
                for (Seg s : ins) out.add(new Seg(s.text, s.selector, cur.overlay(s.style)));
                return true;
            }
            case "click": {
                if (args.size() < 2) return false;
                String action = args.get(0).toLowerCase(Locale.ROOT);
                if (!action.equals("open_url") && !action.equals("run_command") && !action.equals("suggest_command")
                        && !action.equals("copy_to_clipboard")) return false;
                Style s = cur.copy();
                s.clickAction = action;
                s.clickValue = join(args, 1);
                stack.push(new Frame("click", s, out.size(), args));
                return true;
            }
            case "hover": {
                if (args.size() < 2 || !args.get(0).equalsIgnoreCase("show_text")) return false;
                Style s = cur.copy();
                s.hover = parse(join(args, 1), allowCenter, inserts);
                stack.push(new Frame("hover", s, out.size(), args));
                return true;
            }
            default:
                return false;
        }
    }

    private static String join(List<String> args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.size(); i++) {
            if (i > from) sb.append(':');
            sb.append(args.get(i));
        }
        return sb.toString();
    }

    private static String kindOf(String name) {
        String d = decoKind(name);
        if (d != null) return d;
        if (parseColor(name) != null || name.equals("color") || name.equals("c") || name.equals("colour")) return "color";
        switch (name) {
            case "reset": case "gradient": case "rainbow": case "centered": case "click": case "hover":
                return name;
            default:
                return null;
        }
    }

    /** Called when a frame ends; applies gradient/rainbow/centered to everything it contained. */
    private static void close(Frame f, List<Seg> out) {
        switch (f.kind) {
            case "gradient": applyGradient(f, out); break;
            case "rainbow": applyRainbow(f, out); break;
            case "centered": applyCentered(f, out); break;
            default: break;
        }
    }

    private static int codePoints(List<Seg> out, int from) {
        int total = 0;
        for (int i = from; i < out.size(); i++) {
            Seg s = out.get(i);
            total += s.text == null ? 1 : s.text.codePointCount(0, s.text.length());
        }
        return total;
    }

    private interface ColorAt {
        String at(int index, int total);
    }

    private static void recolor(Frame f, List<Seg> out, ColorAt colorAt) {
        int total = codePoints(out, f.start);
        if (total == 0) return;
        List<Seg> rest = new ArrayList<>(out.subList(f.start, out.size()));
        while (out.size() > f.start) out.remove(out.size() - 1);
        int idx = 0;
        for (Seg s : rest) {
            if (s.text == null) {
                Style st = s.style.copy();
                st.color = colorAt.at(idx++, total);
                out.add(new Seg(null, s.selector, st));
                continue;
            }
            int p = 0;
            while (p < s.text.length()) {
                int cp = s.text.codePointAt(p);
                String ch = new String(Character.toChars(cp));
                p += ch.length();
                Style st = s.style.copy();
                st.color = colorAt.at(idx++, total);
                out.add(new Seg(ch, null, st));
            }
        }
    }

    private static double phaseOf(List<String> args) {
        if (!args.isEmpty()) {
            try {
                return Double.parseDouble(args.get(args.size() - 1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static void applyGradient(Frame f, List<Seg> out) {
        final List<Integer> colors = new ArrayList<>();
        for (String a : f.args) {
            String c = parseColor(a);
            if (c != null) colors.add(rgb(c));
        }
        if (colors.isEmpty()) {
            colors.add(0xFFFFFF);
            colors.add(0x000000);
        }
        if (colors.size() == 1) colors.add(colors.get(0));
        final double phase = phaseOf(f.args);
        recolor(f, out, new ColorAt() {
            @Override
            public String at(int index, int total) {
                double t = total <= 1 ? 0 : (double) index / (total - 1);
                if (phase != 0) t = ((t + phase) % 1 + 1) % 1;
                int segments = colors.size() - 1;
                double scaled = t * segments;
                int k = Math.min((int) scaled, segments - 1);
                double local = scaled - k;
                int a = colors.get(k), b = colors.get(k + 1);
                int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * local);
                int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * local);
                int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * local);
                return hex((r << 16) | (g << 8) | bl);
            }
        });
    }

    private static void applyRainbow(Frame f, List<Seg> out) {
        boolean reversed = false;
        for (String a : f.args) if (a.equals("!")) reversed = true;
        final boolean rev = reversed;
        final double phase = phaseOf(f.args);
        recolor(f, out, new ColorAt() {
            @Override
            public String at(int index, int total) {
                double t = (double) (rev ? total - 1 - index : index) / Math.max(1, total);
                float h = (float) (((t + phase) % 1 + 1) % 1);
                return hex(hueToRgb(h));
            }
        });
    }

    /** Fully saturated, full brightness hue (0..1) to 0xRRGGBB. */
    private static int hueToRgb(float h) {
        float x = h * 6f;
        int i = (int) x;
        float f = x - i;
        float r, g, b;
        switch (i % 6) {
            case 0: r = 1; g = f; b = 0; break;
            case 1: r = 1 - f; g = 1; b = 0; break;
            case 2: r = 0; g = 1; b = f; break;
            case 3: r = 0; g = 1 - f; b = 1; break;
            case 4: r = f; g = 0; b = 1; break;
            default: r = 1; g = 0; b = 1 - f; break;
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    // ------------------------------------------------------------------ centering

    public static int charWidth(char c) {
        switch (c) {
            case 'f': case 'k': case '<': case '>':
                return 4;
            case 'ı': case 'i': case '!': case ':': case ';': case '\'': case '|': case '.': case ',':
                return 1;
            case 'I': case 'İ': case 't': case '*': case '"': case '(': case ')': case '{': case '}':
            case '[': case ']': case ' ':
                return 3;
            case 'l': case '`':
                return 2;
            case '@':
                return 6;
            default:
                return 5;
        }
    }

    private static int width(List<Seg> segs, int from) {
        int w = 0;
        for (int i = from; i < segs.size(); i++) {
            Seg s = segs.get(i);
            if (s.text == null) continue;
            for (int k = 0; k < s.text.length(); k++) {
                w += charWidth(s.text.charAt(k)) + 1;
                if (s.bold()) w += 1;
            }
        }
        return w;
    }

    private static void applyCentered(Frame f, List<Seg> out) {
        int length = 320;
        char fill = ' ';
        try {
            if (!f.args.isEmpty()) length = Integer.parseInt(f.args.get(0).trim());
        } catch (NumberFormatException ignored) {
        }
        if (f.args.size() > 1 && !f.args.get(1).isEmpty()) fill = f.args.get(1).charAt(0);
        int w = width(out, f.start);
        int step = charWidth(fill) + 1;
        StringBuilder pad = new StringBuilder();
        int compensated = 0;
        while (compensated < (length - w) / 2) {
            pad.append(fill);
            compensated += step;
        }
        if (pad.length() > 0) out.add(f.start, new Seg(pad.toString(), null, new Style()));
    }

    // ------------------------------------------------------------------ rendering a whole announcement

    /**
     * Builds template + message into one list of segments.
     *
     * @param pre applied to the message and to every template line before parsing (placeholder expansion)
     */
    public static List<Seg> renderTemplate(List<String> template, String message, Function<String, String> pre,
                                           boolean allowCenter) {
        Map<String, List<Seg>> inserts = new HashMap<>();
        inserts.put("announcement", parse(pre.apply(message), allowCenter));
        List<Seg> all = new ArrayList<>();
        for (String line : template) {
            all.addAll(parse(pre.apply(line), allowCenter, inserts));
            all.add(new Seg("\n", null, new Style()));
        }
        return all;
    }

    // ------------------------------------------------------------------ output formats

    public static String toPlain(List<Seg> segs) {
        StringBuilder sb = new StringBuilder();
        for (Seg s : segs) sb.append(s.text != null ? s.text : "player");
        return sb.toString();
    }

    /** Section sign formatting codes, understood by vanilla chat, Bukkit consoles and Minecraft text rendering. */
    public static String toLegacy(List<Seg> segs) {
        StringBuilder sb = new StringBuilder();
        for (Seg s : segs) {
            String t = s.text != null ? s.text : "you";
            if (t.equals("\n")) {
                sb.append('\n');
                continue;
            }
            sb.append("§r");
            if (s.style.color != null) {
                String c = s.style.color;
                if (c.startsWith("#")) {
                    sb.append("§x");
                    for (int i = 1; i < 7; i++) sb.append('§').append(c.charAt(i));
                } else {
                    for (String[] nc : NAMED) if (nc[0].equals(c)) sb.append('§').append(nc[2]);
                }
            }
            if (Boolean.TRUE.equals(s.style.bold)) sb.append("§l");
            if (Boolean.TRUE.equals(s.style.italic)) sb.append("§o");
            if (Boolean.TRUE.equals(s.style.underlined)) sb.append("§n");
            if (Boolean.TRUE.equals(s.style.strike)) sb.append("§m");
            if (Boolean.TRUE.equals(s.style.obfuscated)) sb.append("§k");
            sb.append(t);
        }
        return sb.toString();
    }

    /**
     * Chat component JSON. Both the pre- and post-1.21.5 spellings of click/hover events are written;
     * every game version ignores the keys of the other format.
     */
    public static String toJson(List<Seg> segs) {
        StringBuilder sb = new StringBuilder("{\"text\":\"\",\"extra\":[");
        boolean first = true;
        for (Seg s : segs) {
            if (!first) sb.append(',');
            first = false;
            segJson(sb, s);
        }
        return sb.append("]}").toString();
    }

    private static void segJson(StringBuilder sb, Seg s) {
        sb.append('{');
        if (s.text != null) sb.append("\"text\":").append(quote(s.text));
        else sb.append("\"selector\":").append(quote(s.selector));
        Style st = s.style;
        if (st.color != null) sb.append(",\"color\":").append(quote(st.color));
        deco(sb, "bold", st.bold);
        deco(sb, "italic", st.italic);
        deco(sb, "underlined", st.underlined);
        deco(sb, "strikethrough", st.strike);
        deco(sb, "obfuscated", st.obfuscated);
        if (st.clickAction != null) {
            String a = st.clickAction, v = st.clickValue;
            sb.append(",\"clickEvent\":{\"action\":").append(quote(a)).append(",\"value\":").append(quote(v)).append('}');
            String key = a.equals("open_url") ? "url" : a.equals("copy_to_clipboard") ? "value" : "command";
            sb.append(",\"click_event\":{\"action\":").append(quote(a)).append(",\"").append(key).append("\":")
                    .append(quote(v)).append('}');
        }
        if (st.hover != null) {
            String h = toJson(st.hover);
            sb.append(",\"hoverEvent\":{\"action\":\"show_text\",\"contents\":").append(h).append('}');
            sb.append(",\"hover_event\":{\"action\":\"show_text\",\"value\":").append(h).append('}');
        }
        sb.append('}');
    }

    private static void deco(StringBuilder sb, String key, Boolean v) {
        if (v != null) sb.append(",\"").append(key).append("\":").append(v);
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
