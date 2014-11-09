package edu.stanford.riedel_kruse.bioticgames;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by dchiu on 11/8/14.
 */
public abstract class Modifier
{
    protected int mDuration;

    /**
     * Constructor for the modifier object.
     * @param duration How long this modifier's effect should last in milliseconds. A value of 0
     *                 specifies that this is not a time-based modifier.
     */
    public Modifier(int duration)
    {
        mDuration = duration;
    }

    public void apply(SoccerGame game)
    {
        // Call the reset function after the duration is over to reverse the effects of this
        // modifier.
        if (mDuration > 0)
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
                mDuration
            );
        }
    }

    public abstract void reset();
}
