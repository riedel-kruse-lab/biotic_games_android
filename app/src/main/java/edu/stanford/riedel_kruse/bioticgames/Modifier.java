package edu.stanford.riedel_kruse.bioticgames;

import org.opencv.core.Point;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by dchiu on 11/8/14.
 */
public abstract class Modifier
{
    protected long mActiveDuration;
    protected long mAvailableDuration;
    protected Point mLocation;

    /**
     * Constructor for the modifier object.
     * @param activeDuration How long this modifier's effect should last in milliseconds. A value of
     *                       0 specifies that this is not a time-based modifier.
     * @param availableDuration How long this modifier will sit on the field before disappearing in
     *                          milliseconds.
     */
    public Modifier(int x, int y, long activeDuration, long availableDuration)
    {
        mActiveDuration = activeDuration;
        mAvailableDuration = availableDuration;
        mLocation = new Point(x, y);
    }

    public void apply(SoccerGame game)
    {
        // Call the reset function after the duration is over to reverse the effects of this
        // modifier.
        if (mActiveDuration > 0)
        {
            new Timer().schedule(
                new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        reset();
                    }
                },
                mActiveDuration
            );
        }
    }

    public abstract void reset();
}
