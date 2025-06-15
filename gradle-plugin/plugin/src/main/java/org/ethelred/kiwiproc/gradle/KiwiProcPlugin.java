package org.ethelred.kiwiproc.gradle;

import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

public class KiwiProcPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getGradle()
                .getSharedServices()
                .registerIfAbsent(EmbeddedPostgresService.DEFAULT_NAME, EmbeddedPostgresService.class);

        var version = loadVersion();
        var dependencies = project.getDependencies();
        dependencies.add("annotationProcessor", "org.ethelred.kiwiproc:processor:" + version);
        dependencies.add("implementation", "org.ethelred.kiwiproc:runtime:" + version);

        var extension = project.getExtensions().create("kiwiProc", KiwiProcExtension.class);
        extension.getDebug().convention(false);
        extension.getDependencyInjectionStyle().convention(DependencyInjectionStyle.JAKARTA);
        extension.getLiquibaseChangelog().convention(project.getLayout().getProjectDirectory().file("src/main/resources/changelog.xml"));

        var processorConfigTask = project.getTasks().register("processorConfig", KiwiProcConfigTask.class, task -> {
            task.getConfigFile().set(project.getLayout().getBuildDirectory().file("processorConfig/config.json"));
            task.getApplicationPropertiesFile().convention(project.getLayout().getBuildDirectory().file("processorConfig/application-test.properties"));
            task.getDependencyInjectionStyle().convention(extension.getDependencyInjectionStyle());
            task.getDebug().convention(extension.getDebug());
            task.setExtension(extension);
        });

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.dependsOn(processorConfigTask);
            task.getOptions().getCompilerArgumentProviders().add(() -> List.of(processorConfigTask.map(pct -> "-Aorg.ethelred.kiwiproc.configuration=%s".formatted(pct.getConfigFile().get().getAsFile())).get()));
        });
    }

    private String loadVersion() {
        try (var resourceAsStream = getClass().getResourceAsStream("/VERSION"))
        {
            return new String(resourceAsStream.readAllBytes()).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
