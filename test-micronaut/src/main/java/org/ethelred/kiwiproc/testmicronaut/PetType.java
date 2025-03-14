/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.testmicronaut;

import org.jspecify.annotations.Nullable;

public record PetType(int id, @Nullable String name) {}
