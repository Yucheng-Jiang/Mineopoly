package mineopoly_two.competition;

import java.awt.Point;
import java.util.*;
import java.util.Map.Entry;

import mineopoly_two.action.TurnAction;
import mineopoly_two.game.Economy;
import mineopoly_two.item.InventoryItem;
import mineopoly_two.item.ResourceType;
import mineopoly_two.strategy.MinePlayerStrategy;
import mineopoly_two.strategy.PlayerBoardView;
import mineopoly_two.tiles.TileType;

public class OciStrategy implements MinePlayerStrategy {
  protected int boardSize;
  private int maxInventorySize;
  private int maxCharge;
  private int winningScore;
  private PlayerBoardView startingBoard;
  protected boolean isRedPlayer;

  private PlayerBoardView currentBoard;
  private Economy currentEconomy;
  private Point currentPoint;
  protected boolean isRedTurn;

  private List<InventoryItem> inventoryItemList;
  private Map<Point, TileType> functionalTileMap;
  private Map<Point, InventoryItem> itemOnGround;
  List<Entry<Point, Double>> sortList = new ArrayList<>();
  protected int score;
  protected int currentCharge;

  private boolean oppo_has_defend = false;
  private Point oppoPreviousMarket = null;
  private Point oppoNowMarket = null;
  private int oppo_charge;
  TileType opponentMarket = null;
  TileType marketType;
  protected Map<Point, TileType> allResources = new HashMap<>();
  protected List<InventoryItem> oppoInventoryList = new ArrayList<>();
  protected Map<Point, Integer> opponentPath = new HashMap<>();

  @Override
  public void initialize(int boardSize, int maxInventorySize, int maxCharge, int winningScore,
      PlayerBoardView startingBoard, Point startTileLocation, boolean isRedPlayer, Random random) {
    // store incoming data.
    this.boardSize = boardSize;
    this.maxInventorySize = maxInventorySize;
    this.maxCharge = maxCharge;
    this.winningScore = winningScore;
    this.startingBoard = startingBoard;
    this.isRedPlayer = isRedPlayer;
    // initialize customized data storage.
    this.score = 0;
    this.inventoryItemList = new ArrayList<>();
    this.functionalTileMap = new HashMap<>();
    // update functional tile location.
    updateFunctionalTileLocation();
    oppo_charge = maxCharge;
    opponentMarket = isRedPlayer ? TileType.BLUE_MARKET : TileType.RED_MARKET;
    marketType = isRedPlayer ? TileType.RED_MARKET : TileType.BLUE_MARKET;
  }

  @Override
  public TurnAction getTurnAction(PlayerBoardView boardView, Economy economy, int currentCharge,
      boolean isRedTurn) {
    // store data.
    this.currentBoard = boardView;
    this.currentCharge = currentCharge;
    this.currentEconomy = economy;
    this.isRedTurn = isRedTurn;
    this.currentPoint = currentBoard.getYourLocation();
    this.itemOnGround = currentBoard.getItemsOnGround();
    Point rechargeTile = findFunctionTile(currentPoint, TileType.RECHARGE);
    TurnAction turnAction;

    // set radius of tile that need to calculate priority index
    int radius = boardSize;
    // get all neighbor tiles including current point.
    List<Point> allNeighbors = getNeighborPoint(currentPoint, radius);
    // get prioirty map of all neighbor point.
    Map<Point, Double> priorityMap = getPriorityMap(currentPoint, allNeighbors);
    sortList = entriesSortedByValues(priorityMap);


    // find which market is opponent nearest market
    Point opponentPoint = currentBoard.getOtherPlayerLocation();
    oppoNowMarket = findFunctionTile(opponentPoint, opponentMarket);

    updateOpponentInfo();
    if (score > currentBoard.getOtherPlayerScore() + opponentSellPrize() && inventoryItemList.size() >= 3) {
      if (!opponentHasDefend()) {
        //return blockMarket();
      }

      Point oppo_current_point = currentBoard.getOtherPlayerLocation();
      Point oppo_recharge_point = findFunctionTile(oppo_current_point, TileType.RECHARGE);
      int oppo_to_recharge_step = getSteps(oppo_current_point, oppo_recharge_point);
      if (oppo_to_recharge_step <= oppo_charge && !currentBoard.getTileTypeAtLocation(currentPoint).equals(TileType.RECHARGE)) {
        //return blockRecharge();
      }
    }


    if (canWin()) {
      turnAction = getTurn_SellMode(); // end the game in shortest turns.
    } else if (currentCharge != maxCharge && currentPoint.equals(rechargeTile)) {
      turnAction = null; // continue staying until recharge to full energy
    } else if (currentCharge <= getSteps(currentPoint, rechargeTile)) {
      turnAction =  getTurn_RechargeMode(); // under recharge mode, go to recharge tile
    } else if (inventoryItemList.size() == maxInventorySize) {
      turnAction = getTurn_SellMode(); // under sell mode
    } else {
      turnAction = getTurn_MineMode(); // under mine mode.
    }
    return turnAction;
  }

