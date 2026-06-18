package xyz.atomland.atomized.compat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The Sodium "do-not-touch" map (master plan §7), generated from Sodium's own mixin
 * configs across every supported Minecraft version (see {@code tools/}). It is the data
 * backing Atomized's "never conflicts with Sodium" guarantee:
 *
 * <ul>
 *   <li>{@link #forbidden()} — classes Sodium owns/rewrites (terrain, vertex/buffer, models,
 *       textures, fog/cloud, sky, render pipeline). Atomized must <strong>never</strong>
 *       {@code @Mixin} these.</li>
 *   <li>{@link #injectableOverlap()} — classes Sodium also hooks but only at lifecycle/event
 *       points. Atomized may target these <strong>only with chainable MixinExtras injectors</strong>
 *       (never {@code @Overwrite}/{@code @Redirect}).</li>
 * </ul>
 *
 * <p>Any class in neither set is untouched by Sodium and free to use (with chainable injectors).
 * The {@code MixinAuditTest} enforces this against Atomized's own mixins.
 */
public final class SodiumRegions {
    public static final String RESOURCE = "/atomized_sodium_regions.json";

    private final Set<String> forbidden;
    private final Set<String> injectableOverlap;

    private SodiumRegions(Set<String> forbidden, Set<String> injectableOverlap) {
        this.forbidden = Set.copyOf(forbidden);
        this.injectableOverlap = Set.copyOf(injectableOverlap);
    }

    public static SodiumRegions load() {
        try (InputStream in = SodiumRegions.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled resource " + RESOURCE);
            }
            return fromJson(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load Sodium regions map", e);
        }
    }

    public static SodiumRegions fromJson(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        return new SodiumRegions(readArray(root, "forbidden"), readArray(root, "injectable_overlap"));
    }

    private static Set<String> readArray(JsonObject root, String key) {
        Set<String> out = new LinkedHashSet<>();
        JsonArray array = root.getAsJsonArray(key);
        if (array != null) {
            for (JsonElement element : array) {
                out.add(element.getAsString());
            }
        }
        return out;
    }

    public Set<String> forbidden() {
        return forbidden;
    }

    public Set<String> injectableOverlap() {
        return injectableOverlap;
    }

    /** Whether Sodium rewrites this class (by simple class name) and Atomized must avoid it. */
    public boolean isForbidden(String simpleClassName) {
        return forbidden.contains(simpleClassName);
    }

    /** Whether Sodium also hooks this class but chainable injection is allowed. */
    public boolean isInjectableOverlap(String simpleClassName) {
        return injectableOverlap.contains(simpleClassName);
    }
}
