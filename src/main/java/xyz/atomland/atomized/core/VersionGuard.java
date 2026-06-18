package xyz.atomland.atomized.core;

/**
 * Minimal Minecraft release-version predicate matcher.
 *
 * <p>Supports the subset of fabric-style predicates Atomized actually uses:
 * {@code *}, {@code =1.21.4}, {@code >=1.21.2 <=1.21.3} (whitespace-separated clauses
 * are AND-ed). Versions are compared as dotted numeric segments; a missing segment
 * counts as {@code 0} (so {@code 1.21} == {@code 1.21.0}). Pre-release suffixes after
 * {@code -} are ignored for comparison (Atomized only targets releases).
 */
public final class VersionGuard {
    private VersionGuard() {
    }

    /**
     * @param version a release version such as {@code "1.21.4"}
     * @param predicate {@code "*"} or AND-ed clauses such as {@code ">=1.21 <=1.21.1"}
     * @return whether the version satisfies every clause
     * @throws IllegalArgumentException on a malformed predicate or version
     */
    public static boolean matches(String version, String predicate) {
        if (version == null || predicate == null) {
            throw new IllegalArgumentException("version and predicate must not be null");
        }
        String trimmed = predicate.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("empty predicate");
        }
        for (String clause : trimmed.split("\\s+")) {
            if (!matchesClause(version, clause)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesClause(String version, String clause) {
        if (clause.equals("*")) {
            return true;
        }
        String op;
        String bound;
        if (clause.startsWith(">=") || clause.startsWith("<=")) {
            op = clause.substring(0, 2);
            bound = clause.substring(2);
        } else if (clause.startsWith(">") || clause.startsWith("<") || clause.startsWith("=")) {
            op = clause.substring(0, 1);
            bound = clause.substring(1);
        } else {
            op = "=";
            bound = clause;
        }
        int cmp = compare(version, bound);
        return switch (op) {
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            default -> cmp == 0;
        };
    }

    /**
     * Compares two dotted numeric versions ({@code 1.21} &lt; {@code 1.21.2} &lt; {@code 1.21.10}).
     *
     * @throws IllegalArgumentException if a segment is not numeric
     */
    public static int compare(String a, String b) {
        int[] left = parse(a);
        int[] right = parse(b);
        int len = Math.max(left.length, right.length);
        for (int i = 0; i < len; i++) {
            int l = i < left.length ? left[i] : 0;
            int r = i < right.length ? right[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int[] parse(String version) {
        String release = version;
        int dash = release.indexOf('-');
        if (dash >= 0) {
            release = release.substring(0, dash);
        }
        if (release.isEmpty()) {
            throw new IllegalArgumentException("malformed version: " + version);
        }
        String[] parts = release.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("malformed version segment in: " + version, e);
            }
            if (out[i] < 0) {
                throw new IllegalArgumentException("negative version segment in: " + version);
            }
        }
        return out;
    }
}