  @Override
  public void onReceiveItem(InventoryItem itemReceived) {
    inventoryItemList.add(itemReceived);
  }

  @Override
  public void onSoldInventory(int totalSellPrice) {
    score += totalSellPrice;
    inventoryItemList.clear();
  }

  @Override
  public String getName() {
    return "OCELOT";
  }

  @Override
  public void endRound(int pointsScored, int opponentPointsScored) {
    this.currentBoard = null;
    this.currentEconomy = null;
    this.isRedTurn = false;
    this.currentPoint = null;
    score = 0;

    inventoryItemList.clear();
    allResources.clear();
    oppo_charge = maxCharge;
    oppoInventoryList.clear();
    opponentMarket = null;
    opponentPath.clear();
  }

  /**
   * get all point in the radius with current point as center.
   * @param point currrent point.
   * @return the list of available point.
   */
  private List<Point> getNeighborPoint(Point point, int radius) {
    int boundaryIndex = boardSize - 1;
    List<Point> neighborPoint = new ArrayList<>();
    for (int x = point.x - radius; x <= point.x + radius; x++) {
      for (int y = point.y - radius; y <= point.y + radius; y++) {
        if (x >= 0 && x <= boundaryIndex && y >= 0 && y <= boundaryIndex) {
          neighborPoint.add(new Point(x, y));
        }
      }
    }
    return neighborPoint;
  }

  /**
   * calculate efficiency of mining each tile in the list based on current point.
   * @param currentPoint current location.
   * @param pointList list of Point that need to calculate efficiency.
   * @return a map of efficiency. Key is each point, value is the efficiency of that point.
   */
  private Map<Point, Double> getPriorityMap(Point currentPoint, List<Point> pointList) {
    // key is each point, value is the efficiency of the point.
    Map<Point, Double> priorityMap = new HashMap<>();
    // calculate efficiency of each point in the list.
    for(Point p : pointList) {
      int steps = getSteps(currentPoint, p);
      double efficiency = getEfficiency(p, steps);
      priorityMap.put(p, efficiency);
    }
    // return result
    return priorityMap;
  }

  /**
   * get efficiency of mining current tile.
   * @param point current point.
   * @return turns efficiency (price / turns to get this tile).
   */
  private double getEfficiency (Point point, int step) {
    ResourceType resourceType;
    double efficiency = 0;
    int turnsToPick = 1;
    // find what resource type of current point.
    switch (currentBoard.getTileTypeAtLocation(point)) {
      case RESOURCE_DIAMOND:
        resourceType = ResourceType.DIAMOND;
        break;
      case RESOURCE_EMERALD:
        resourceType = ResourceType.EMERALD;
        break;
      case RESOURCE_RUBY:
        resourceType = ResourceType.RUBY;
        break;
      default:
        resourceType = null;
    }

    // if the resource type is either diamond, emerald, or ruby
    if (resourceType != null) {
      // determine turns to mine this tile.
      int turnsToMine = resourceType.getTurnsToMine();
      // get current price for this item.
      int price = currentEconomy.getCurrentPrices().get(resourceType);
      // calculate efficiency.
      if (inventoryItemList.size() == maxInventorySize - 1) {
        efficiency = (double) price / (turnsToMine + turnsToPick + step + getSteps(point, findFunctionTile(point, marketType)));
      } else if (currentCharge < getSteps(point, findFunctionTile(point, TileType.RECHARGE))) {
        efficiency = (double) price / (turnsToMine + turnsToPick + step + getSteps(point, findFunctionTile(point, TileType.RECHARGE)));
      } else {
        efficiency = (double) price / (turnsToMine + turnsToPick + step);
      }

    }
    // if there's an item left on this tile.
    if (itemOnGround.containsKey(point)) {

      if (inventoryItemList.size() == maxInventorySize - 1) {
        efficiency = (double) currentEconomy
            .getCurrentPrices()
            .get(itemOnGround.get(point).getItemType()) / (step + turnsToPick + getSteps(point, findFunctionTile(point, marketType)));
      } else if (currentCharge < getSteps(point, findFunctionTile(point, TileType.RECHARGE))) {
        efficiency = (double) currentEconomy
            .getCurrentPrices()
            .get(itemOnGround.get(point).getItemType()) / (step + turnsToPick + getSteps(point, findFunctionTile(point, TileType.RECHARGE)));
      } else {
        efficiency = (double) currentEconomy
            .getCurrentPrices()
            .get(itemOnGround.get(point).getItemType()) / (step + turnsToPick);
      }
    }
    return efficiency;
  }

