package com.nhlstenden.amazonsimulatie.controllers;

import com.nhlstenden.amazonsimulatie.models.*;
import com.nhlstenden.amazonsimulatie.models.generated.Node;
import com.nhlstenden.amazonsimulatie.models.generated.Rack;
import com.nhlstenden.amazonsimulatie.models.generated.Robot;
import com.nhlstenden.amazonsimulatie.models.generated.Waybill;
import net.ravendb.client.documents.BulkInsertOperation;
import net.ravendb.client.documents.indexes.spatial.SpatialRelation;
import net.ravendb.client.documents.queries.spatial.WktField;
import net.ravendb.client.documents.session.IDocumentSession;
import net.ravendb.client.documents.subscriptions.SubscriptionBatch;
import net.ravendb.client.documents.subscriptions.SubscriptionWorker;

import java.util.Queue;
import java.util.*;

import static com.nhlstenden.amazonsimulatie.models.Data.rackSpawnPositions;
import static com.nhlstenden.amazonsimulatie.models.generated.Waybill.Destination.MELKFACTORY;
import static com.nhlstenden.amazonsimulatie.models.generated.Waybill.Destination.WAREHOUSE;

/*
 * Deze class is een versie van het model van de simulatie. In dit geval is het
 * de 3D wereld die we willen modelleren (magazijn). De zogenaamde domain-logic,
 * de logica die van toepassing is op het domein dat de applicatie modelleerd, staat
 * in het model. Dit betekent dus de logica die het magazijn simuleert.
 */
public class WarehouseManager implements Warehouse {
  private int loadingBay = 99999;
  /*
   * De wereld bestaat uit objecten, vandaar de naam worldObjects. Dit is een lijst
   * van alle objecten in de 3D wereld. Deze objecten zijn in deze voorbeeldcode alleen
   * nog robots. Er zijn ook nog meer andere objecten die ook in de wereld voor kunnen komen.
   * Deze objecten moeten uiteindelijk ook in de lijst passen (overerving). Daarom is dit
   * een lijst van Object3D onderdelen. Deze kunnen in principe alles zijn. (Robots, vrachrtwagens, etc)
   */
  private HashMap<String, RobotLogic> robots = new HashMap<>();
  private Grid grid;

  /*
   * De wereld maakt een lege lijst voor worldObjects aan. Daarin wordt nu één robot gestopt.
   * Deze methode moet uitgebreid worden zodat alle objecten van de 3D wereld hier worden gemaakt.
   */
  public WarehouseManager() {
    grid = new Grid(Data.moduleLength, (6 * Data.modules));
    int numberOfRobots = Data.modules * 10;
    numberOfRobots -= (int) Math.ceil(Data.modules / 5.0) * 10;

    try (IDocumentSession session = DocumentStoreHolder.getStore().openSession()) {
      numberOfRobots -= session.query(Robot.class).count();

      if (numberOfRobots > 0)
        try (BulkInsertOperation bulkInsert = DocumentStoreHolder.getStore().bulkInsert()) {
          for (int m = 0; m < Data.modules; m++) {
            if (m % 5 == 2) {
              for (int y : Data.rackPositionsY) {
                for (int x : Data.rackPositionsX) {
                  if (numberOfRobots > 0) {
                    Robot robot = new Robot();
                    robot.setStatus(Robot.Status.IDLE);
                    robot.setX(x);
                    int yf = y + (m * 6);
                    robot.setY(yf);

                    robot.setWkt(DocumentStoreHolder.formatWtk(x, yf));

                    bulkInsert.store(robot);

                    RobotLogic logic = new RobotLogic(session.advanced().getDocumentId(robot), grid, x, y + (m * 6));
                    logic.registerWarehouse(this);
                    numberOfRobots -= 1;
                  }
                }
              }
            }
          }
        }

      for (Robot robot : session.query(Robot.class).toList()) {
        RobotLogic logic = new RobotLogic(robot.getId(), grid, robot.getX(), robot.getY());
        logic.registerWarehouse(this);
        robots.put(logic.getId(), logic);
        grid.addWall(robot.getX(), robot.getY());
      }

      List<Robot> results = session
        .query(Robot.class)
        .spatial(
          new WktField("wkt"),
          f -> f.relatesToShape("Circle(53.000000 6.000000 d=10.0000)", SpatialRelation.WITHIN)
        )
        .toList();
    }

    for (int y = 0; y < (6 * Data.modules); y++) {
      if (y % 6 == 0 || y % 6 == 1 || y % 6 == 4 || y % 6 == 5) {
        for (int x = 0; x < 6; x++) {
          grid.addWall((29 - x), y);
        }
      }
    }

    String subscriptionName = DocumentStoreHolder.getStore().subscriptions().create(Waybill.class);
    SubscriptionWorker<Waybill> workerWBatch = DocumentStoreHolder.getStore().subscriptions().getSubscriptionWorker(Waybill.class, subscriptionName);
    workerWBatch.run(batch -> {
      for (SubscriptionBatch.Item<Waybill> item : batch.getItems()) {
        if (item.getResult().getStatus() == Waybill.Status.UNRESOLVED)
          processWaybill(item.getId());
      }
    });
  }

  public void update() {
    for (Map.Entry<String, RobotLogic> logicEntry : robots.entrySet()) {
      logicEntry.getValue().update();
    }
  }



