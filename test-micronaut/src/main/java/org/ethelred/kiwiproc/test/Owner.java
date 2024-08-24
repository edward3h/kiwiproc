package org.ethelred.kiwiproc.test;

import org.jspecify.annotations.Nullable;

public record Owner(
        int id,
        @Nullable String first_name,
        @Nullable String last_name) { // TODO should be able to convert names to camel case
}
