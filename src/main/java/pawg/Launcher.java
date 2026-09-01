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

public class Launcher {
    public static void main(String[] args) {
        if (args.length > 0) {
            switch (args[0]) {
                case "1" -> Application.launch(HeatDistributionFX.class, args);
                case "2" -> Application.launch(HeatDistributionConstantHeatersFX.class, args);
                case "3" -> SolarEclipseFX.main(args);
                case "4" -> Application.launch(EarthOrbitGPU.class, args);
                case "5" -> Application.launch(SolarSystemGPU.class, args);
                case "6" -> Application.launch(GravitySystemCPU.class, args);
                case "7" -> Application.launch(GravityGPU.class, args);
                case "8" -> Application.launch(BodySimulator.class, args);
                default -> Application.launch(GameOfLifeInteractive.class, args);
            }
        } else {
            Application.launch(noArgumentApplication(bootstrapFxAvailable()));
        }
    }

    static Class<? extends Application> noArgumentApplication(boolean bootstrapAvailable) {
        return bootstrapAvailable ? LauncherWindow.class : GameOfLifeInteractive.class;
    }

    static boolean bootstrapFxAvailable() {
        try {
            Class.forName("org.kordamp.bootstrapfx.BootstrapFX", false, Launcher.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError _) {
            return false;
        }
    }
}
