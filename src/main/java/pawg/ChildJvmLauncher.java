package pawg;

import pawg.nbody.TornadoDeviceSelector;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Starts one simulation in a fresh JVM while retaining the required TornadoVM runtime options. */
public final class ChildJvmLauncher {
    static final String DEVICE_PROPERTY = TornadoDeviceSelector.INHERITED_DEVICE_PROPERTY;
    private static final String DEVICE_PROPERTY_ARGUMENT_PREFIX = "-D" + DEVICE_PROPERTY + "=";
    private static final List<String> PAIRED_OPTIONS = List.of(
            "--module-path",
            "-p",
            "--upgrade-module-path",
            "--add-modules",
            "--add-reads",
            "--add-opens",
            "--add-exports",
            "--enable-native-access"
    );

    private ChildJvmLauncher() {
    }

    public static Process start(SimulationTarget target, String selectedDevice) throws IOException {
        Objects.requireNonNull(target, "target");
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<String> command = buildCommand(
                target,
                selectedDevice,
                currentJavaExecutable(),
                ManagementFactory.getRuntimeMXBean().getInputArguments(),
                System.getProperty("java.class.path"),
                System.getenv());
        return new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .inheritIO()
                .start();
    }

    static List<String> buildCommand(SimulationTarget target,
                                     String selectedDevice,
                                     Path javaExecutable,
                                     List<String> parentJvmArguments,
                                     String classPath,
                                     Map<String, String> environment) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(javaExecutable, "javaExecutable");
        if (classPath == null || classPath.isBlank()) {
            throw new IllegalArgumentException("The current JVM classpath is empty");
        }

        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toAbsolutePath().normalize().toString());
        command.addAll(curatedJvmArguments(parentJvmArguments, environment));
        if (target.gpuTarget()) {
            command.add(DEVICE_PROPERTY_ARGUMENT_PREFIX + validatedDevice(selectedDevice));
        }
        command.add("-cp");
        command.add(classPath);
        command.add(Launcher.class.getName());
        command.add(target.branch().orElse("launcher-game-of-life"));
        return List.copyOf(command);
    }

    static List<String> curatedJvmArguments(List<String> arguments, Map<String, String> environment) {
        if (arguments == null || arguments.isEmpty()) {
            return List.of();
        }
        List<String> curated = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            if (argument == null || argument.isBlank() || isRejectedArgument(argument)) {
                continue;
            }
            if (PAIRED_OPTIONS.contains(argument)) {
                if (index + 1 < arguments.size()) {
                    String value = arguments.get(++index);
                    if (value != null && !value.isBlank()) {
                        curated.add(argument);
                        curated.add(value);
                    }
                }
                continue;
            }
            if (argument.startsWith("-javaagent:")) {
                if (isAllowedTornadoAgent(argument, environment)) {
                    curated.add(argument);
                }
                continue;
            }
            if (isRequiredRuntimeArgument(argument)) {
                curated.add(argument);
            }
        }
        return List.copyOf(curated);
    }

    private static boolean isRejectedArgument(String argument) {
        String lower = argument.toLowerCase(Locale.ROOT);
        return argument.startsWith(DEVICE_PROPERTY_ARGUMENT_PREFIX)
                || lower.startsWith("-agentlib:")
                || lower.startsWith("-agentpath:")
                || lower.startsWith("-xrunjdwp")
                || lower.equals("-xdebug");
    }

    private static boolean isRequiredRuntimeArgument(String argument) {
        return isTornadoArgumentFile(argument)
                || argument.startsWith("-Dtornado.")
                || argument.startsWith("-Djava.library.path=")
                || argument.startsWith("--module-path=")
                || argument.startsWith("--upgrade-module-path=")
                || argument.startsWith("--add-modules=")
                || argument.startsWith("--add-reads=")
                || argument.startsWith("--add-opens=")
                || argument.startsWith("--add-exports=")
                || argument.startsWith("--enable-native-access=")
                || argument.equals("--enable-preview")
                || argument.equals("-XX:+UnlockExperimentalVMOptions")
                || argument.equals("-XX:+EnableJVMCI")
                || argument.equals("-XX:+UseParallelGC");
    }

    private static boolean isTornadoArgumentFile(String argument) {
        if (!argument.startsWith("@") || argument.length() == 1) {
            return false;
        }
        try {
            Path argumentFile = Path.of(argument.substring(1));
            Path fileName = argumentFile.getFileName();
            return fileName != null && fileName.toString().equalsIgnoreCase("tornado-argfile");
        } catch (RuntimeException _) {
            return false;
        }
    }

    private static boolean isAllowedTornadoAgent(String argument, Map<String, String> environment) {
        Optional<Path> tornadoHome = configuredTornadoHome(environment);
        if (tornadoHome.isEmpty()) {
            return false;
        }
        String agentDefinition = argument.substring("-javaagent:".length());
        int optionsSeparator = agentDefinition.indexOf('=');
        String agentPathText = optionsSeparator < 0 ? agentDefinition : agentDefinition.substring(0, optionsSeparator);
        try {
            Path agentPath = Path.of(agentPathText);
            if (!Files.exists(agentPath) || !Files.exists(tornadoHome.get())) {
                return false;
            }
            return agentPath.toRealPath().startsWith(tornadoHome.get().toRealPath());
        } catch (IOException | RuntimeException _) {
            return false;
        }
    }

    private static Optional<Path> configuredTornadoHome(Map<String, String> environment) {
        if (environment == null) {
            return Optional.empty();
        }
        String configuredHome = environment.get("TORNADOVM_HOME");
        if (configuredHome == null || configuredHome.isBlank()) {
            configuredHome = environment.get("TORNADO_HOME");
        }
        if (configuredHome == null || configuredHome.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(configuredHome).toAbsolutePath().normalize());
        } catch (RuntimeException _) {
            return Optional.empty();
        }
    }

    static String validatedDevice(String selectedDevice) {
        if (selectedDevice == null || !selectedDevice.matches("\\d+:\\d+")) {
            throw new IllegalArgumentException("GPU device must use non-negative driver:device coordinates");
        }
        String[] coordinates = selectedDevice.split(":", -1);
        try {
            int driverIndex = Integer.parseInt(coordinates[0]);
            int deviceIndex = Integer.parseInt(coordinates[1]);
            return driverIndex + ":" + deviceIndex;
        } catch (NumberFormatException outOfRange) {
            throw new IllegalArgumentException("GPU device coordinates are outside the supported integer range", outOfRange);
        }
    }

    static Path currentJavaExecutable() {
        String executableName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executableName);
    }
}
