package xyz.atomland.atomized.mixinsupport;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import xyz.atomland.atomized.compat.SodiumRegions;
import xyz.atomland.atomized.core.ModuleIds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Mechanically enforces the "never conflicts" constitution (master plan §5, §7) against
 * Atomized's own mixins — the strongest guarantee obtainable without a running client:
 *
 * <ol>
 *   <li>No {@code @Overwrite} and no {@code @Redirect} anywhere (golden rule §1): only
 *       chainable MixinExtras injectors are permitted, so other mods touching the same
 *       method coexist.</li>
 *   <li>No mixin targets a class Sodium rewrites ({@link SodiumRegions#forbidden()}).</li>
 *   <li>Every mixin lives under a module sub-package so the gate can disable it.</li>
 * </ol>
 *
 * <p>The audit scans the authored mixin sources under {@code src/main/java/.../mixin/}.
 */
class MixinAuditTest {
    private static final String MIXIN_REL = "src/main/java/xyz/atomland/atomized/mixin";
    private static final Pattern MIXIN_ANNOTATION =
            Pattern.compile("@Mixin\\s*\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern CLASS_REF = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\.class");
    private static final Pattern TARGETS_STRING = Pattern.compile("\"([^\"]+)\"");

    /** Mixin sub-packages that are infrastructure (not module-gated): allowed to exist ungated. */
    private static final Set<String> INFRA_PACKAGES = Set.of("core", "plugin");

    private static Path mixinDir() {
        Path dir = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir.resolve(MIXIN_REL);
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static List<Path> mixinFiles() throws IOException {
        Path dir = mixinDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    @Test
    void regionsResourceLoadsAndIsPopulated() {
        SodiumRegions regions = SodiumRegions.load();
        assertFalse(regions.forbidden().isEmpty(), "forbidden region list must not be empty");
        assertFalse(regions.injectableOverlap().isEmpty(), "injectable-overlap list must not be empty");
        // Spot-check known classifications so a regenerated map can't silently regress.
        assertTrue(regions.isForbidden("LevelRenderer"));
        assertTrue(regions.isForbidden("GameRenderer"));
        assertTrue(regions.isInjectableOverlap("Minecraft"));
        assertFalse(regions.isForbidden("ParticleEngine"), "ParticleEngine is untouched by Sodium");
    }

    @Test
    void regionsForbiddenAndOverlapAreDisjoint() {
        SodiumRegions regions = SodiumRegions.load();
        Set<String> intersection = new LinkedHashSet<>(regions.forbidden());
        intersection.retainAll(regions.injectableOverlap());
        assertTrue(intersection.isEmpty(), "a class cannot be both forbidden and injectable: " + intersection);
    }

    @Test
    void mixinSourceDirectoryIsLocatable() {
        // If this fails, the audit below silently scans nothing — guard against that.
        assertNotNull(mixinDir(), "could not locate the mixin source directory from " + System.getProperty("user.dir"));
    }

    @Test
    void noForbiddenInjectors() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mixinFiles()) {
            // Strip comments first so a Javadoc mentioning the banned annotations (as this
            // very class does) is not a false positive — only real code counts.
            String src = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            if (containsToken(src, "@Overwrite")) {
                violations.add(file.getFileName() + " uses @Overwrite (banned — not chainable)");
            }
            if (containsToken(src, "@Redirect")) {
                violations.add(file.getFileName() + " uses @Redirect (banned — not chainable)");
            }
        }
        assertTrue(violations.isEmpty(), "Golden rule §1 violated:\n" + String.join("\n", violations));
    }

    @Test
    void noMixinTargetsSodiumRewrittenClass() throws IOException {
        SodiumRegions regions = SodiumRegions.load();
        List<String> violations = new ArrayList<>();
        for (Path file : mixinFiles()) {
            String src = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            for (String target : extractMixinTargets(src)) {
                if (regions.isForbidden(target)) {
                    violations.add(file.getFileName() + " targets Sodium-rewritten class '" + target + "'");
                }
            }
        }
        assertTrue(violations.isEmpty(), "Golden rule §7 (do-not-touch) violated:\n" + String.join("\n", violations));
    }

    @Test
    void everyMixinIsModuleGatedOrInfrastructure() throws IOException {
        Path root = mixinDir();
        if (root == null) {
            return;
        }
        List<String> violations = new ArrayList<>();
        for (Path file : mixinFiles()) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            int slash = rel.indexOf('/');
            if (slash < 0) {
                // Directly in the mixin root: only allowed for infra, which lives in subpackages.
                violations.add(rel + " is not under a module sub-package");
                continue;
            }
            String firstPackage = rel.substring(0, slash);
            boolean known = ModuleIds.ALL.contains(firstPackage) || INFRA_PACKAGES.contains(firstPackage);
            if (!known) {
                violations.add(rel + " is under unknown sub-package '" + firstPackage + "'");
            }
        }
        assertTrue(violations.isEmpty(), "Mixins not gated to a module:\n" + String.join("\n", violations));
    }

    @Test
    void everyMixinDeclaresATarget() throws IOException {
        for (Path file : mixinFiles()) {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            if (!src.contains("@Mixin")) {
                fail(file.getFileName() + " is in the mixin tree but has no @Mixin annotation");
            }
            if (extractMixinTargets(src).isEmpty()) {
                fail(file.getFileName() + " has @Mixin but no resolvable target class");
            }
        }
    }

    /** Extracts simple target class names from every {@code @Mixin(...)} in the source. */
    static Set<String> extractMixinTargets(String src) {
        Set<String> targets = new LinkedHashSet<>();
        Matcher mixin = MIXIN_ANNOTATION.matcher(src);
        while (mixin.find()) {
            String body = mixin.group(1);
            Matcher classRef = CLASS_REF.matcher(body);
            while (classRef.find()) {
                targets.add(classRef.group(1));
            }
            if (body.contains("targets")) {
                Matcher str = TARGETS_STRING.matcher(body);
                while (str.find()) {
                    String fqn = str.group(1);
                    int dot = fqn.lastIndexOf('.');
                    targets.add(dot >= 0 ? fqn.substring(dot + 1) : fqn);
                }
            }
        }
        return targets;
    }

    /** Removes block and line comments (keeps string literals so target="..." still parses). */
    static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        boolean inString = false;
        boolean inChar = false;
        while (i < n) {
            char c = src.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(src.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                i++;
            } else if (inChar) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(src.charAt(++i));
                } else if (c == '\'') {
                    inChar = false;
                }
                i++;
            } else if (c == '"') {
                inString = true;
                out.append(c);
                i++;
            } else if (c == '\'') {
                inChar = true;
                out.append(c);
                i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean containsToken(String src, String token) {
        int idx = src.indexOf(token);
        while (idx >= 0) {
            int after = idx + token.length();
            boolean boundary = after >= src.length() || !Character.isJavaIdentifierPart(src.charAt(after));
            if (boundary) {
                return true;
            }
            idx = src.indexOf(token, after);
        }
        return false;
    }

    // --- self-tests of the audit's own extraction logic (so the enforcer is itself trusted) ---

    @Test
    void extractsSingleAndMultipleClassTargets() {
        assertTrue(extractMixinTargets("@Mixin(Minecraft.class)").contains("Minecraft"));
        Set<String> multi = extractMixinTargets("@Mixin({Foo.class, Bar.class})");
        assertTrue(multi.contains("Foo") && multi.contains("Bar"));
    }

    @Test
    void extractsStringTargets() {
        Set<String> t = extractMixinTargets("@Mixin(targets = \"net.minecraft.client.Thing\")");
        assertTrue(t.contains("Thing"));
    }

    @Test
    void tokenMatchIsWordBounded() {
        // A class named "@OverwriteHelperThing" must not trip the @Overwrite check.
        assertFalse(containsToken("@OverwriteHelper x", "@Overwrite"));
        assertTrue(containsToken("@Overwrite\npublic void x", "@Overwrite"));
    }

    @Test
    void stripCommentsRemovesCommentsButKeepsStrings() {
        assertFalse(containsToken(stripComments("// uses @Overwrite here\ncode"), "@Overwrite"));
        assertFalse(containsToken(stripComments("/* never @Redirect */ code"), "@Redirect"));
        // A targets="..." string survives stripping so it can still be parsed.
        String kept = stripComments("@Mixin(targets = \"net.foo.Bar\") /* @Overwrite mention */");
        assertTrue(kept.contains("net.foo.Bar"));
        assertFalse(containsToken(kept, "@Overwrite"));
        assertTrue(extractMixinTargets(kept).contains("Bar"));
    }

    @Test
    void regionsFromJsonParsesArrays() {
        SodiumRegions r = SodiumRegions.fromJson(new StringReader(
                "{\"forbidden\":[\"LevelRenderer\"],\"injectable_overlap\":[\"Minecraft\"]}"));
        assertTrue(r.isForbidden("LevelRenderer"));
        assertTrue(r.isInjectableOverlap("Minecraft"));
        assertFalse(r.isForbidden("Minecraft"));
    }
}
