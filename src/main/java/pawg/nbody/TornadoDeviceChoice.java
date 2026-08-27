package pawg.nbody;

public record TornadoDeviceChoice(int driverIndex, int deviceIndex, String title, String commandInfo, String commandOutput,
                                  boolean defaultDevice) {

    public String tornadoDeviceId() {
        return driverIndex + ":" + deviceIndex;
    }

    @Override
    public String toString() {
        return title;
    }
}
