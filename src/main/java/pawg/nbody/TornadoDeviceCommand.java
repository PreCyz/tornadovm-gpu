package pawg.nbody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TornadoDeviceCommand {
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[;\\d]*[ -/]*[@-~]");
    private static final Pattern DEVICE_LINE_PATTERN = Pattern.compile("^\\s*Tornado device=(\\d+):(\\d+)\\s*(\\(DEFAULT\\))?.*$");
    private static final Pattern DRIVER_LINE_PATTERN = Pattern.compile("^\\s*Driver:\\s*(.+?)\\s*$");
    private static final long DEVICE_COMMAND_TIMEOUT_SECONDS = 8L;

    private TornadoDeviceCommand() {
    }

    static List<TornadoDeviceChoice> detectDevices() {
        try {
            Process process = new ProcessBuilder("tornado", "--devices")
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(DEVICE_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return fallback("Timed out while running: tornado --devices");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return fallback(stripAnsi(output).trim().isEmpty()
                        ? "Command failed: tornado --devices"
                        : stripAnsi(output));
            }
            return parseDevices(output);
        } catch (IOException e) {
            return fallback("Could not run 'tornado --devices': " + e.getMessage());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return fallback("Interrupted while running: tornado --devices");
        }
    }

    static List<TornadoDeviceChoice> parseDevices(String commandOutput) {
        String cleanOutput = stripAnsi(commandOutput).replace("\r\n", "\n").replace('\r', '\n').trim();
        if (cleanOutput.isEmpty()) {
            return fallback("No output from: tornado --devices");
        }

        String[] lines = cleanOutput.split("\n");
        List<TornadoDeviceChoice> devices = new ArrayList<>();
        String currentDriver = "";
        int index = 0;
        while (index < lines.length) {
            String line = lines[index];
            Matcher driverMatcher = DRIVER_LINE_PATTERN.matcher(line);
            if (driverMatcher.matches()) {
                currentDriver = driverMatcher.group(1).trim();
            }

            Matcher deviceMatcher = DEVICE_LINE_PATTERN.matcher(line);
            if (!deviceMatcher.matches()) {
                index++;
                continue;
            }

            int driverIndex = Integer.parseInt(deviceMatcher.group(1));
            int deviceIndex = Integer.parseInt(deviceMatcher.group(2));
            boolean defaultDevice = deviceMatcher.group(3) != null;
            List<String> deviceLines = new ArrayList<>();
            deviceLines.add(line.trim());
            index++;
            while (index < lines.length
                    && !DEVICE_LINE_PATTERN.matcher(lines[index]).matches()
                    && !DRIVER_LINE_PATTERN.matcher(lines[index]).matches()) {
                if (!lines[index].isBlank()) {
                    deviceLines.add(lines[index].stripTrailing());
                }
                index++;
            }

            String title = titleFor(driverIndex, deviceIndex, currentDriver, deviceLines, defaultDevice);
            devices.add(new TornadoDeviceChoice(
                    driverIndex,
                    deviceIndex,
                    title,
                    String.join(System.lineSeparator(), deviceLines),
                    cleanOutput,
                    defaultDevice));
        }

        if (devices.isEmpty()) {
            return fallback(cleanOutput);
        }
        return devices;
    }

    private static String titleFor(int driverIndex, int deviceIndex, String currentDriver, List<String> deviceLines, boolean defaultDevice) {
        String titleDetail = "";
        for (int i = 1; i < deviceLines.size(); i++) {
            String line = deviceLines.get(i).trim();
            if (!line.isEmpty()) {
                titleDetail = line;
                break;
            }
        }
        String driverName = currentDriver == null || currentDriver.isBlank() ? "driver " + driverIndex : currentDriver;
        String suffix = defaultDevice ? " (default)" : "";
        if (titleDetail.isBlank()) {
            return String.format("%s device %d:%d%s", driverName, driverIndex, deviceIndex, suffix);
        }
        return String.format("%s device %d:%d - %s%s", driverName, driverIndex, deviceIndex, titleDetail, suffix);
    }

    private static List<TornadoDeviceChoice> fallback(String message) {
        String info = message == null || message.isBlank() ? "Using Tornado default device 0:0." : message.trim();
        return List.of(new TornadoDeviceChoice(
                0,
                0,
                "Tornado default device 0:0",
                info,
                info,
                true));
    }

    private static String stripAnsi(String value) {
        return ANSI_PATTERN.matcher(value == null ? "" : value).replaceAll("");
    }
}
