package edu.stanford.riedel_kruse.bioticgames;

/**
 * Created by dchiu on 12/7/14.
 */
public class Tutorial
{
    int[] mStrings = new int[]
        {
            R.string.tutorial_push_joystick, // Index 0
            R.string.tutorial_change_direction,
            R.string.tutorial_play_around,
            R.string.tutorial_euglena_in_ball, // Index 3
            R.string.tutorial_zoom_view,
            R.string.tutorial_direction_tracking, // Index 5
            R.string.tutorial_out_of_bounds,
            R.string.tutorial_tap_to_pass,
            R.string.tutorial_pass_tip,
            R.string.tutorial_bounce_tip,
            R.string.tutorial_play_around, // Index 10
            R.string.tutorial_try_for_goal,
            R.string.tutorial_goal_carry_or_pass,
            R.string.tutorial_score,
            R.string.tutorial_turns, // Index 14
            R.string.tutorial_play_around
        };

    private int mCurrentIndex;
    private boolean mFinished;

    public Tutorial()
    {
        mCurrentIndex = 0;
        mFinished = false;
    }

    public void advance()
    {
        mCurrentIndex++;
        if (mCurrentIndex >= mStrings.length)
        {
            mCurrentIndex = mStrings.length - 1;
            mFinished = true;
        }
    }

    public boolean finished()
    {
        return mFinished;
    }

    public int getCurrentStringResource()
    {
        return mStrings[mCurrentIndex];
    }

    public int getButtonTextResource()
    {
        if (mCurrentIndex != mStrings.length - 1)
        {
            return R.string.tutorial_button_next;
        }
        else
        {
            return R.string.tutorial_button_finish;
        }
    }

    public boolean shouldDrawGoals()
    {
        return mCurrentIndex > 10 || finished();
    }

    public boolean shouldDrawCentroids()
    {
        return mCurrentIndex > 2 || finished();
    }

    public boolean shouldDrawBall()
    {
        return mCurrentIndex > 2 || finished();
    }

    public boolean shouldDrawDirection()
    {
        return mCurrentIndex > 4 || finished();
    }

    public boolean shouldDrawBlinkingArrow()
    {
        return mCurrentIndex > 10 || finished();
    }

    public boolean shouldDisplayVelocity()
    {
        return mCurrentIndex > 4 || finished();
    }

    public boolean shouldTrack()
    {
        return mCurrentIndex > 2 || finished();
    }

    public boolean shouldDisplayCountdown()
    {
        return mCurrentIndex > 13 || finished();
    }

    public boolean shouldDisplayScores()
    {
        return mCurrentIndex > 10 || finished();
    }

    public boolean shouldCountDown()
    {
        return mCurrentIndex > 13 || finished();
    }

    public boolean shouldDisplayActionButton(){return mCurrentIndex > 6 || finished(); }
}
