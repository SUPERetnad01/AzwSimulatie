package com.nhlstenden.amazonsimulatie.models;

import com.nhlstenden.amazonsimulatie.models.generated.Node;

public class RobotTask {
  private Node destination;
  private Task task;

  public RobotTask(Node destination, Task task) {
    this.destination = destination;
    this.task = task;
  }

  public Node getDestination() {
    return destination;
  }

  public Task getTask() {
    return task;
  }

  public enum Task {
    PICKUP,
    DROP,
    PARK
  }
}
