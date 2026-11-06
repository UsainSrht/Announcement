package me.usainsrht.announcement.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny YAML reader, just enough for config.yml (replaces SnakeYAML, ~350 KB, in the jar).
 * <p>
 * Supported: block maps and block lists (nested, list items that are maps), comments, plain / 'single' /
 * "double quoted" scalars (with the usual escapes), booleans, numbers, null, {@code []} / {@code {}},
 * one-line flow lists of scalars, and {@code |} / {@code >} block scalars.
 * Not supported: anchors, tags, multi-document files, complex keys, nested flow collections.
 * <p>
 * Values come back as {@link LinkedHashMap}, {@link ArrayList}, {@link String}, {@link Boolean},
 * {@link Integer}/{@link Long}/{@link Double} or {@code null}.
 */
public final class MiniYaml {
    private static final class Line {
        int indent;
        String text; // comment stripped, trailing blanks removed
        String raw;  // untouched (for block scalars)

        Line(int indent, String text, String raw) {
            this.indent = indent;
            this.text = text;
            this.raw = raw;
        }
    }

    private final List<Line> lines = new ArrayList<>();
    private int pos;

    private MiniYaml() {
    }

    /** Parses a document. Returns an empty map for an empty document; the root is normally a map. */
    public static Object parse(String text) {
        MiniYaml y = new MiniYaml();
        y.split(text);
        if (y.lines.isEmpty()) return new LinkedHashMap<String, Object>();
        return y.block(y.lines.get(0).indent);
    }

    // ------------------------------------------------------------------ lines

    private void split(String text) {
        if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
        for (String raw : text.split("\r?\n|\r", -1)) {
            int i = 0;
            while (i < raw.length() && raw.charAt(i) == ' ') i++;
            String body = stripComment(raw.substring(i));
            int end = body.length();
            while (end > 0 && (body.charAt(end - 1) == ' ' || body.charAt(end - 1) == '\t')) end--;
            body = body.substring(0, end);
            // keep blank/comment lines too, block scalars need them; they are skipped via text.isEmpty()
            lines.add(new Line(i, body, raw));
        }
        // drop leading/trailing noise only logically: parsing skips empty lines itself
    }