  /**
   * get number of steps from one point to another.
   * @param startPoint starting point.
   * @param targetPoint target point.
   * @return steps to get from current point to target point.
   */
  private int getSteps(Point startPoint, Point targetPoint) {
    return Math.abs(startPoint.x - targetPoint.x) + Math.abs(startPoint.y - targetPoint.y);
  }

  /**
   * find all locations of recharge, red store, and blue store.
   */
  private void updateFunctionalTileLocation() {
    for (int xCoordinate = 0; xCoordinate < boardSize; xCoordinate++) {
      for (int yCoordinate = 0; yCoordinate < boardSize; yCoordinate++) {
        Point point = new Point(xCoordinate, yCoordinate);
        TileType tileType = startingBoard.getTileTypeAtLocation(xCoordinate, yCoordinate);
        switch (tileType) {
          case RECHARGE:
          case RED_MARKET:
          case BLUE_MARKET:
            functionalTileMap.put(point, tileType);
            break;
          case RESOURCE_RUBY:
          case RESOURCE_DIAMOND:
          case RESOURCE_EMERALD:
            allResources.put(point, tileType);
        }
      }
    }
  }

  /**
   * get nearest point to recharge.
   * @param startPoint current point.
   * @param tileType the tile type that is looking for
   * @return nearest recharge tile.
   */
  private Point findFunctionTile(Point startPoint, TileType tileType) {
    // initialize near point as first entry of the map.
    Point nearestPoint = null;
    // find the nearest
    for (Point p : functionalTileMap.keySet()) {
      if (functionalTileMap.get(p).equals(tileType)) {
        if (nearestPoint == null || getSteps(startPoint, nearestPoint) > getSteps(startPoint, p)) {
          nearestPoint = p;
        }
      }
    }
    return nearestPoint;
  }

  /**
   * check if there's item on the ground on the path.
   * @param startPoint start point.
   * @param endPoint end point.
   * @return whether there's any resource to pick up.
   */
  private boolean hasResourceOnPath(Point startPoint, Point endPoint) {
    for (Point p : currentBoard.getItemsOnGround().keySet()) {
      if (getSteps(p, startPoint) + getSteps(p,endPoint) == getSteps(startPoint, endPoint)) {
        return true;
      }
    }
    return false;
  }

  /**
   * get how player should turn under recharge mode.
   * @return best turn action under recharge mode.
   */
  private TurnAction getTurn_RechargeMode() {
    if (currentBoard.getItemsOnGround().containsKey(currentPoint)) {
      allResources.remove(currentPoint);
      return TurnAction.PICK_UP;
    }
    // get nearest recharge tile location.
    Point rechargeLocation = findFunctionTile(currentPoint, TileType.RECHARGE);
    // get all turns available to reach the recharge tile.
    List<TurnAction> availableTurns = getAvailableTurns(currentPoint, rechargeLocation);
    // if there's resource on the ground, pick it up on the way to recharge
    if (hasResourceOnPath(currentPoint, new Point(currentPoint.x, rechargeLocation.y))) {
      return availableTurns.contains(TurnAction.MOVE_UP) ? TurnAction.MOVE_UP : TurnAction.MOVE_DOWN;
    }
    // otherwise, randomly select a turn action.
    return availableTurns.remove(0);
  }

  /**
   * get how player should turn under mine mode.
   * @return best turn action under mine mode.
   */
  private TurnAction getTurn_MineMode() {
    // if current point has item to pick , then pick up.
    if (currentBoard.getItemsOnGround().containsKey(currentPoint)) {
      allResources.remove(currentPoint);
      return TurnAction.PICK_UP;
    }

    // if current point is the best, then mine. Else go to the best point.
    if (currentPoint.equals(sortList.get(0).getKey())) {
      return TurnAction.MINE;
    }
    return getAvailableTurns(currentPoint, sortList.get(0).getKey()).get(0);
  }

