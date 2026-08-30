package pawg;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChildJvmLauncherTest {
    @Test
    void gpuChildHasOneValidatedSelectorAndKeepsSpaceContainingTokensIntact() {
        List<String> command = ChildJvmLauncher.buildCommand(
                SimulationTarget.GRAVITY_GPU,
                "12:34",
                Path.of("C:/Program Files/Java/bin/java.exe"),
                List.of("-Dtornado.enable.profiling=True", "--module-path", "C:/Program Files/JavaFX/lib",
                        "-Dtornado.device.selector.inherited=0:0", "-agentlib:jdwp=ignored"),
                "C:/classes with spaces",
                Map.of());

        assertTrue(command.getFirst().endsWith("Program Files\\Java\\bin\\java.exe"));
        assertEquals(1, command.stream().filter(value -> value.startsWith("-D" + ChildJvmLauncher.DEVICE_PROPERTY + "=")).count());
        assertTrue(command.contains("-D" + ChildJvmLauncher.DEVICE_PROPERTY + "=12:34"));
        assertEquals(List.of("--module-path", "C:/Program Files/JavaFX/lib"),
                command.subList(command.indexOf("--module-path"), command.indexOf("--module-path") + 2));
        assertEquals("C:/classes with spaces", command.get(command.indexOf("-cp") + 1));
        assertEquals("7", command.getLast());
        assertFalse(command.stream().anyMatch(value -> value.startsWith("-agentlib:")));
    }

    @Test
    void cpuChildDoesNotReceiveDeviceSelectorAndGameOfLifeUsesLauncherSentinel() {
        List<String> cpu = ChildJvmLauncher.buildCommand(SimulationTarget.GRAVITY_CPU, null, Path.of("java"),
                List.of("-Dtornado.foo=bar"), "classes", Map.of());
        List<String> gameOfLife = ChildJvmLauncher.buildCommand(SimulationTarget.GAME_OF_LIFE, "1:2", Path.of("java"),
                List.of(), "classes", Map.of());

        assertFalse(cpu.stream().anyMatch(value -> value.startsWith("-D" + ChildJvmLauncher.DEVICE_PROPERTY + "=")));
        assertEquals("6", cpu.getLast());
        assertTrue(gameOfLife.stream().anyMatch(value -> value.equals("-D" + ChildJvmLauncher.DEVICE_PROPERTY + "=1:2")));
        assertEquals("launcher-game-of-life", gameOfLife.getLast());
    }

    @Test
    void curatedArgumentsRejectDebuggingAndIncompletePairedOptions() {
        List<String> curated = ChildJvmLauncher.curatedJvmArguments(List.of(
                "-Dtornado.foo=bar", "-agentpath:profiler", "-Xrunjdwp:transport=dt_socket", "-Xdebug",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--module-path", "", "-javaagent:C:/outside.jar"), Map.of());

        assertEquals(List.of("-Dtornado.foo=bar", "--add-opens", "java.base/java.lang=ALL-UNNAMED"), curated);
    }

    @Test
    void invalidGpuCoordinatesAreRejectedBeforeAChildCanStart() {
        assertThrows(IllegalArgumentException.class, () -> ChildJvmLauncher.validatedDevice(null));
        for (String invalid : List.of("", "-1:0", "1:-1", "1", "1:2:3", "a:2", "999999999999:0")) {
            assertThrows(IllegalArgumentException.class, () -> ChildJvmLauncher.validatedDevice(invalid));
        }
        assertEquals("0:12", ChildJvmLauncher.validatedDevice("0:12"));
    }
}