    /** Cuts a trailing "# comment". A '#' only counts at the start or after whitespace and outside quotes. */
    private static String stripComment(String s) {
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (quote == '"' && c == '\\') {
                    i++;
                } else if (c == quote) {
                    if (quote == '\'' && i + 1 < s.length() && s.charAt(i + 1) == '\'') i++; // '' escape
                    else quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                if (opensScalar(s, i)) quote = c;
            } else if (c == '#' && (i == 0 || s.charAt(i - 1) == ' ' || s.charAt(i - 1) == '\t')) {
                return s.substring(0, i);
            }
        }
        return s;
    }

    /** A quote starts a quoted scalar only at the start of a value, not inside e.g. {@code can't}. */
    private static boolean opensScalar(String s, int i) {
        int j = i - 1;
        while (j >= 0 && (s.charAt(j) == ' ' || s.charAt(j) == '\t')) j--;
        if (j < 0) return true;
        char p = s.charAt(j);
        return j < i - 1 && (p == '-' || p == ':' || p == '[' || p == ',' || p == '{')
                || (j == i - 1 && (p == '[' || p == ',' || p == '{'));
    }

    private Line peek() {
        while (pos < lines.size() && lines.get(pos).text.isEmpty()) pos++;
        return pos < lines.size() ? lines.get(pos) : null;
    }

    // ------------------------------------------------------------------ blocks

    private Object block(int indent) {
        Line l = peek();
        if (l == null) return null;
        if (isItem(l.text)) return list(l.indent);
        return map(l.indent);
    }

    private static boolean isItem(String t) {
        return t.equals("-") || t.startsWith("- ");
    }

    private Map<String, Object> map(int indent) {
        Map<String, Object> out = new LinkedHashMap<>();
        Line l;
        while ((l = peek()) != null && l.indent == indent && !isItem(l.text)) {
            int colon = keyEnd(l.text);
            if (colon < 0) throw new IllegalArgumentException("line " + (pos + 1) + ": expected 'key: value'");
            String key = scalarText(l.text.substring(0, colon).trim());
            String rest = l.text.substring(colon + 1).trim();
            pos++;
            out.put(key, value(rest, indent));
        }
        Line bad = peek();
        if (bad != null && bad.indent > indent) {
            throw new IllegalArgumentException("line " + (pos + 1) + ": unexpected indentation");
        }
        return out;
    }

    private List<Object> list(int indent) {
        List<Object> out = new ArrayList<>();
        Line l;
        while ((l = peek()) != null && l.indent == indent && isItem(l.text)) {
            String rest = l.text.length() <= 2 ? "" : l.text.substring(2);
            int lead = 0;
            while (lead < rest.length() && rest.charAt(lead) == ' ') lead++;
            rest = rest.substring(lead);
            if (rest.isEmpty()) {
                pos++;
                Line n = peek();
                out.add(n != null && n.indent > indent ? block(n.indent) : null);
            } else if (keyEnd(rest) >= 0) {
                // "- key: value": the item is a map whose column is where 'key' starts
                l.indent = indent + 2 + lead;
                l.text = rest;
                out.add(map(l.indent));
            } else if (isItem(rest)) {
                l.indent = indent + 2 + lead;
                l.text = rest;
                out.add(list(l.indent));
            } else {
                pos++;
                out.add(value(rest, indent));
            }
        }
        return out;
    }

    /** Value after "key:" (rest), possibly continued on the following, deeper indented lines. */
    private Object value(String rest, int indent) {
        if (rest.isEmpty()) {
            Line n = peek();
            if (n == null) return null;
            if (n.indent > indent) return block(n.indent);
            if (n.indent == indent && isItem(n.text)) return list(indent); // "key:\n- a" at the same column
            return null;
        }
        if (rest.charAt(0) == '|' || rest.charAt(0) == '>') return blockScalar(rest, indent);
        return scalar(rest);
    }

    private String blockScalar(String header, int indent) {
        boolean folded = header.charAt(0) == '>';
        boolean keep = header.contains("+");
        boolean strip = header.contains("-");
        List<String> body = new ArrayList<>();
        int blockIndent = -1;
        while (pos < lines.size()) {
            Line l = lines.get(pos);
            boolean blank = l.raw.trim().isEmpty();
            if (!blank) {
                if (blockIndent < 0) {
                    if (l.indent <= indent) break;
                    blockIndent = l.indent;
                } else if (l.indent < blockIndent) {
                    break;
                }
            }
            body.add(blank ? "" : l.raw.substring(blockIndent));
            pos++;
        }
        while (!keep && !body.isEmpty() && body.get(body.size() - 1).isEmpty()) body.remove(body.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < body.size(); i++) {
            String s = body.get(i);
            if (i > 0) sb.append(folded && !s.isEmpty() && !body.get(i - 1).isEmpty() ? " " : "\n");
            sb.append(s);
        }
        if (!strip && sb.length() > 0) sb.append('\n');
        return sb.toString();
    }

    // ------------------------------------------------------------------ scalars

    private static boolean startsQuoted(String s) {
        return !s.isEmpty() && (s.charAt(0) == '"' || s.charAt(0) == '\'');
    }

    /** Index of the ':' that ends a mapping key, or -1 when the text is not "key: ..." / "key:". */
    private static int keyEnd(String s) {
        int i = 0;
        if (startsQuoted(s)) {
            char q = s.charAt(0);
            i = 1;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (q == '"' && c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == q) {
                    if (q == '\'' && i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                        i += 2;
                        continue;
                    }
                    break;
                }
                i++;
            }
            i++; // past the closing quote
            while (i < s.length() && s.charAt(i) == ' ') i++;
            return i < s.length() && s.charAt(i) == ':' && (i + 1 == s.length() || s.charAt(i + 1) == ' ') ? i : -1;
        }
        if (s.startsWith("[") || s.startsWith("{")) return -1;
        for (; i < s.length(); i++) {
            if (s.charAt(i) == ':' && (i + 1 == s.length() || s.charAt(i + 1) == ' ')) return i;
        }
        return -1;
    }

    private static String scalarText(String s) {
        Object o = scalar(s);
        return o == null ? "null" : String.valueOf(o);
    }

    private static Object scalar(String s) {
        if (s.isEmpty()) return null;
        char c = s.charAt(0);
        if (c == '"') return doubleQuoted(s);
        if (c == '\'') return singleQuoted(s);
        if (c == '[') return flowList(s);
        if (c == '{') {
            if (s.replace(" ", "").equals("{}")) return new LinkedHashMap<String, Object>();
            throw new IllegalArgumentException("flow maps are not supported: " + s);
        }
        switch (s) {
            case "~":
            case "null":
            case "Null":
            case "NULL":
                return null;
            case "true":
            case "True":
            case "TRUE":
                return Boolean.TRUE;
            case "false":
            case "False":
            case "FALSE":
                return Boolean.FALSE;
            default:
        }
        if (s.matches("[-+]?\\d+")) {
            try {
                long v = Long.parseLong(s.startsWith("+") ? s.substring(1) : s);
                return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE ? (Object) (int) v : (Object) v;
            } catch (NumberFormatException ignored) {
                return s;
            }
        }
        if (s.matches("[-+]?(\\d+\\.\\d*|\\.\\d+)([eE][-+]?\\d+)?")) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return s;
            }
        }
        return s;
    }

    private static String singleQuoted(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                    sb.append('\'');
                    i++;
                } else {
                    return sb.toString();
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("unterminated quote: " + s);
    }

    private static String doubleQuoted(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') return sb.toString();
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case '0': sb.append('\0'); break;
                case 'e': sb.append('\u001B'); break;
                case '/': sb.append('/'); break;
                case ' ': sb.append(' '); break;
                case 'x': i = hex(s, i, 2, sb); break;
                case 'u': i = hex(s, i, 4, sb); break;
                case 'U': i = hex(s, i, 8, sb); break;
                default: sb.append(n); // \" \\ and anything else
            }
        }
        throw new IllegalArgumentException("unterminated quote: " + s);
    }

    private static int hex(String s, int at, int digits, StringBuilder sb) {
        if (at + digits >= s.length()) throw new IllegalArgumentException("bad escape in: " + s);
        sb.appendCodePoint(Integer.parseInt(s.substring(at + 1, at + 1 + digits), 16));
        return at + digits;
    }

    private static List<Object> flowList(String s) {
        List<Object> out = new ArrayList<>();
        int end = s.lastIndexOf(']');
        if (end < 0) throw new IllegalArgumentException("unterminated list: " + s);
        String inner = s.substring(1, end).trim();
        int i = 0;
        while (i < inner.length()) {
            while (i < inner.length() && (inner.charAt(i) == ' ' || inner.charAt(i) == ',')) i++;
            if (i >= inner.length()) break;
            int start = i;
            if (startsQuoted(inner.substring(i))) {
                char q = inner.charAt(i++);
                while (i < inner.length()) {
                    char c = inner.charAt(i);
                    if (q == '"' && c == '\\') {
                        i += 2;
                        continue;
                    }
                    if (c == q) {
                        if (q == '\'' && i + 1 < inner.length() && inner.charAt(i + 1) == '\'') {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
            } else {
                while (i < inner.length() && inner.charAt(i) != ',') i++;
            }
            out.add(scalar(inner.substring(start, Math.min(i, inner.length())).trim()));
        }
        return out;
    }
}
