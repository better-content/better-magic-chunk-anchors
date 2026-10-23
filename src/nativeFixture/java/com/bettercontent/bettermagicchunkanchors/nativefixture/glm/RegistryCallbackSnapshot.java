package com.bettercontent.bettermagicchunkanchors.nativefixture.glm;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/** JDK-only, read-only public-view projection. Observations are deliberately NOT atomic. */
public record RegistryCallbackSnapshot(int size, boolean empty, int keys, int visitedKeys,
        long collectionSum, boolean truncated, String entries) {
    public static final int MAX_KEYS = 128;

    public static RegistryCallbackSnapshot read(int size, boolean empty,
            Map<?, ? extends Collection<?>> view, Function<Object, String> keyScalar) {
        int keys = view.size(), visited = 0;
        long sum = 0;
        StringBuilder entries = new StringBuilder();
        var iterator = view.entrySet().iterator();
        while (visited < MAX_KEYS && iterator.hasNext()) {
            var entry = iterator.next();
            int count = entry.getValue().size();
            // Only the first eight names are printed; all <=128 collection sizes are counted.
            if (visited < 8) {
                if (visited != 0) entries.append(',');
                entries.append(scalar(keyScalar.apply(entry.getKey()))).append(':').append(count);
            }
            sum += count;
            visited++;
        }
        return new RegistryCallbackSnapshot(size, empty, keys, visited, sum,
            iterator.hasNext() || keys > visited, entries.toString());
    }

    public boolean asymmetric() {
        return !truncated && (size != collectionSum || empty != (keys == 0));
    }

    public String fields() {
        return "size=" + size + " empty=" + empty + " keys=" + keys + " visited_keys=" + visitedKeys
            + " collection_sum=" + collectionSum + " truncated=" + truncated
            + " asymmetric=" + asymmetric() + " entries=" + entries;
    }

    public static String scalar(String value) {
        if (value == null) return "null";
        return value.substring(0, Math.min(value.length(), 160)).replaceAll("[^A-Za-z0-9_.$:/@,=+\\-]", "_");
    }
}
