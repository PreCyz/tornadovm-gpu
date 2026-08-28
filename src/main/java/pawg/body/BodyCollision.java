package pawg.body;

final class BodyCollision {

    private BodyCollision() {
    }

    static State merge(State first, State second) {
        float totalMass = first.mass + second.mass;
        if (totalMass <= 0.0f) {
            throw new IllegalArgumentException("Merged mass must be positive");
        }
        return new State(
                weightedAverage(first.x, first.mass, second.x, second.mass, totalMass),
                weightedAverage(first.y, first.mass, second.y, second.mass, totalMass),
                weightedAverage(first.z, first.mass, second.z, second.mass, totalMass),
                weightedAverage(first.vx, first.mass, second.vx, second.mass, totalMass),
                weightedAverage(first.vy, first.mass, second.vy, second.mass, totalMass),
                weightedAverage(first.vz, first.mass, second.vz, second.mass, totalMass),
                totalMass);
    }

    private static float weightedAverage(float firstValue, float firstMass, float secondValue, float secondMass,
            float totalMass) {
        return (firstValue * firstMass + secondValue * secondMass) / totalMass;
    }

    record State(float x, float y, float z, float vx, float vy, float vz, float mass) {
    }
}
