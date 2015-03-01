package edu.stanford.riedel_kruse.bioticgames;

/**
 * Created by dchiu on 11/9/14.
 */
public interface SoccerGameDelegate
{
    public void onChangedTurn(SoccerGame.Turn currentTurn);
    public void onGoalScored(SoccerGame.Turn currentTurn);
    public void onOutOfBounds();
    public void onPickupButtonPressed(SoccerGame.Turn currentTurn);
    public void onNonzeroVelocity();
    public void onZeroVelocity();
}
