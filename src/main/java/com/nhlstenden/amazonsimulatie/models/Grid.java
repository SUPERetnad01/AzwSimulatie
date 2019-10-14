package com.nhlstenden.amazonsimulatie.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Grid {
  private final int[][] checkInts = {
    {0, 1},
    {1, 0},
    {0, -1},
    {-1, 0}
  };
  private int gridSizeX = 30;
  private int gridSizeY;
  private Node[][] grid;
  private Random r = new Random();

  public Grid(int modules) {
    this.gridSizeY = (6 * modules);
    createGrid();
  }

  private void createGrid() {
    grid = new Node[gridSizeX][gridSizeY];

    for (int x = 0; x < gridSizeX; x++) {
      for (int y = 0; y < gridSizeY; y++) {
        grid[x][y] = new Node(x, y, null);
      }
    }
  }

  public List<Node> getNeighbours(Node node) {
    List<Node> neighbours = new ArrayList<>();
    for (int[] vec : checkInts) {
      int checkX = (node.getGridX() + vec[0]);
      int checkY = (node.getGridY() + vec[1]);
      if (checkX >= 0 && checkX < gridSizeX && checkY >= 0 && checkY < gridSizeY) {
        neighbours.add(grid[checkX][checkY]);
      }
    }
    return neighbours;
  }

  public int getGridSizeX() {
    return gridSizeX;
  }

  public int getGridSizeY() {
    return gridSizeY;
  }

  public Node getNode(int x, int y) {
    return grid[x][y];
  }

  public Node RandomNode() {
    Node random = grid[r.nextInt(gridSizeX)][r.nextInt(gridSizeY)];
    if (random.getOccupation() == null)
      return random;
    return RandomNode();
  }
}
