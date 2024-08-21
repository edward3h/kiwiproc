package org.ethelred.kiwiproc.test;

import org.jspecify.annotations.Nullable;

public record PetType(int id, @Nullable String name) {}
