package edu.stanford.riedel_kruse.bioticgames;

/**
 * Created by dchiu on 11/9/14.
 */
public abstract class PowerUp extends Modifier
{
    public PowerUp(int x, int y, long activeDuration, long availableDuration)
    {
        super(x, y, activeDuration, availableDuration);
    }

    public abstract void reset();
}