  /**
   * get how player should turn under recharge mode.
   * @return best turn action under recharge mode.
   */
  private TurnAction getTurn_SellMode() {
    // get corresponding market. i.e. red or blue
    TileType requiredTileType = isRedPlayer ? TileType.RED_MARKET : TileType.BLUE_MARKET;
    // find nearest market.
    Point marketLocation = findFunctionTile(currentPoint, requiredTileType);
    if (marketLocation.equals(currentPoint)) {
      return null; // if currently at the market, return null;
    }

    if (currentBoard.getOtherPlayerLocation().equals(marketLocation)) {
      for (Point p : functionalTileMap.keySet()) {
        if (!p.equals(marketLocation) && functionalTileMap.get(p).equals(requiredTileType)) {
          marketLocation = p;
          break;
        }
      }
    }
    // else randomly select a turn to get to the market.
    return getAvailableTurns(currentPoint, marketLocation).get(0);
  }

  /**
   * get turns that enable shortest path from start point to end point.
   * @param startPoint start point.
   * @param endPoint end point.
   * @return get turns that create shortest path.
   */
  private List<TurnAction> getAvailableTurns(Point startPoint, Point endPoint) {
    List<TurnAction> turnActions = new ArrayList<>();
    int xDifference = startPoint.x - endPoint.x;
    int yDifference = startPoint.y - endPoint.y;
    // deal with x orientation
    if (xDifference > 0) {
      turnActions.add(TurnAction.MOVE_LEFT);
    } else if (xDifference < 0) {
      turnActions.add(TurnAction.MOVE_RIGHT);
    }
    // deal with y orientation
    if (yDifference > 0) {
      turnActions.add(TurnAction.MOVE_DOWN);
    } else if (yDifference < 0) {
      turnActions.add(TurnAction.MOVE_UP);
    }
    // if it's my turn, no need to consider collision
    if (isRedPlayer == isRedTurn) {
      return turnActions;
    }
    // otherwise, avoid collision
    turnActions.removeIf(
        turnAction -> willCollide(currentPoint, currentBoard.getOtherPlayerLocation(), turnAction));
    if (turnActions.size() == 0) {
      turnActions.add(null);
    }
    return turnActions;
  }

  /**
   * calculate total value of items in inventory
   * @return total value of items in inventory
   */
  private boolean canWin () {
    // get nearest market
    Point nearestMarket = isRedPlayer ? findFunctionTile(currentPoint, TileType.RED_MARKET)
        : findFunctionTile(currentPoint, TileType.BLUE_MARKET);
    // count steps to this market
    int stepsToMarket = getSteps(currentPoint, nearestMarket);
    // calculate the price of each resource when the player arrive the store
    Map<ResourceType, Integer> expectedPrice = currentEconomy.getCurrentPrices();
    for(ResourceType resourceType : expectedPrice.keySet()) {
      int price = expectedPrice.get(resourceType);
      price += resourceType.getPriceIncreasePerTurn() * stepsToMarket;
      expectedPrice.put(resourceType, Math.min(price, resourceType.getMaxPrice()));
    }
    // calculate expected score based on expected price.
    int expectedScore = score;
    for (InventoryItem inventoryItem : inventoryItemList) {
      expectedScore += expectedPrice.get(inventoryItem.getItemType());
    }
    return expectedScore >= winningScore;
  }

  /**
   * check if attempt turn action will collide with opponent.
   * @param myPoint my current point.
   * @param opponentPoint opponent current point.
   * @param turnAction attempt turn action.
   * @return whether attempt action will collide with opponent.
   */
  private boolean willCollide(Point myPoint, Point opponentPoint, TurnAction turnAction) {
    // if distance between the player and opponent is too far, they cannot collide.
    if (getSteps(myPoint, opponentPoint) > 1 ) {
      return false;
    }

    if (isRedTurn == isRedPlayer) {
      return false; // if it's my turn ,cannot collide.
    }

    if (myPoint.x > opponentPoint.x && turnAction.equals(TurnAction.MOVE_LEFT)) {
      return true; // opponent on the left
    } else if (myPoint.x < opponentPoint.x && turnAction.equals(TurnAction.MOVE_RIGHT)) {
      return true; // opponent on the right
    } else if (myPoint.y > opponentPoint.y && turnAction.equals(TurnAction.MOVE_DOWN)) {
      return true; // opponent on the lower tile
    }
    return myPoint.y < opponentPoint.y && turnAction.equals(TurnAction.MOVE_UP); // opponent on the upper tile
  }

  /**
   * reverse sort the map.
   * This function is cited from
   * https://stackoverflow.com/questions/11647889/sorting-the-mapkey-value-in-descending-order-based-on-the-value
   * @param map the map that need to sort.
   * @param <K> object type of map key
   * @param <V> object type of map value
   * @return reverse sorted entries.
   */
  private static <K,V extends Comparable<? super V>> List<Entry<K, V>> entriesSortedByValues(Map<K,V> map) {
    List<Entry<K,V>> sortedEntries = new ArrayList<>(map.entrySet());
    sortedEntries.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
    return sortedEntries;
  }

