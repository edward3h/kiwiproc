/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testmicronaut5;

import org.jspecify.annotations.Nullable;

public record Owner(
        int id, @Nullable String firstName, @Nullable String lastName) {}
