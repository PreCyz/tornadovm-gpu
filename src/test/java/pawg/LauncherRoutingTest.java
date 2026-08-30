package pawg;

import org.junit.jupiter.api.Test;
import pawg.body.BodySimulator;
import pawg.eclipse.SolarEclipseFX;
import pawg.gameoflife.GameOfLifeInteractive;
import pawg.gravity.EarthOrbitGPU;
import pawg.gravity.SolarSystemGPU;
import pawg.heatdistribution.HeatDistributionConstantHeatersFX;
import pawg.heatdistribution.HeatDistributionFX;
import pawg.nbody.GravityGPU;
import pawg.nbody.GravitySystemCPU;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherRoutingTest {
    @Test
    void registryKeepsEveryLegacyBranchAndItsApplicationClass() {
        Map<String, Class<?>> expectedTargets = Map.of(
                "1", HeatDistributionFX.class,
                "2", HeatDistributionConstantHeatersFX.class,
                "3", SolarEclipseFX.class,
                "4", EarthOrbitGPU.class,
                "5", SolarSystemGPU.class,
                "6", GravitySystemCPU.class,
                "7", GravityGPU.class,
                "8", BodySimulator.class);

        assertEquals(9, SimulationTarget.values().length);
        expectedTargets.forEach((branch, applicationClass) -> {
            SimulationTarget target = SimulationTarget.resolveCliTarget(new String[]{branch});
            assertEquals(applicationClass, target.applicationClass());
            assertEquals(branch, target.branch().orElseThrow());
        });
        assertEquals(GameOfLifeInteractive.class, SimulationTarget.GAME_OF_LIFE.applicationClass());
        assertTrue(SimulationTarget.GAME_OF_LIFE.branch().isEmpty());
        assertFalse(SimulationTarget.GRAVITY_CPU.gpuTarget());
        assertTrue(SimulationTarget.GRAVITY_GPU.gpuTarget());
    }

    @Test
    void absentOrUnknownCliBranchUsesGameOfLifeSentinel() {
        assertEquals(SimulationTarget.GAME_OF_LIFE, SimulationTarget.resolveCliTarget(null));
        assertEquals(SimulationTarget.GAME_OF_LIFE, SimulationTarget.resolveCliTarget(new String[0]));
        assertEquals(SimulationTarget.GAME_OF_LIFE, SimulationTarget.resolveCliTarget(new String[]{"unknown"}));
        assertEquals(SimulationTarget.GAME_OF_LIFE, SimulationTarget.resolveCliTarget(new String[]{"0"}));
    }

    @Test
    void noArgumentRoutingUsesTheLauncherOnlyWhenBootstrapFxIsAvailable() {
        assertEquals(LauncherWindow.class, Launcher.noArgumentApplication(true));
        assertEquals(GameOfLifeInteractive.class, Launcher.noArgumentApplication(false));
        assertTrue(Launcher.bootstrapFxAvailable());
    }
}
