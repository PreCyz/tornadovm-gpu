package pawg.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BodyCollisionTest {

    @Test
    void mergeConservesMassCenterOfMassAndLinearMomentum() {
        BodyCollision.State first = new BodyCollision.State(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 2.0f);
        BodyCollision.State second = new BodyCollision.State(5.0f, 6.0f, 7.0f, -2.0f, 1.0f, 0.0f, 6.0f);

        BodyCollision.State merged = BodyCollision.merge(first, second);

        assertEquals(8.0f, merged.mass());
        assertEquals(4.0f, merged.x());
        assertEquals(5.0f, merged.y());
        assertEquals(6.0f, merged.z());
        assertEquals(-0.5f, merged.vx());
        assertEquals(2.0f, merged.vy());
        assertEquals(1.5f, merged.vz());
        assertEquals(first.mass() * first.vx() + second.mass() * second.vx(), merged.mass() * merged.vx());
        assertEquals(first.mass() * first.vy() + second.mass() * second.vy(), merged.mass() * merged.vy());
        assertEquals(first.mass() * first.vz() + second.mass() * second.vz(), merged.mass() * merged.vz());
    }
}
