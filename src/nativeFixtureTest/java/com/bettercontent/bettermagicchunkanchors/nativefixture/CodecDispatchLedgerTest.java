package com.bettercontent.bettermagicchunkanchors.nativefixture;

import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.CodecDispatchLedger;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

public final class CodecDispatchLedgerTest {
    private static final String HASH = "0".repeat(64);
    private static List<String> ids(int size) { return IntStream.range(0, size).mapToObj(i -> "test:row_" + i).toList(); }
    private static CodecDispatchLedger complete(int size, String fault) {
        var ledger = new CodecDispatchLedger(); ledger.begin(ids(size));
        for (int i = 0; i < size; i++) {
            ledger.before("test:row_" + i, "test:codec", HASH);
            boolean first = i == 0;
            if (!(first && fault.equals("missing-selection")))
                ledger.selected(!(first && fault.equals("wrong-selection")), !(first && fault.equals("partial-selection")), "test:codec");
            if (first && fault.equals("duplicate-selection")) ledger.selected(true, true, "test:codec");
            if (!(first && fault.equals("missing-root")))
                ledger.returned(!(first && fault.equals("wrong-root")), !(first && fault.equals("partial-root")),
                    first && fault.equals("changed-input") ? "1".repeat(64) : HASH, "example.Body");
            ledger.after("test:row_" + i);
        }
        return ledger;
    }
    @Test public void everyOriginalRowMustProveSelectionInputAndBodyForBothDeclaredScopes() {
        for (int size : List.of(16, 72)) {
            var ledger = complete(size, "none");
            for (int i = 0; i < size; i++) ledger.requireLoaded(i, "test:row_" + i, "test:codec", HASH, "example.Body");
            ledger.end(); assertFalse(ledger.active());
        }
    }
    @Test public void missingDuplicateWrongPartialOrChangedOriginalWitnessCannotQualify() {
        for (String fault : List.of("missing-selection", "wrong-selection", "partial-selection", "duplicate-selection",
                "missing-root", "wrong-root", "partial-root", "changed-input")) {
            var ledger = complete(16, fault);
            assertThrows(IllegalStateException.class, () -> ledger.requireLoaded(0, "test:row_0", "test:codec", HASH, "example.Body"), fault);
            ledger.end();
        }
    }
    @Test public void loadedBodyOrdinalKeyAndCanonicalInputMustMatchOriginalWitness() {
        var ledger = complete(16, "none");
        assertThrows(IllegalStateException.class, () -> ledger.requireLoaded(0, "test:row_1", "test:codec", HASH, "example.Body"));
        assertThrows(IllegalStateException.class, () -> ledger.requireLoaded(0, "test:row_0", "test:wrong", HASH, "example.Body"));
        assertThrows(IllegalStateException.class, () -> ledger.requireLoaded(0, "test:row_0", "test:codec", "1".repeat(64), "example.Body"));
        assertThrows(IllegalStateException.class, () -> ledger.requireLoaded(0, "test:row_0", "test:codec", HASH, "wrong.Body"));
        ledger.end();
    }
    @Test public void unfinishedReorderedOverlappingAndOverflowAppliesFailClosed() {
        var missing = new CodecDispatchLedger(); missing.begin(ids(16));
        assertThrows(IllegalStateException.class, () -> missing.requireLoaded(0, "test:row_0", "test:codec", HASH, "example.Body"));
        var order = new CodecDispatchLedger(); order.begin(ids(16)); order.before("test:row_1", "test:codec", HASH);
        assertThrows(IllegalStateException.class, () -> order.requireLoaded(0, "test:row_0", "test:codec", HASH, "example.Body"));
        var overlap = complete(16, "none"); overlap.begin(ids(16));
        assertThrows(IllegalStateException.class, () -> overlap.requireLoaded(0, "test:row_0", "test:codec", HASH, "example.Body"));
        var overflow = new CodecDispatchLedger(); overflow.begin(ids(73));
        assertThrows(IllegalStateException.class, () -> overflow.requireLoaded(0, "test:row_0", "test:codec", HASH, "example.Body"));
    }
}
