import {
  AmbientLight,
  BoxGeometry,
  CubeTextureLoader,
  DoubleSide,
  GridHelper,
  Group,
  Mesh,
  MeshBasicMaterial,
  MeshStandardMaterial,
  Scene,
  SphereGeometry,
  TextureLoader
} from "three";
import {OBJLoader} from "three/examples/jsm/loaders/OBJLoader";

class WorldObjectManger {
  private worldObjects: Array<Group> = [];
  private preloadedModels: Record<string, Group> = {};
  private reqModels: Array<Array<string>> = [
    ["Rack", "Rack_RackMat"],
    ["Warehouse", "Warehouse_Concrete"],
    ["Box", "BoxMat"]
  ];
  private scene = new Scene();
  private objloader = new OBJLoader();
  textureCube;


  public loadModels(callback: () => void) {
    const r = "https://threejs.org/examples/textures/cube/Bridge2/";
    const urls = [r + "posx.jpg", r + "negx.jpg",
      r + "posy.jpg", r + "negy.jpg",
      r + "posz.jpg", r + "negz.jpg"];

    this.textureCube = new CubeTextureLoader().load(urls);

    const promises = [];
    this.reqModels.forEach(e => {
      promises.push(this.objloaderLoad(e));
    });
    Promise.all(promises).then(callback);
  }

  private objloaderLoad(dat: Array<string>) {
    return new Promise(resolve => {
      this.objloader.load(`assets/models/${dat[0]}.obj`, obj => {
        obj.traverse(m => {
          if (m instanceof Mesh)
            m.material = new MeshStandardMaterial({
              map: new TextureLoader().load(`assets/textures/${dat[1]}_BaseColor.png`),
              normalMap: new TextureLoader().load(`assets/textures/${dat[1]}_Normal.png`),
              metalnessMap: new TextureLoader().load(`assets/textures/${dat[1]}_Metallic.png`),
              roughnessMap: new TextureLoader().load(`assets/textures/${dat[1]}_Roughness.png`),
              bumpMap: new TextureLoader().load(`assets/textures/${dat[1]}_Height.png`),
              envMap: this.textureCube
            });
        });
        this.preloadedModels[dat[0]] = obj;
        resolve();
      });
    });
  }

  public initWorld() {

    const sphericalSkyboxGeometry = new SphereGeometry(900, 32, 32);
    const sphericalSkyboxMaterial = new MeshBasicMaterial({
      map: new TextureLoader().load('assets/textures/lebombo_2k.jpg'),
      side: DoubleSide
    });
    const sphericalSkybox = new Mesh(sphericalSkyboxGeometry, sphericalSkyboxMaterial);
    this.scene.add(sphericalSkybox);

    this.createWarehouse(4);

    const gridHelper = new GridHelper(42, 42);
    gridHelper.position.x = 21;
    gridHelper.position.z = 21;
    this.scene.add(gridHelper);

    const light = new AmbientLight(0x404040);
    light.intensity = 4;
    this.scene.add(light);
  }

  private getModel(name: string) {
    return this.preloadedModels[name].clone();
  }

  private createWarehouse(numberOfModules) {
    for (let i = 0; i < numberOfModules; i++) {
      let obj = this.getModel("Warehouse");
      obj.rotation.y = Math.PI;
      obj.position.x = 11.5;
      obj.position.y = 0;
      obj.position.z = 2.5 + (i * 6);
      this.addScene(obj)
    }
  }

  private addScene(obj) {
    this.scene.add(obj);
  }

  public getWorldObjects(): Array<Group> {
    return this.worldObjects;
  }

  public getScene(): Scene {
    return this.scene;
  }

  public updateObject(command): void {
    // Wanneer het object dat moet worden geupdate nog niet bestaat (komt niet voor in de lijst met worldObjects op de client),
    // dan wordt het 3D model eerst aangemaakt in de 3D wereld.
    if (Object.keys(this.worldObjects).indexOf(command.parameters.uuid) < 0) {
      // Wanneer het object een robot is, wordt de code hieronder uitgevoerd.
      if (command.parameters.type === 'robot') {
        this.createRobot(command);
      }
      if (command.parameters.type === 'rack') {
        this.createRack(command);
      }
    }
    /*
   * Deze code wordt elke update uitgevoerd. Het update alle positiegegevens van het 3D object.
   */
    const object = this.worldObjects[command.parameters.uuid];

    if (object == null)
      return;

    object.position.x = command.parameters.x;
    object.position.y = command.parameters.y;
    object.position.z = command.parameters.z;

    object.rotation.x = command.parameters.rotationX;
    object.rotation.y = command.parameters.rotationY;
    object.rotation.z = command.parameters.rotationZ;
  }

  public createRobot(command): void {
    const geometry = new BoxGeometry(0.9, 0.3, 0.9);
    const cubeMaterials = [
      new MeshBasicMaterial({
        map: new TextureLoader().load('assets/textures/robot_side.png'),
        side: DoubleSide
      }), // LEFT
      new MeshBasicMaterial({
        map: new TextureLoader().load('assets/textures/robot_side.png'),
        side: DoubleSide
      }), // RIGHT
      new MeshBasicMaterial({
        map: new TextureLoader().load('assets/textures/robot_top.png'),
        side: DoubleSide
      }), // TOP
      new MeshBasicMaterial({
        map: new TextureLoader().load('assets/textures/robot_bottom.png'),
        side: DoubleSide
      }), // BOTTOM
      new MeshBasicMaterial({
        map: new TextureLoader().load('assets/textures/robot_front.png'),
        side: DoubleSide
      }), // FRONT
      new MeshBasicMaterial({
        map: new TextureLoader().load('assets/textures/robot_front.png'),
        side: DoubleSide
      }) // BACK
    ];
    const robot = new Mesh(geometry, cubeMaterials);
    robot.position.y = 0.15;

    const group = new Group();
    group.add(robot);
    this.scene.add(group);
    this.worldObjects[command.parameters.uuid] = group;
  }

  public createRack(command): void {
    const scene = this.scene;
    const worldObjects = this.worldObjects;

    let rack = this.getModel("Rack");
    //initial spawn position of the racks
    rack.position.y = -1000;
    scene.add(rack);
    worldObjects[command.parameters.uuid] = rack;
  }


  CleanupAll(): void {
    for (let e in this.worldObjects) {
      this.scene.remove(this.worldObjects[e])
    }
  }
}

export {WorldObjectManger}
