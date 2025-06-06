/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.testmicronaut;

import org.jspecify.annotations.Nullable;

public record Owner(int id, @Nullable String firstName, @Nullable String lastName) {}
