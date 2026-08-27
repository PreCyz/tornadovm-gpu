package pawg.nbody;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TornadoDeviceCommandTest {
    @Test
    void parsesTornadoDevicesOutputIntoSelectableDevices() {
        String output = """
                Number of Tornado drivers: 1
                Driver: \u001B[36mOpenCL\u001B[0m
                  Total number of OpenCL devices  : 2
                  Tornado device=0:0  (DEFAULT)
                \t\u001B[36mOPENCL\u001B[0m --  [AMD Accelerated Parallel Processing] -- gfx1103
                \t\tGlobal Memory Size: 25.0 GB
                \t\tLocal Memory Size: 64.0 KB
                  Tornado device=0:1
                \tOPENCL --  [Intel OpenCL] -- Arc
                \t\tGlobal Memory Size: 8.0 GB
                """;

        List<TornadoDeviceChoice> devices = TornadoDeviceCommand.parseDevices(output);

        assertEquals(2, devices.size());
        assertEquals(0, devices.get(0).driverIndex());
        assertEquals(0, devices.get(0).deviceIndex());
        assertTrue(devices.get(0).defaultDevice());
        assertTrue(devices.get(0).commandInfo().contains("Tornado device=0:0"));
        assertTrue(devices.get(0).commandInfo().contains("gfx1103"));
        assertEquals(1, devices.get(1).deviceIndex());
        assertTrue(devices.get(1).title().contains("Arc"));
    }
}
