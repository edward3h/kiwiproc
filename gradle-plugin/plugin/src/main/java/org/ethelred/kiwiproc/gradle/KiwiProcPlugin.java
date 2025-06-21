/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.jvm.tasks.ProcessResources;

@SuppressWarnings("unused")
public class KiwiProcPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");

        project.getGradle()
                .getSharedServices()
                .registerIfAbsent(EmbeddedPostgresService.DEFAULT_NAME, EmbeddedPostgresService.class);

        var version = loadVersion();

        var extension = project.getExtensions().create("kiwiProc", KiwiProcExtension.class);
        extension.getDebug().convention(false);
        extension.getDependencyInjectionStyle().convention(DependencyInjectionStyle.JAKARTA);
        extension
                .getLiquibaseChangelog()
                .convention(project.getLayout().getProjectDirectory().file("src/main/resources/changelog.xml"));
        extension.getAddDependencies().convention(true);

        var configurations = project.getConfigurations();
        configurations.named(
                "annotationProcessor",
                conf -> conditionallyAddDependency(
                        conf,
                        extension.getAddDependencies(),
                        project.getDependencies(),
                        "org.ethelred.kiwiproc:processor:" + version));
        configurations.named(
                "implementation",
                conf -> conditionallyAddDependency(
                        conf,
                        extension.getAddDependencies(),
                        project.getDependencies(),
                        "org.ethelred.kiwiproc:runtime:" + version));

        var processorConfigTask = project.getTasks().register("processorConfig", KiwiProcConfigTask.class, task -> {
            task.getConfigFile().set(project.getLayout().getBuildDirectory().file("processorConfig/config.json"));
            task.getApplicationPropertiesFile()
                    .convention(project.getLayout()
                            .getBuildDirectory()
                            .file("processorConfig/application-test.properties"));
            task.getDependencyInjectionStyle().convention(extension.getDependencyInjectionStyle());
            task.getDebug().convention(extension.getDebug());
            task.setExtension(extension);
        });

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.dependsOn(processorConfigTask);
            task.getOptions()
                    .getCompilerArgumentProviders()
                    .add(() -> List.of(processorConfigTask
                            .map(pct -> "-Aorg.ethelred.kiwiproc.configuration=%s"
                                    .formatted(pct.getConfigFile().get().getAsFile()))
                            .get()));
        });

        project.getTasks().named("processTestResources", ProcessResources.class, task -> {
           task.dependsOn(processorConfigTask);
           task.from(processorConfigTask.map(KiwiProcConfigTask::getApplicationPropertiesFile));
        });
    }

    private void conditionallyAddDependency(
            Configuration configuration,
            Property<Boolean> addDependencies,
            DependencyHandler dependencyHandler,
            String dependency) {
        configuration.getDependencies().addAllLater(addDependencies.map(doAdd -> {
            if (doAdd) {
                return List.of(dependencyHandler.create(dependency));
            } else {
                return List.of();
            }
        }));
    }

    private String loadVersion() {
        try (var resourceAsStream = getClass().getResourceAsStream("/VERSION")) {
            return new String(resourceAsStream.readAllBytes()).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
