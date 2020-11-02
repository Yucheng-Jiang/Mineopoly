# Mineopoly

#### Project structure
**MinePlayerStrategy** Interface implemented by **AutoPlayerStrategy**

#### How to start the game
In **MineopolyMain** class, if want to visualize game, set global variable
**TEST_STRATEGY_WIN_PERCENT** as false; else set it as true to get winning percentage.

#### Testing Frameworks
(1) Junit

(2) Mockito

#### Algorithm Logic
(1) If selling all items in inventory list can win, go to market immediately.

(2) If at recharge tile and not fully charges, do nothing.

(3) If inventory list full, go to market

(4) If energy need (to go to nearest recharge tile) is not enough, go recharge

(5) Calculate efficiency of mining each point, then go to the point with highest efficiency.

#### Citation
(1) Reverse sort Map
https://stackoverflow.com/questions/11647889/sorting-the-mapkey-value-in-descending-order-based-on-the-value

(2) Print progress bar
https://stackoverflow.com/questions/852665/command-line-progress-bar-in-java

#### Testing strategy
(1) test over all win rate (Black box test)

(2) test change side will not significantly influence win rate (Black box test)

(3) Test get priority map (helper function)

(4) Test canWin (helper function)

(5) Test willCollide (helper function)

(6) Test getAvailableTurns (helper function)

(7) Test getTurns_SellMode (helper function)

(8) Test getTurns_MineMode (helper function)

(9) Test getTurns_RechargeMode (helper function)
