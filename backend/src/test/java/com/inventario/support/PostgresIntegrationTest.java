package com.inventario.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de las pruebas de integración: un PostgreSQL 16 desechable (Testcontainers)
 * creado con los mismos scripts de Python que el resto de entornos
 * ({@code python tools/cli.py crear-bd}). Se comparte durante toda la ejecución
 * y se descarta al terminar. Sin Docker, las pruebas se omiten.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

    private static final DockerImageName IMAGEN = DockerImageName.parse("postgres:16-alpine");
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String JWT_SECRET = aleatorio(48);

    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> contenedor = iniciar();
        registry.add("spring.datasource.url", contenedor::getJdbcUrl);
        registry.add("spring.datasource.username", contenedor::getUsername);
        registry.add("spring.datasource.password", contenedor::getPassword);
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    private static synchronized PostgreSQLContainer<?> iniciar() {
        if (postgres == null) {
            PostgreSQLContainer<?> contenedor = new PostgreSQLContainer<>(IMAGEN)
                    .withDatabaseName("inventario_pruebas");
            contenedor.start();
            crearBaseConScripts(contenedor);
            postgres = contenedor;
        }
        return postgres;
    }

    private static void crearBaseConScripts(PostgreSQLContainer<?> contenedor) {
        Path raiz = raizDelRepositorio();
        ProcessBuilder proceso = new ProcessBuilder(
                List.of(python(raiz), "tools/cli.py", "crear-bd"))
                .directory(raiz.toFile())
                .redirectErrorStream(true);
        Map<String, String> entorno = proceso.environment();
        entorno.put("DATABASE_URL", "postgresql://%s:%s@%s:%d/%s".formatted(
                contenedor.getUsername(),
                contenedor.getPassword(),
                contenedor.getHost(),
                contenedor.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                contenedor.getDatabaseName()));
        entorno.put("ADMIN_EMAIL", "admin-pruebas@inventario.invalid");
        entorno.put("ADMIN_PASSWORD", aleatorio(24));
        entorno.put("PYTHONIOENCODING", "utf-8");
        try {
            Process ejecucion = proceso.start();
            String salida = new String(ejecucion.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!ejecucion.waitFor(5, TimeUnit.MINUTES) || ejecucion.exitValue() != 0) {
                throw new IllegalStateException("crear-bd falló sobre el contenedor de pruebas:\n" + salida);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No se pudo ejecutar Python para crear la base de pruebas. "
                            + "Defina PYTHON o cree el entorno .venv del proyecto.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Creación de la base de pruebas interrumpida.", exception);
        }
    }

    /** Usa PYTHON si está definida; si no, el entorno virtual del proyecto o el del sistema. */
    private static String python(Path raiz) {
        String definido = System.getenv("PYTHON");
        if (definido != null && !definido.isBlank()) {
            return definido;
        }
        List<Path> candidatos = new ArrayList<>(List.of(
                raiz.resolve(".venv/Scripts/python.exe"),
                raiz.resolve(".venv/bin/python")));
        return candidatos.stream()
                .filter(Files::isExecutable)
                .map(Path::toString)
                .findFirst()
                .orElse("python3");
    }

    private static Path raizDelRepositorio() {
        Path actual = Paths.get("").toAbsolutePath();
        while (actual != null && !Files.exists(actual.resolve("tools/cli.py"))) {
            actual = actual.getParent();
        }
        if (actual == null) {
            throw new IllegalStateException("No se encontró tools/cli.py desde el directorio de trabajo.");
        }
        return actual;
    }

    private static String aleatorio(int bytes) {
        byte[] valor = new byte[bytes];
        ALEATORIO.nextBytes(valor);
        return Base64.getEncoder().encodeToString(valor);
    }
}
