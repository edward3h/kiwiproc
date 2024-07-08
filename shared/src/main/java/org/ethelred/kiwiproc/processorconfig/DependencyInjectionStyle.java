package org.ethelred.kiwiproc.processorconfig;

import java.util.List;

public enum DependencyInjectionStyle {
    JAKARTA;

    public List<String> getImports() {
        //TODO
        return List.of(
                "jakarta.inject.Singleton",
                "jakarta.inject.Named"
        );
    }
}
