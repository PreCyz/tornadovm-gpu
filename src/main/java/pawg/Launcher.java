package pawg;

import javafx.application.Application;
import pawg.eclipse.SolarEclipseFX;
import pawg.gameoflife.GameOfLifeInteractive;
import pawg.heatdistribution.HeatDistributionConstantHeatersFX;
import pawg.heatdistribution.HeatDistributionFX;

public class Launcher {
    static void main(String[] args) {
        if (args.length > 0) {
            switch (args[0]) {
                case "1" -> Application.launch(HeatDistributionFX.class, args);
                case "2" -> Application.launch(HeatDistributionConstantHeatersFX.class, args);
                case "3" -> SolarEclipseFX.main(args);
            }
        } else {
            Application.launch(GameOfLifeInteractive.class, args);
        }
    }
}
