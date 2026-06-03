/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testmicronaut5;

import org.jspecify.annotations.Nullable;

public record PetType(int id, @Nullable String name) {}
