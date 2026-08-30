package pawg.nbody;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TornadoDeviceSelectorTest {
    private static final String PROPERTY = TornadoDeviceSelector.INHERITED_DEVICE_PROPERTY;
    private static final List<TornadoDeviceChoice> DEVICES = List.of(
            new TornadoDeviceChoice(0, 0, "default", "", "", true),
            new TornadoDeviceChoice(2, 3, "alternate", "", "", false));

    @Test
    void inheritedChoiceMustBeStrictAndMatchADetectedDevice() {
        withProperty("2:3", () -> assertEquals(DEVICES.get(1), TornadoDeviceSelector.inheritedDeviceChoice(DEVICES).orElseThrow()));
        for (String invalid : List.of("02:3", "2:03", "-2:3", "2:-3", "2", "2:3:4", " 2:3", "2147483648:0")) {
            withProperty(invalid, () -> assertTrue(TornadoDeviceSelector.inheritedDeviceChoice(DEVICES).isEmpty()));
        }
        withProperty("7:8", () -> assertTrue(TornadoDeviceSelector.inheritedDeviceChoice(DEVICES).isEmpty()));
    }

    @Test
    void inheritedMatchingChoiceTakesInitialPreferenceOtherwiseDeclaredDefaultWins() {
        withProperty("2:3", () -> assertEquals(DEVICES.get(1), TornadoDeviceSelector.initialDeviceChoice(DEVICES)));
        withProperty("9:9", () -> assertEquals(DEVICES.getFirst(), TornadoDeviceSelector.initialDeviceChoice(DEVICES)));
    }

    @Test
    void presenceOfInheritedPropertyIsRetainedEvenWhenItCannotBeUsed() {
        withProperty("malformed", () -> assertTrue(TornadoDeviceSelector.hasInheritedDeviceProperty()));
        String previous = System.getProperty(PROPERTY);
        try {
            System.clearProperty(PROPERTY);
            assertTrue(!TornadoDeviceSelector.hasInheritedDeviceProperty());
        } finally {
            if (previous != null) {
                System.setProperty(PROPERTY, previous);
            }
        }
    }

    private static void withProperty(String value, Runnable assertion) {
        String previous = System.getProperty(PROPERTY);
        try {
            System.setProperty(PROPERTY, value);
            assertion.run();
        } finally {
            if (previous == null) {
                System.clearProperty(PROPERTY);
            } else {
                System.setProperty(PROPERTY, previous);
            }
        }
    }
}