  private Node rackDropLocation() {
    for (int m = 0; m < Data.modules; m++) {
      if (m % 5 != 2) {
        for (int y : Data.rackPositionsY) {
          for (int x : Data.rackPositionsX) {
            if (!grid.getNode(x, (m * 6) + y).isOccupied()) {
              grid.getNode(x, (m * 6) + y).setOccupied(true);
              return grid.getNode(x, (m * 6) + y);
            }
          }
        }
      }
    }
    return null;
  }

  private void processWaybill(String waybillId) {
    try (IDocumentSession session = DocumentStoreHolder.getStore().openSession()) {
      loadingBay++;
      if (loadingBay >= Data.modules)
        loadingBay = 0;

      int truckPosX = 24,
        truckPosY = (loadingBay * 6);

      Waybill waybill = session.load(Waybill.class, waybillId);
      List<String> cargo = new ArrayList<>();//waybill.getRacks();

      int numberOfRacks = waybill.getRacksAmount();
      List<Rack> racks;

      if (waybill.getDestination() == WAREHOUSE) {
        racks = session.query(Rack.class)
          .whereEquals("status", Rack.Status.POOLED)
          .take(numberOfRacks).toList();

        if (racks.size() <= numberOfRacks) {
          numberOfRacks -= racks.size();

          try (BulkInsertOperation bulkInsert = DocumentStoreHolder.getStore().bulkInsert()) {
            for (int i = 0; i < numberOfRacks; i++) {
              Rack rack = new Rack();
              rack.setItem("kaas");
              rack.setWkt(DocumentStoreHolder.formatWtk(truckPosX, truckPosY));
              rack.setStatus(Rack.Status.WAITING);
              bulkInsert.store(rack);
              racks.add(rack);
            }
          }

          List<Rack> results = session
            .query(Rack.class)
            .spatial(
              new WktField("wkt"),
              f -> f.relatesToShape("Circle(53.000000 6.000000 d=10.0000)", SpatialRelation.WITHIN)
            )
            .toList();
        }
      } else {
        racks = session.query(Rack.class)
          .whereEquals("status", Rack.Status.STORED)
          .orderByDistance("wkt", DocumentStoreHolder.formatWtk(truckPosX, truckPosY))
          .take(numberOfRacks).toList();
      }

      for (Rack rack : racks) {
        cargo.add(rack.getId());
      }

      waybill.setStatus(Waybill.Status.RESOLVING);
      waybill.setTodoList(cargo);
      waybill.getRacks().clear();

      List<Robot> idleRobots = session.query(Robot.class)
        .whereEquals("status", Robot.Status.IDLE)
        .orderByDistance("wkt", DocumentStoreHolder.formatWtk(truckPosX, truckPosY))
        .take(cargo.size()).toList();

      for (int i = 0; i < idleRobots.size(); i++) {
        Robot idleRobot = idleRobots.get(i);
        RobotLogic robotLogic = robots.get(idleRobot.getId());

        int x = rackSpawnPositions[i][1] + truckPosX,
          y = (rackSpawnPositions[i][0] + truckPosY);

        Queue<RobotTaskStrategy> tasks = new LinkedList<>();
        Rack rack = racks.get(i);//session.load(Rack.class, );//cargo.get(i)
        if (waybill.getDestination() == WAREHOUSE) {
          rack.setX(x);
          rack.setY(y);
          rack.setZ(0);

          MessageBroker.Instance().updateObject(rack);

          tasks.add(new RobotPickupStrategy(grid.getNode(x, y)));
          tasks.add(new RobotDropStrategy(rackDropLocation()));
        } else {
          tasks.add(new RobotPickupStrategy(grid.getNode(rack.getX(), rack.getY())));
          tasks.add(new RobotDropStrategy(grid.getNode(x, y)));
        }
        tasks.add(new RobotParkStrategy(grid.getNode(robotLogic.getPx(), robotLogic.getPy())));
        robotLogic.assignTask(tasks);

        robotLogic.setRack(rack);
        robotLogic.setWaybillUUID(waybill.getId());
        idleRobot.setStatus(Robot.Status.WORKING);
        idleRobot.setWaybill(waybill);
      }
      session.saveChanges();
    }
  }

  @Override
  public void robotFinishedTask(RobotLogic robotLogic, RobotTaskStrategy task) {
    if (task instanceof RobotDropStrategy) {
      try (IDocumentSession session = DocumentStoreHolder.getStore().openSession()) {
        Waybill waybill = session.load(Waybill.class, robotLogic.getWaybillUUID());

        if (waybill.getTodoList() != null) {
          waybill.getTodoList().remove(robotLogic.getRackUUID());
          waybill.getRacks().add(robotLogic.getRackUUID());
        }

        if (waybill.getTodoList() == null || waybill.getTodoList().size() <= 0) {
          if (waybill.getDestination() == MELKFACTORY)
            for (String id : waybill.getRacks()) {
              Rack rack = session.load(Rack.class, id);
              rack.setZ(-10);
              rack.setStatus(Rack.Status.POOLED);
              MessageBroker.Instance().updateObject(rack);
            }
          waybill.setStatus(Waybill.Status.POOLED);
        }

        session.saveChanges();
      }
    }
  }
}
