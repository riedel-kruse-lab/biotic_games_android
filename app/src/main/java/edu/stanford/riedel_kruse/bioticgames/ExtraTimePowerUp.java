package edu.stanford.riedel_kruse.bioticgames;

/**
 * Created by dchiu on 11/9/14.
 */
public class ExtraTimePowerUp extends PowerUp
{
    public static long EXTRA_TIME = 2000;

    public ExtraTimePowerUp(int x, int y)
    {
        super(x, y, 0, 5000);
    }

    @Override
    public void apply(SoccerGame game)
    {
        game.addTimeToTurn(EXTRA_TIME);
        super.apply(game);
    }

    /**
     * ExtraTimePowerUp does not need to do anything to reset.
     */
    public void reset()
    {

    }
}
