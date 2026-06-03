/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.testmicronaut5;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record OwnerPets(@Nullable String ownerFirstName, List<String> petNames) {}