  private TurnAction blockMarket() {
    Point opponentPoint = currentBoard.getOtherPlayerLocation();
    if (oppoNowMarket.equals(currentPoint)) {
      return null;
    }
    if (getSteps(opponentPoint, oppoNowMarket) > 2 && getSteps(currentPoint, oppoNowMarket) == 1) {
      return null;
    }
    return getAvailableTurns(currentPoint, oppoNowMarket).get(0);
  }

  private boolean opponentHasDefend() {
    if (oppo_has_defend) {
      return true;
    }

    if (oppoPreviousMarket == null) {
      oppoPreviousMarket = oppoNowMarket;
    } else if (!oppoPreviousMarket.equals(oppoNowMarket)) {
      oppo_has_defend = true;
      return true;
    }
    return false;
  }

  private void updateOpponentInfo() {
    Point oppo_current_point = currentBoard.getOtherPlayerLocation();
    opponentPath.merge(oppo_current_point, 1, Integer::sum);

    if (currentBoard.getTileTypeAtLocation(oppo_current_point).equals(opponentMarket)) {
      oppoInventoryList.clear();
    }
    for (Point p : allResources.keySet()) {
      if (currentBoard.getTileTypeAtLocation(p).equals(TileType.EMPTY) && opponentPath.containsKey(p) && opponentPath.get(p) >= 2) {
        switch (allResources.get(p)) {
          case RESOURCE_RUBY:
            oppoInventoryList.add(new InventoryItem(ResourceType.RUBY));
            break;
          case RESOURCE_DIAMOND:
            oppoInventoryList.add(new InventoryItem(ResourceType.DIAMOND));
            break;
          case RESOURCE_EMERALD:
            oppoInventoryList.add(new InventoryItem(ResourceType.EMERALD));
            break;
        }
        allResources.remove(p);
        break;
      }
    }
    // if opponent reach recharge tile, increase his battery.
    if (currentBoard.getTileTypeAtLocation(oppo_current_point).equals(TileType.RECHARGE)) {
      oppo_charge += maxCharge * 0.1;
      oppo_charge = Math.min(oppo_charge, maxCharge);
    }
  }

  private TurnAction blockRecharge() {
    Point oppo_current_point = currentBoard.getOtherPlayerLocation();
    Point oppo_recharge_point = findFunctionTile(oppo_current_point, TileType.RECHARGE);

    if (!currentPoint.equals(oppo_recharge_point)) {
      return getAvailableTurns(currentPoint, oppo_recharge_point).get(0);
    } else {
      return null;
    }
  }

  private boolean shouldSellFirst() {
    if (oppoInventoryList.size() != maxInventorySize) {
      return false;
    }


    TileType requiredTileType = isRedPlayer ? TileType.RED_MARKET : TileType.BLUE_MARKET;
    if (getSteps(currentPoint, findFunctionTile(currentPoint, requiredTileType))
        >= getSteps(currentBoard.getOtherPlayerLocation(), oppoNowMarket)) {
      return false;
    }
    int oppo_diamond = 0;
    int oppo_emerald = 0;
    int oppo_ruby = 0;

    for (InventoryItem inventoryItem : oppoInventoryList) {
      switch (inventoryItem.getItemType()) {
        case DIAMOND:
          oppo_diamond++;
          break;
        case RUBY:
          oppo_ruby++;
          break;
        case EMERALD:
          oppo_emerald++;
          break;
      }
    }

    int my_diamond = 0;
    int my_emerald = 0;
    int my_ruby = 0;

    for (InventoryItem inventoryItem : inventoryItemList) {
      switch (inventoryItem.getItemType()) {
        case DIAMOND:
          my_diamond++;
          break;
        case RUBY:
          my_ruby++;
          break;
        case EMERALD:
          my_emerald++;
          break;
      }
    }

    if (oppo_diamond > maxInventorySize / 2 && my_diamond > maxInventorySize
        || oppo_ruby > maxInventorySize / 2 && my_ruby > maxInventorySize
        || oppo_emerald > maxInventorySize / 2 && my_emerald > maxInventorySize) {
      return true;
    }
    return false;
  }

  private int opponentSellPrize() {
    int score = 0;
    Map<ResourceType, Integer> priceMap = currentEconomy.getCurrentPrices();
    for (InventoryItem inventoryItem : oppoInventoryList) {
      score += priceMap.get(inventoryItem.getItemType());
    }
    return score;
  }

}