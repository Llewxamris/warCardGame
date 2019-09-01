package com.maxwellhaley.war.core.gm;

import com.maxwellhaley.war.core.model.Card;
import com.maxwellhaley.war.core.model.Deck;
import com.maxwellhaley.war.core.model.Pot;
import com.maxwellhaley.war.core.model.player.CpuPlayer;
import com.maxwellhaley.war.core.model.player.HumanPlayer;
import com.maxwellhaley.war.core.model.player.Player;
import com.maxwellhaley.war.core.result.AbstractPhaseResult;
import com.maxwellhaley.war.core.result.AbstractStandoffPhaseResult;
import com.maxwellhaley.war.core.result.AbstractWarPhaseResult;
import com.maxwellhaley.war.core.result.BettingPhaseResult;
import com.maxwellhaley.war.core.result.Outcome;
import com.maxwellhaley.war.core.result.StandoffPhaseResult;
import com.maxwellhaley.war.core.result.WarPhaseResult;

/**
 * The GM manages the state and status of the game. After receiving inputs from
 * the player(s), the UI layer will call the GM to process that information. For
 * example: After the UI has taken bets inputs from the players, the UI should
 * pass the bet values to the GM to increase the pot value.
 * 
 * @author Maxwell Haley
 * @version 2.0.0
 * @since 2019-07-16
 */
public class GameMaster {
  /** The deck of cards. */
  protected Deck deck;

  /** The first player. */
  private Player playerOne;

  /** The second player. */
  private Player playerTwo;

  /** The Pot. */
  private Pot thePot;

  /** Can not instantiate the Game Master typically. */
  protected GameMaster() {
    deck = new Deck();
    thePot = new Pot();
  }

  /** Sync-less Singleton implementation. */
  private static class GameMasterHelper {
    private static final GameMaster INSTANCE = new GameMaster();
  }

  /**
   * Get the instance of the Game Master.
   * 
   * @return The Game Master itself.
   */
  public static GameMaster getInstance() {
    return GameMasterHelper.INSTANCE;
  }

  /**
   * Player Two's name can be auto-generated by the GM. The UI requires a way to
   * retrieve it.
   * 
   * @return Player Two's Name
   */
  public String playerTwosName() {
    return playerTwo.getName();
  }

  /**
   * Registers the player to the GM. Assumes the second player is a CPU, and
   * generates the CPU players name.
   * 
   * @param p1Name - The human player's name
   */
  public void register(String p1Name) {
    playerOne = new HumanPlayer(p1Name);
    playerTwo = new CpuPlayer();
  }

  /**
   * Creates both players.
   * 
   * @see Player
   * @param p1Name - The first player's name
   * @param p2Name - The second player's name
   */
  public void register(String p1Name, String p2Name) {
    playerOne = new HumanPlayer(p1Name);
    playerTwo = new HumanPlayer(p2Name);
  }

  /**
   * Run the betting phase of the game. During this phase, the players bets are
   * evaluated to determine if they are valid. If they are valid, they are
   * removed from their total cash pool and added to the pot.
   * 
   * @param p1Bet - Player One's bet
   * @param p2Bet - Player Two's bet
   * @see Outcome
   * @return BettingPhaseResult - The result of the betting phase
   */
  public AbstractPhaseResult runBettingPhase(int p1Bet, int p2Bet) {
    if (p1Bet > playerOne.getCash()) {
			playerOne.getCash();
      return new BettingPhaseResult(Outcome.PLAYER_1_BET_FAIL,
              0, playerOne.getCash(), playerTwo.getCash());
    } else if (p2Bet > playerTwo.getCash()) {
      return new BettingPhaseResult(Outcome.PLAYER_2_BET_FAIL,
              0, playerOne.getCash(), playerTwo.getCash());
    } else {
      playerOne.subtractCash(p1Bet);
      playerTwo.subtractCash(p2Bet);
      thePot.addCash(p1Bet + p2Bet);
      return new BettingPhaseResult(Outcome.BET_SUCCESS, thePot.getValue(),
              playerOne.getCash(), playerTwo.getCash());
    }
  }

