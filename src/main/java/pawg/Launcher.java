package pawg;

import javafx.application.Application;
import pawg.eclipse.SolarEclipseFX;
import pawg.gameoflife.GameOfLifeInteractive;
import pawg.gravity.*;
import pawg.heatdistribution.HeatDistributionConstantHeatersFX;
import pawg.heatdistribution.HeatDistributionFX;

public class Launcher {
    static void main(String[] args) {
        if (args.length > 0) {
            switch (args[0]) {
                case "1" -> Application.launch(HeatDistributionFX.class, args);
                case "2" -> Application.launch(HeatDistributionConstantHeatersFX.class, args);
                case "3" -> SolarEclipseFX.main(args);
                case "4" -> Application.launch(EarthOrbitGPU.class, args);
                case "5" -> Application.launch(SolarSystemGPU.class, args);
                case "6" -> Application.launch(GravitySystemCPU.class, args);
                case "7" -> Application.launch(GravityGPU.class, args);
            }
        } else {
            Application.launch(GameOfLifeInteractive.class, args);
        }
    }
}
