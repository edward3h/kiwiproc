package org.ethelred.buildsupport;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.List;

public abstract class EmbeddedPostgresPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getGradle().getSharedServices().registerIfAbsent("embeddedPostgres", EmbeddedPostgresService.class);

        var processorConfigTask = project.getTasks().register("processorConfig", ProcessorConfigTask.class, task -> {
            task.getConfigFile().set(project.getLayout().getBuildDirectory().file("config.json"));
            task.getDependencyInjectionStyle().convention("JAKARTA");
            task.getLiquibaseChangelog().convention(project.getLayout().getProjectDirectory().file("src/main/resources/changelog.xml"));
        });

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.dependsOn(processorConfigTask);
            task.getOptions().getCompilerArgumentProviders().add(() -> List.of(processorConfigTask.map(pct -> "-Aorg.ethelred.kiwiproc.configuration=%s".formatted(pct.getConfigFile().get().getAsFile())).get()));
        });
    }
}
