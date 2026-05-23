// "2D" arrays in Java are arrays of arrays. Useful for grids, matrices,
// boards. The inner arrays don't have to be the same length — jagged arrays
// are allowed.

public class TwoDimensionalArray {

    public static void main(String[] args) {

        int[][] grid = {
                {1, 2, 3},
                {4, 5, 6},
                {7, 8, 9}
        };

        // Access by row, column
        System.out.println("grid[1][2] = " + grid[1][2]);   // 6
        System.out.println("rows: " + grid.length);
        System.out.println("cols in row 0: " + grid[0].length);

        // Print the whole grid
        System.out.println("\nGrid:");
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                System.out.print(grid[row][col] + " ");
            }
            System.out.println();
        }

        // A jagged array — rows of different lengths
        int[][] jagged = {
                {1, 2},
                {3, 4, 5, 6},
                {7}
        };

        System.out.println("\nJagged:");
        for (int[] row : jagged) {
            for (int n : row) {
                System.out.print(n + " ");
            }
            System.out.println();
        }
    }
}
