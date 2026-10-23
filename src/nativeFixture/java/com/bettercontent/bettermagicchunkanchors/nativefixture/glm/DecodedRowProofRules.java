package com.bettercontent.bettermagicchunkanchors.nativefixture.glm;

import com.google.gson.JsonElement;

/** Pure predicates used by the real RETURN observer; tests do not initialize Minecraft or decode loot. */
public final class DecodedRowProofRules {
    private DecodedRowProofRules() {}

    /** Only a genuine success-without-error Pair may supply a transient value. Partial-with-body is NOT success. */
    public static Object successfulPairValue(com.mojang.serialization.DataResult<?> result) {
        if (result == null || result.error().isPresent()) return null;
        Object value = result.result().orElse(null);
        return value instanceof com.mojang.datafixers.util.Pair<?, ?> pair ? pair.getFirst() : null;
    }

    /** Codec.parse has already projected the successful decode Pair to its body. */
    public static Object successfulParseValue(com.mojang.serialization.DataResult<?> result) {
        return result == null || result.error().isPresent() ? null : result.result().orElse(null);
    }

    public static boolean existingRootReceiver(Object receiver, Object publicDirectConstant) {
        return receiver != null && receiver == publicDirectConstant;
    }

    public static boolean exactBody(Object body, Class<?> pinnedImplementation) {
        return body != null && body.getClass() == pinnedImplementation;
    }

    public static void inputAndImplementation(String expectedType, String expectedImplementation,
            String actualImplementation, JsonElement actualApplyInput, JsonElement expectedEffectiveInput) {
        if (!expectedImplementation.equals(actualImplementation))
            throw new IllegalStateException("Actual loaded implementation/order differs");
        if (actualApplyInput == null || !actualApplyInput.equals(expectedEffectiveInput)
                || !actualApplyInput.isJsonObject()
                || !actualApplyInput.getAsJsonObject().has("type")
                || !expectedType.equals(actualApplyInput.getAsJsonObject().get("type").getAsString()))
            throw new IllegalStateException("Original actual apply input type/body differs");
    }

    public static void arsRegistration(String type, Object registered, Object publicRegistryObjectValue,
            String registeredKey) {
        if (!"ars_nouveau:dungeon_loot".equals(type) || !type.equals(registeredKey)
                || registered == null || registered != publicRegistryObjectValue)
            throw new IllegalStateException("Ars actual public registered decoder/key differs");
    }
}
