/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.testspring;

import java.util.List;

public record OwnerPets(String ownerFirstName, List<String> petNames) {}
