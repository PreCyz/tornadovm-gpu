package pawg.nbody;

import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.SearchableComboBox;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TornadoDeviceSelector {
    private static final String AUTO_DEFAULT_DEVICE_PROPERTY = "tornado.device.selector.default";
    /**
     * A launcher-provided TornadoVM device identifier in the strict {@code driver:device} form.
     */
    public static final String INHERITED_DEVICE_PROPERTY = "tornado.device.selector.inherited";
    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("^(0|[1-9]\\d*):(0|[1-9]\\d*)$");

    public static TornadoDevice selectDevice(Stage owner) {
        List<TornadoDeviceChoice> devices = deviceChoices();
        Optional<TornadoDeviceChoice> inheritedChoice = inheritedDeviceChoice(devices);
        if (hasInheritedDeviceProperty()) {
            TornadoDeviceChoice choice = inheritedChoice.orElseGet(() -> defaultChoice(devices));
            return resolveDevice(owner, choice, false);
        }
        TornadoDeviceChoice choice = Boolean.getBoolean(AUTO_DEFAULT_DEVICE_PROPERTY)
                ? initialDeviceChoice(devices)
                : new TornadoDeviceSelector().showAndWait(owner);
        return resolveDevice(owner, choice);
    }

    public static TornadoExecutionPlan applyDevice(TornadoExecutionPlan plan, TornadoDevice device) {
        if (device == null) {
            return plan;
        }
        return plan.withDevice(device);
    }

    public static List<TornadoDeviceChoice> deviceChoices() {
        return TornadoDeviceCommand.detectDevices();
    }

    public static TornadoDeviceChoice initialDeviceChoice(List<TornadoDeviceChoice> devices) {
        Optional<TornadoDeviceChoice> inheritedChoice = inheritedDeviceChoice(devices);
        if (inheritedChoice.isPresent()) {
            return inheritedChoice.get();
        }
        if (devices == null || devices.isEmpty()) {
            return defaultDeviceChoice();
        }
        return devices.stream()
                .filter(TornadoDeviceChoice::defaultDevice)
                .findFirst()
                .orElse(devices.getFirst());
    }

    public static TornadoDevice resolveDevice(Stage owner, TornadoDeviceChoice choice) {
        return resolveDevice(owner, choice, true);
    }

    /**
     * Encodes a device choice for {@value #INHERITED_DEVICE_PROPERTY}.
     */
    public static String encodeDeviceChoice(TornadoDeviceChoice choice) {
        if (choice == null || choice.driverIndex() < 0 || choice.deviceIndex() < 0) {
            throw new IllegalArgumentException("A device choice must have nonnegative driver and device indices");
        }
        return choice.driverIndex() + ":" + choice.deviceIndex();
    }

    /**
     * Returns the launcher-provided device only when the property is strictly formatted and
     * identifies one of the supplied detected choices.
     */
    public static Optional<TornadoDeviceChoice> inheritedDeviceChoice(List<TornadoDeviceChoice> devices) {
        Optional<DeviceIndices> inheritedIndices = parseInheritedDeviceIndices();
        if (inheritedIndices.isEmpty() || devices == null) {
            return Optional.empty();
        }
        DeviceIndices indices = inheritedIndices.get();
        return devices.stream()
                .filter(choice -> choice.driverIndex() == indices.driverIndex()
                        && choice.deviceIndex() == indices.deviceIndex())
                .findFirst();
    }

    public static boolean hasInheritedDeviceProperty() {
        return System.getProperty(INHERITED_DEVICE_PROPERTY) != null;
    }

    private static TornadoDevice resolveDevice(Stage owner, TornadoDeviceChoice choice, boolean showFailureAlert) {
        if (choice == null) {
            return null;
        }
        try {
            return TornadoExecutionPlan.getDevice(choice.driverIndex(), choice.deviceIndex());
        } catch (RuntimeException e) {
            String message = "Could not select Tornado device " + choice.tornadoDeviceId()
                    + "; the simulation will use TornadoVM's default device. " + e.getMessage();
            if (!showFailureAlert) {
                System.err.println(message);
                return null;
            }
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("TornadoVM Device");
            alert.setHeaderText("Could not select Tornado device " + choice.tornadoDeviceId());
            alert.setContentText("The simulation will use TornadoVM's default device.\n\n" + e.getMessage());
            if (owner != null && owner.getScene() != null) {
                alert.initOwner(owner);
            }
            alert.showAndWait();
            return null;
        }
    }

    private static Optional<DeviceIndices> parseInheritedDeviceIndices() {
        String encodedChoice = System.getProperty(INHERITED_DEVICE_PROPERTY);
        if (encodedChoice == null) {
            return Optional.empty();
        }
        Matcher matcher = DEVICE_ID_PATTERN.matcher(encodedChoice);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DeviceIndices(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public TornadoDeviceChoice showAndWait(Stage owner) {
        List<TornadoDeviceChoice> devices = TornadoDeviceCommand.detectDevices();
        TornadoDeviceChoice initialSelection = devices.stream()
                .filter(TornadoDeviceChoice::defaultDevice)
                .findFirst()
                .orElse(devices.getFirst());

        SearchableComboBox<TornadoDeviceChoice> deviceCombo = new SearchableComboBox<>();
        deviceCombo.getItems().setAll(devices);
        deviceCombo.setValue(initialSelection);
        deviceCombo.setMaxWidth(Double.MAX_VALUE);

        TextArea commandInfo = readOnlyTextArea(initialSelection.commandInfo(), 7);
        deviceCombo.valueProperty().addListener((_, _, selectedDevice) -> {
            if (selectedDevice != null) {
                commandInfo.setText(selectedDevice.commandInfo());
            }
        });

        MasterDetailPane masterDetailPane = new MasterDetailPane(Side.BOTTOM);
        masterDetailPane.setShowDetailNode(false);
        masterDetailPane.setDividerPosition(0.58);

        Button moreDetailsButton = new Button("More device details");
        moreDetailsButton.setOnAction(_ -> {
            TornadoDeviceChoice selectedDevice = deviceCombo.getValue();
            if (selectedDevice == null) {
                return;
            }
            masterDetailPane.setDetailNode(deviceDetailsNode(selectedDevice));
            masterDetailPane.setShowDetailNode(!masterDetailPane.isShowDetailNode());
            moreDetailsButton.setText(masterDetailPane.isShowDetailNode() ? "Hide device details" : "More device details");
        });

        VBox masterContent = new VBox(8,
                new Label("Simulation device"),
                deviceCombo,
                new Label("tornado --devices"),
                commandInfo,
                moreDetailsButton);
        masterContent.setPadding(new Insets(4));
        VBox.setVgrow(commandInfo, Priority.ALWAYS);
        masterDetailPane.setMasterNode(masterContent);

        Dialog<TornadoDeviceChoice> dialog = new Dialog<>();
        dialog.setTitle("Select TornadoVM Device");
        dialog.setHeaderText("Choose the GPU device for this simulation");
        if (owner != null && owner.getScene() != null) {
            dialog.initOwner(owner);
        }
        ButtonType startButtonType = new ButtonType("Start simulation", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(startButtonType);
        dialog.getDialogPane().setContent(masterDetailPane);
        dialog.getDialogPane().setPrefSize(760, 520);
        dialog.setResultConverter(button -> button == startButtonType ? deviceCombo.getValue() : initialSelection);
        return dialog.showAndWait().orElse(initialSelection);
    }

    private static TornadoDeviceChoice defaultDeviceChoice() {
        return defaultChoice(TornadoDeviceCommand.detectDevices());
    }

    private static TornadoDeviceChoice defaultChoice(List<TornadoDeviceChoice> devices) {
        if (devices == null || devices.isEmpty()) {
            return null;
        }
        return devices.stream()
                .filter(TornadoDeviceChoice::defaultDevice)
                .findFirst()
                .orElse(devices.getFirst());
    }

    private Node deviceDetailsNode(TornadoDeviceChoice choice) {
        TextArea details = readOnlyTextArea(fetchDeviceDetails(choice), 10);
        VBox.setVgrow(details, Priority.ALWAYS);
        VBox detailBox = new VBox(6, new Label("On-demand Tornado device details"), details);
        detailBox.setPadding(new Insets(8, 4, 4, 4));
        return detailBox;
    }

    private String fetchDeviceDetails(TornadoDeviceChoice choice) {
        try {
            TornadoDevice device = TornadoExecutionPlan.getDevice(choice.driverIndex(), choice.deviceIndex());
            return String.join(System.lineSeparator(),
                    "Tornado device: " + choice.tornadoDeviceId(),
                    "Name: " + device.getDeviceName(),
                    "Description: " + device.getDescription(),
                    "Platform: " + device.getPlatformName(),
                    "Type: " + device.getDeviceType(),
                    "Backend: " + device.getTornadoVMBackend(),
                    "Global memory: " + formatBytes(device.getMaxGlobalMemory()),
                    "Max allocation: " + formatBytes(device.getMaxAllocMemory()),
                    "Local memory: " + formatBytes(device.getDeviceLocalMemorySize()),
                    "Workgroup dimensions: " + Arrays.toString(device.getDeviceMaxWorkgroupDimensions()),
                    "OpenCL C version: " + device.getDeviceOpenCLCVersion(),
                    "Available processors: " + device.getAvailableProcessors(),
                    "SPIR-V supported: " + device.isSPIRVSupported());
        } catch (RuntimeException e) {
            return "Could not fetch Tornado API details for device " + choice.tornadoDeviceId()
                    + System.lineSeparator()
                    + e.getMessage();
        }
    }

    private TextArea readOnlyTextArea(String text, int visibleRows) {
        TextArea textArea = new TextArea(text);
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setPrefRowCount(visibleRows);
        textArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        return textArea;
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        while (value >= 1024.0 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        return String.format("%.1f %s", value, units[unitIndex]);
    }

    private record DeviceIndices(int driverIndex, int deviceIndex) {
    }
}
