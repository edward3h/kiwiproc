package org.ethelred.kiwiproc.processor;

import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import io.zonky.test.db.postgres.embedded.LiquibasePreparer;
import io.zonky.test.db.postgres.junit5.EmbeddedPostgresExtension;
import io.zonky.test.db.postgres.junit5.PreparedDbExtension;
import net.serverpeon.testing.compile.CompilationExtension;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;
import org.ethelred.kiwiproc.processorconfig.ProcessorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.stream.Stream;

import static com.google.testing.compile.CompilationSubject.assertThat;

@ExtendWith(CompilationExtension.class)
public class ProcessorTest {
    @RegisterExtension
    public static PreparedDbExtension pg = EmbeddedPostgresExtension
            .preparedDatabase(LiquibasePreparer.forClasspathLocation("changelog.xml"));

    public static JsonType<ProcessorConfig> processorConfigType = Jsonb.builder().build().type(ProcessorConfig.class);

    @Test
    void whenNoConfigurationFileFailCompilation() {
        var compilation = Compiler.javac()
                .withProcessors(new KiwiProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.MyDAO",
                        // language=java
                        """
                                package com.example;
                                
                                public interface MyDAO {
                                }
                                """));
        assertThat(compilation).hadErrorContaining("No config file specified");
    }

    @Test
    void whenConfigurationFileIsMissingFailCompilation() {
        var compilation = Compiler.javac()
                .withProcessors(new KiwiProcessor())
                .withOptions("-Aorg.ethelred.kiwiproc.configuration=bogus.json")
                .compile(JavaFileObjects.forSourceString("com.example.MyDAO",
                        // language=java
                        """
                                package com.example;
                                
                                public interface MyDAO {
                                }
                                """));
        assertThat(compilation).hadErrorContaining("Config file 'bogus.json' not found");
    }

    @Test
    void whenConfigurationFileIsInvalidFailCompilation() throws IOException {
        var config = Files.createTempFile("config", ".json");
        Files.writeString(config, """
                {}
                """);
        var compilation = Compiler.javac()
                .withProcessors(new KiwiProcessor())
                .withOptions("-Aorg.ethelred.kiwiproc.configuration=%s".formatted(config))
                .compile(
                        JavaFileObjects.forSourceString("com.example.MyDAO",
                        // language=java
                        """
                                package com.example;
                                
                                import org.ethelred.kiwiproc.annotation.DAO;
                                
                                @DAO
                                public interface MyDAO {
                                }
                                """));
        assertThat(compilation).hadErrorContaining("No datasources in config file");
    }

    Compiler configuredCompiler() throws IOException {
        var ci = pg.getConnectionInfo();
        var dataSourceConfig = new DataSourceConfig("default", "jdbc:postgresql://localhost:%d/%s?user=%s".formatted(ci.getPort(), ci.getDbName(), ci.getUser()), ci.getDbName(), ci.getUser(), "postgres", "org.postgresql.Driver");
        var processorConfig = new ProcessorConfig(Map.of("default", dataSourceConfig), DependencyInjectionStyle.JAKARTA);
        var configFile = Files.createTempFile("config", ".json");
        Files.writeString(configFile, processorConfigType.toJson(processorConfig));

        return Compiler.javac()
                .withProcessors(new KiwiProcessor())
                .withOptions("-Aorg.ethelred.kiwiproc.configuration=%s".formatted(configFile));
    }

    @Test
    void whenDAOInterfaceHasNoQueryMethodsFailCompilation() throws IOException {
        var compilation = configuredCompiler()
                .compile(
                        JavaFileObjects.forSourceString("com.example.MyDAO",
                                // language=java
                                """
                                        package com.example;
                                        
                                        import org.ethelred.kiwiproc.annotation.DAO;
                                        
                                        @DAO
                                        public interface MyDAO {
                                        }
                                        """));
        assertThat(compilation).hadErrorContaining("No valid Sql or default methods found.");
    }

    @Test
    void whenDAOInterfaceHasAQueryMethodAnImplementationIsGenerated() throws IOException {
        var compilation = configuredCompiler()
                .compile(
                        JavaFileObjects.forSourceString("com.example.Restaurant",
                                // language=java
                                """
                                package com.example;
                                
                                public record Restaurant(int id, String name, int tables, String chain){}
                                """),
                        JavaFileObjects.forSourceString("com.example.MyDAO",
                                // language=java
                                """
                                        package com.example;
                                        
                                        import java.util.List;
                                        import org.ethelred.kiwiproc.annotation.DAO;
                                        import org.ethelred.kiwiproc.annotation.SqlQuery;
                                        
                                        @DAO
                                        public interface MyDAO {
                                            @SqlQuery(sql = "SELECT * FROM restaurant WHERE name like :search || '%'")
                                            List<Restaurant> findRestaurantsByName(String search);
                                        }
                                        """));
        Stream.of("$MyDAO$Impl", "$MyDAO$Mapper", "$MyDAO$Provider", "$MyDAO$findRestaurantsByName$Row").forEach(className ->
        assertThat(compilation).generatedSourceFile("com.example." + className)
        );
        compilation.generatedSourceFiles().forEach(f -> {
            System.err.println(f.getName());
            try {
                f.openInputStream().transferTo(System.err);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void anArbitraryClassIsNotASupportedReturnType() throws IOException {
        var compilation = configuredCompiler()
                .compile(
                        JavaFileObjects.forSourceString("com.example.Restaurant",
                                // language=java
                                """
                                package com.example;
                                
                                public class Restaurant {
                                    public Restaurant(int id, String name, int tables, String chain) {
                                    }
                                }
                                """),
                        JavaFileObjects.forSourceString("com.example.MyDAO",
                                // language=java
                                """
                                        package com.example;
                                        
                                        import java.util.List;
                                        import org.ethelred.kiwiproc.annotation.DAO;
                                        import org.ethelred.kiwiproc.annotation.SqlQuery;
                                        
                                        @DAO
                                        public interface MyDAO {
                                            @SqlQuery(sql = "SELECT * FROM restaurant WHERE name like :search || '%'")
                                            List<Restaurant> findRestaurantsByName(String search);
                                        }
                                        """));
        assertThat(compilation).hadErrorContaining("Invalid return type");
        assertThat(compilation).hadErrorCount(2);
    }
}
