package pawg;

import javafx.application.Application;
import pawg.body.BodySimulator;
import pawg.eclipse.SolarEclipseFX;
import pawg.gameoflife.GameOfLifeInteractive;
import pawg.gravity.EarthOrbitGPU;
import pawg.gravity.SolarSystemGPU;
import pawg.heatdistribution.HeatDistributionConstantHeatersFX;
import pawg.heatdistribution.HeatDistributionFX;
import pawg.nbody.GravityGPU;
import pawg.nbody.GravitySystemCPU;

import java.util.Arrays;
import java.util.Optional;

/** Fixed registry shared by the CLI dispatcher and graphical launcher. */
public enum SimulationTarget {
    HEAT_DISTRIBUTION("1", "Heat distribution", true, HeatDistributionFX.class),
    CONSTANT_HEATERS("2", "Heat distribution — constant heaters", true, HeatDistributionConstantHeatersFX.class),
    SOLAR_ECLIPSE("3", "Solar eclipse", true, SolarEclipseFX.class),
    EARTH_ORBIT("4", "Earth orbit", true, EarthOrbitGPU.class),
    SOLAR_SYSTEM("5", "Solar system", true, SolarSystemGPU.class),
    GRAVITY_CPU("6", "N-body gravity — CPU", false, GravitySystemCPU.class),
    GRAVITY_GPU("7", "N-body gravity — GPU", true, GravityGPU.class),
    BODY_SIMULATOR("8", "Body simulator", true, BodySimulator.class),
    GAME_OF_LIFE(null, "Game of Life", true, GameOfLifeInteractive.class);

    private final String branch;
    private final String label;
    private final boolean gpuTarget;
    private final Class<? extends Application> applicationClass;

    SimulationTarget(String branch, String label, boolean gpuTarget,
                     Class<? extends Application> applicationClass) {
        this.branch = branch;
        this.label = label;
        this.gpuTarget = gpuTarget;
        this.applicationClass = applicationClass;
    }

    public Optional<String> branch() {
        return Optional.ofNullable(branch);
    }

    public String label() {
        return label;
    }

    public boolean gpuTarget() {
        return gpuTarget;
    }

    public Class<? extends Application> applicationClass() {
        return applicationClass;
    }

    /** Preserves the existing CLI rule: no argument or an unknown branch opens Game of Life. */
    public static SimulationTarget resolveCliTarget(String[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return GAME_OF_LIFE;
        }
        String requestedBranch = arguments[0];
        return Arrays.stream(values())
                .filter(target -> target.branch != null && target.branch.equals(requestedBranch))
                .findFirst()
                .orElse(GAME_OF_LIFE);
    }
}
