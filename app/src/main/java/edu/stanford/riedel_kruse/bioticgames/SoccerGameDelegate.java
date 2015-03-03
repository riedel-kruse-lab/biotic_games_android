package edu.stanford.riedel_kruse.bioticgames;

/**
 * Created by dchiu on 11/9/14.
 */
public interface SoccerGameDelegate
{
    public void onChangedDirection(SoccerGame.Direction currentDirection);
    public void onGoalScored(SoccerGame.Direction currentDirection);
    public void onOutOfBounds();
    public void onNonzeroVelocity();
    public void onZeroVelocity();
    public void onGameOver();
}