  /**
   * Run the standoff phase of the game. During this phase, each player is dealt
   * a card. If Player One has a greater card than Player Two, Player One wins
   * the standoff. If Player Two has a greater card, Player Two wins the
   * standoff. If both cards are equal, the <b>WAR!</b> phase begins.
   * 
   * @return StandoffPhaseResult - Result of the standoff phase
   * @see Card
   * @see Outcome
   * @see GameMaster#runWarPhase(boolean, boolean)
   */
  public AbstractStandoffPhaseResult runStandoffPhase() {
    StandoffPhaseResult result = null;
    playerOne.setCard(dealCard());
    playerTwo.setCard(dealCard());

    switch (playerOne.getCard().compareTo(playerTwo.getCard())) {
      case 1:
        playerOne.addCash(thePot.getValue());
        result = new StandoffPhaseResult(Outcome.PLAYER_1_WIN,
                thePot.getValue(),
                playerOne.getCash(), playerTwo.getCash(), playerOne.getCard(),
                playerTwo.getCard());
        thePot.clearValue();
        break;
      case -1:
        playerTwo.addCash(thePot.getValue());
        result = new StandoffPhaseResult(Outcome.PLAYER_2_WIN,
                thePot.getValue(),
                playerOne.getCash(), playerTwo.getCash(), playerOne.getCard(),
                playerTwo.getCard());
        thePot.clearValue();
        break;
      case 0:
        result = new StandoffPhaseResult(Outcome.TIE, thePot.getValue(),
                playerOne.getCash(), playerTwo.getCash(), playerOne.getCard(),
                playerTwo.getCard());
        break;
    }

    return result;
  }

  /**
   * Run the <b>WAR!</b> phase of the game. During this phase, three cards are
   * burnt and each player is dealt two new cards. The rest of the phase plays
   * out similar to the {@link GameMaster#runStandoffPhase()}, except each
   * player has the option of taking a risk. If the winner took the risk, the
   * dealer draws a card. If the winners card is greater than the dealers card,
   * nothing happens. If the winners card is less than the dealers card, the
   * winnings are halved. If the winners card is the same as the dealers card,
   * the winnings are doubled. A tie during <b>WAR!</b> should result in another
   * <b>WAR!</b> being ran.
   * 
   * @param p1Risk - Is Player One taking a risk?
   * @param p2Risk - Is Player Two taking a risk?
   * @return The <b>WAR!</b> phase results
   * @see GameMaster#runStandoffPhase()
   * @see Outcome
   */
  public AbstractWarPhaseResult runWarPhase(boolean p1Risk, boolean p2Risk) {
    WarPhaseResult result = null;

    // Burn three cards
    dealCard();
    dealCard();
    dealCard();

    // Deal dealers card if a risk was taken
    Card dealersCard = null;
    if (p1Risk || p2Risk) {
      dealersCard = dealCard();
    }

    // Deal players cards
    playerOne.setCard(dealCard());
    playerTwo.setCard(dealCard());

    // Determine the winner
    Outcome riskResult = null;
    switch (playerOne.getCard().compareTo(playerTwo.getCard())) {
      case 1:
        if (p1Risk) {
          riskResult = getRiskResult(playerOne.getCard(), dealersCard);
        }
        playerOne.addCash(thePot.getValue());
        thePot.clearValue();
        result = new WarPhaseResult(Outcome.PLAYER_1_WIN, thePot.getValue(),
                playerOne.getCash(), playerTwo.getCash(), playerOne.getCard(),
                playerTwo.getCard(),
                riskResult, dealersCard);
        break;
      case -1:
        if (p2Risk) {
          riskResult = getRiskResult(playerTwo.getCard(), dealersCard);
        }
        playerTwo.addCash(thePot.getValue());
        thePot.clearValue();
        result = new WarPhaseResult(Outcome.PLAYER_2_WIN, thePot.getValue(),
                playerOne.getCash(), playerTwo.getCash(), playerOne.getCard(),
                playerTwo.getCard(),
                riskResult, dealersCard);
        break;
      case 0:
        result = new WarPhaseResult(Outcome.TIE, thePot.getValue(), playerOne.getCash(),
                playerTwo.getCash(), playerOne.getCard(),
                playerTwo.getCard(),
                riskResult, dealersCard);
        break;
    }

    return result;
  }

  /**
   * Runs through the risk possibilities and returns the risk result.
   * 
   * @param playerCard
   * @param dealersCard
   * @return The risk result
   */
  private Outcome getRiskResult(Card playerCard, Card dealersCard) {
    // TODO: Need to refactor this with GameMaster#runWarPhase(). Calling this
    // method to mutate the pot outside of the control of the calling method is
    // wrong.
    switch (playerCard.compareTo(dealersCard)) {
      case 1:
        return Outcome.RISK_NEUTRAL;
      case -1:
        thePot.halveValue();
        return Outcome.RISK_LOSE;
      default:
        thePot.doubleValue();
        return Outcome.RISK_WIN;
    }
  }

  /**
   * Deal a card, and shuffles the deck if no more cards are remaining.
   * 
   * @return Card - The next card in the deck.
   * @see Card
   */
  private Card dealCard() {
    if (deck.size() == 0) {
      deck = new Deck();
    }
    return deck.deal();
  }
}
