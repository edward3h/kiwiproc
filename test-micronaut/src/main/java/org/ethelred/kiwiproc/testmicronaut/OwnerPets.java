/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.testmicronaut;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record OwnerPets(@Nullable String ownerFirstName, List<String> petNames) {}
