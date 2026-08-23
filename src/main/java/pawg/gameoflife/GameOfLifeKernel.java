package pawg.gameoflife;

import uk.ac.manchester.tornado.api.annotations.Parallel;

public class GameOfLifeKernel {
    public static void computeNextGeneration(int[] currentGrid, int[] nextGrid, int width, int height) {
        for (@Parallel int y = 0; y < height; y++) {
            for (@Parallel int x = 0; x < width; x++) {
                int liveNeighbors = 0;

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;

                        int nx = (x + dx + width) % width;
                        int ny = (y + dy + height) % height;

                        liveNeighbors += currentGrid[ny * width + nx];
                    }
                }

                int currentIndex = y * width + x;
                int currentState = currentGrid[currentIndex];

                if (currentState == 1 && (liveNeighbors == 2 || liveNeighbors == 3)) {
                    nextGrid[currentIndex] = 1;
                } else if (currentState == 0 && liveNeighbors == 3) {
                    nextGrid[currentIndex] = 1;
                } else {
                    nextGrid[currentIndex] = 0;
                }
            }
        }
    }
}
