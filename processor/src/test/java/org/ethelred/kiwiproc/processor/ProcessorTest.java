/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.ethelred.kiwiproc.processor.ProcessorMethodTestCase.method;

import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import io.zonky.test.db.postgres.embedded.LiquibasePreparer;
import io.zonky.test.db.postgres.junit5.EmbeddedPostgresExtension;
import io.zonky.test.db.postgres.junit5.PreparedDbExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.stream.Stream;
import net.serverpeon.testing.compile.CompilationExtension;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;
import org.ethelred.kiwiproc.processorconfig.ProcessorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(CompilationExtension.class)
public class ProcessorTest {
    @RegisterExtension
    public static PreparedDbExtension pg =
            EmbeddedPostgresExtension.preparedDatabase(LiquibasePreparer.forClasspathLocation("changelog.xml"));

    public static JsonType<ProcessorConfig> processorConfigType =
            Jsonb.builder().build().type(ProcessorConfig.class);

    @Test
    void whenNoConfigurationFileFailCompilation() {
        var compilation = Compiler.javac()
                .withProcessors(new KiwiProcessor())
                .compile(
                        JavaFileObjects.forSourceString(
                                "com.example.MyDAO",
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
                .compile(
                        JavaFileObjects.forSourceString(
                                "com.example.MyDAO",
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
                        JavaFileObjects.forSourceString(
                                "com.example.MyDAO",
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
        var dataSourceConfig = new DataSourceConfig(
                "default",
                "jdbc:postgresql://localhost:%d/%s?user=%s".formatted(ci.getPort(), ci.getDbName(), ci.getUser()),
                ci.getDbName(),
                ci.getUser(),
                "postgres",
                "org.postgresql.Driver");
        var processorConfig =
                new ProcessorConfig(Map.of("default", dataSourceConfig), DependencyInjectionStyle.JAKARTA);
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
                        JavaFileObjects.forSourceString(
                                "com.example.MyDAO",
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
                        JavaFileObjects.forSourceString(
                                "com.example.Restaurant",
                                // language=java
                                """
                                package com.example;
                                import org.jspecify.annotations.Nullable;
                                public record Restaurant(int id, String name, Integer tables, @Nullable String chain){}
                                """),
                        JavaFileObjects.forSourceString(
                                "com.example.MyDAO",
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
        Stream.of("$MyDAO$Impl", "$MyDAO$Provider")
                .forEach(className -> assertThat(compilation).generatedSourceFile("com.example." + className));
        compilation.generatedSourceFiles().forEach(f -> {
            System.err.println(f.getName());
            try {
                f.openInputStream().transferTo(System.err);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @ParameterizedTest
    @MethodSource
    void testMethods(ProcessorMethodTestCase testCase) throws IOException {
        var compilation = configuredCompiler().compile(testCase.toFileObjects());
        if (testCase.expectedErrorCount == 0) {
            assertThat(compilation).succeeded();
        }
        assertThat(compilation).hadErrorCount(testCase.expectedErrorCount);
        for (var message : testCase.expectedErrorMessages) {
            assertThat(compilation).hadErrorContaining(message);
        }
    }

    static Stream<Arguments> testMethods() {
        return testCases().map(Arguments::of);
    }

    static Stream<ProcessorMethodTestCase> testCases() {
        return Stream.of(
                method(
                                """
                        @SqlBatch(sql = "INSERT INTO restaurant(name) VALUES (:name)")
                        List<Integer> addRestaurants(String name);
                        """)
                        .withDisplayName(
                                "A SqlBatch method fails when it does not have at least one 'iterable' parameter.")
                        .withExpectedErrorMessage("at least one iterable parameter")
                        .withExpectedErrorCount(2),
                method(
                                """
                        @SqlBatch(sql = "INSERT INTO restaurant(name) VALUES (:name)")
                        List<Integer> addRestaurants(List<String> name);
                        """)
                        .withDisplayName("A SqlBatch method compiles when it has an 'iterable' parameter.")
                        .succeeds(),
                method(
                                """
                        @SqlBatch(sql = "INSERT INTO restaurant(name) VALUES (:name)")
                        List<Integer> addRestaurants(String[] name);
                        """)
                        .withDisplayName("A SqlBatch method compiles when it has an array parameter.")
                        .succeeds(),
                method(
                                """
                        @SqlBatch(sql = "INSERT INTO restaurant(name) VALUES (:name)")
                        void addRestaurants(List<String> name);
                        """)
                        .withDisplayName("A SqlBatch method compiles when it has a void return type.")
                        .succeeds(),
                method(
                                """
                        @SqlBatch(sql = "INSERT INTO restaurant(name, tables) VALUES (:name, :tables)")
                        void addRestaurants(List<String> name, int tables);
                        """)
                        .withDisplayName(
                                "A SqlBatch method compiles when it has an iterable parameter and a not iterable parameter")
                        .succeeds(),
                method(
                                """
                @SqlBatch(sql = "INSERT INTO restaurant(name, tables, chain) VALUES (:name, :tables, :chain)")
                void addRestaurants(List<String> name, int[] tables, String chain);
                """)
                        .withDisplayName(
                                "A SqlBatch method compiles when it has multiple iterable parameters and a not iterable parameter")
                        .succeeds(),
                method(
                                """
                record RestaurantUpdate(String name, int tables, String chain) {}
                @SqlBatch(sql = "INSERT INTO restaurant(name, tables, chain) VALUES (:name, :tables, :chain)")
                void addRestaurants(List<RestaurantUpdate> updates);
                """)
                        .withDisplayName("A SqlBatch method compiles when it has an iterable record type")
                        .succeeds(),
                method(
                                """
                        @SqlBatch(sql = "INSERT INTO restaurant(name) VALUES (:name)")
                        int[] addRestaurants(List<String> name);
                        """)
                        .withDisplayName("A SqlBatch method compiles when it has an int array return type.")
                        .succeeds(),
                method(
                                """
                        @SqlQuery(sql = "SELECT * FROM restaurant WHERE name like :search || '%'")
                        List<Restaurant> findRestaurantsByName(String search);
                        """)
                        .withAdditionalSource(
                                "com.example.Restaurant",
                                """
                        package com.example;

                        public class Restaurant {
                            public Restaurant(int id, String name, int tables, String chain) {
                            }
                        }
                        """)
                        .withDisplayName("A SqlQuery method fails when the return type uses an arbitrary class.")
                        .withExpectedErrorCount(3)
                        .withExpectedErrorMessage("Unsupported return type")
                        .withExpectedErrorMessage("Invalid return type"),
                method(
                                """
                        @SqlQuery(sql = "SELECT * FROM restaurant WHERE name like :search || '%'")
                        List<Restaurant> findRestaurantsByName(String search);
                        """)
                        .withAdditionalSource(
                                "com.example.Restaurant",
                                """
                                package com.example;

                                import org.jspecify.annotations.Nullable;
                                public record Restaurant(int id, String name, Integer tables, @Nullable String chain) {}
                                """)
                        .withDisplayName("A SqlQuery method compiles when the return type uses a record.")
                        .succeeds(),
                method(
                                """
                        @SqlQuery(sql = "SELECT name FROM restaurant", fetchSize = 100)
                        List<String> getRestaurantNames();
                        """)
                        .withDisplayName("A SqlQuery method with fetchSize compiles successfully.")
                        .succeeds(),
                method(
                                """
                        @SqlQuery(sql = "SELECT name, tables FROM restaurant", keyColumn = "name", valueColumn = "tables")
                        SortedMap<String, Integer> tablesByRestaurantName();
                        """)
                        .withDisplayName("A SqlQuery method with SortedMap return type compiles successfully.")
                        .succeeds());
    }
}
