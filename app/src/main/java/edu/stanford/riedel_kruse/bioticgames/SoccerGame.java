package edu.stanford.riedel_kruse.bioticgames;

import android.util.Log;

import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dchiu on 11/8/14.
 */
public class SoccerGame
{
    public static final int DEFAULT_BALL_RADIUS = 50;
    public static final int DEFAULT_GOAL_WIDTH = 10;
    public static final int DEFAULT_GOAL_HEIGHT = 400;
    /**
     * How close the ball has to be to the edge of the screen to be considered out of bounds.
     * Required because the ball cannot technically actually leave the screen.
     */
    public static final int BOUNDS_BUFFER = 20;
    public static final int PREVIOUS_LOCATIONS_TO_TRACK = 10;
    public static final double FRAMES_PER_PASS = 15;
    public static final double PASS_DISTANCE = 200;
    public static final String TAG = "edu.stanford.riedel-kruse.bioticgames.SoccerGame";

    public enum Turn
    {
        RED,
        BLUE
    }

    private int mFieldWidth;
    private int mFieldHeight;
    private int mBallRadius;
    private Point mBallLocation;
    private List<Point> mPreviousBallLocations;
    private Point mPassingDirection;
    private int mRedPlayerPoints;
    private int mBluePlayerPoints;
    private Turn mCurrentTurn;
    private boolean mPassing;
    private int mPassingFrames;

    private SoccerGameDelegate mDelegate;

    private Rect mRedGoal;
    private Rect mBlueGoal;

    public SoccerGame(int fieldWidth, int fieldHeight, SoccerGameDelegate delegate)
    {
        mFieldWidth = fieldWidth;
        mFieldHeight = fieldHeight;

        mDelegate = delegate;

        mPassingDirection = new Point(0, 0);
        mPreviousBallLocations = new ArrayList<Point>();

        mRedGoal = new Rect();
        mBlueGoal = new Rect();
        reset();
    }

    /**
     * Resets the game state.
     */
    public void reset()
    {
        resetBall();
        mBallRadius = DEFAULT_BALL_RADIUS;
        mRedPlayerPoints = 0;
        mBluePlayerPoints = 0;
        mCurrentTurn = Turn.RED;
        mPassing = false;

        // Reset the goal locations, heights, and widths.
        mRedGoal.x = 0;
        mBlueGoal.x = mFieldWidth - DEFAULT_GOAL_WIDTH;
        mRedGoal.y = mBlueGoal.y = (mFieldHeight - DEFAULT_GOAL_HEIGHT) / 2;
        mRedGoal.width = mBlueGoal.width = DEFAULT_GOAL_WIDTH;
        mRedGoal.height = mBlueGoal.height = DEFAULT_GOAL_HEIGHT;
    }

    public void passBall()
    {
        // If the ball is already being passed, do nothing.
        if (mPassing)
        {
            return;
        }
        mPassing = true;
        mPassingFrames = 0;

        // Count this as the first passing frame.
        passingFrame();
    }

    public void passingFrame()
    {
        if (mPassingFrames >= FRAMES_PER_PASS)
        {
            mPassing = false;
            mPassingFrames = 0;
            // Just finished passing, so we should clear all data about the movement direction.
            // Otherwise the player will be able to spam the pass button and continuously pass in
            // the same direction.
            resetPassingDirection();
            return;
        }

        Point newLocation = new Point(mBallLocation.x + ((float) PASS_DISTANCE / FRAMES_PER_PASS) *
                mPassingDirection.x, mBallLocation.y + ((float) PASS_DISTANCE / FRAMES_PER_PASS) *
                mPassingDirection.y);

        mPassingFrames++;

        Log.d(TAG, "ballLocation.x " + mBallLocation.x);
        Log.d(TAG, "ballLocation.y " + mBallLocation.y);

        updateBallLocation(newLocation);
    }

    public Point getBallLocation()
    {
        return mBallLocation;
    }

    public int getBallRadius()
    {
        return mBallRadius;
    }

    public int getBluePlayerPoints()
    {
        return mBluePlayerPoints;
    }

    public Turn getCurrentTurn()
    {
        return mCurrentTurn;
    }

    public int getRedPlayerPoints()
    {
        return mRedPlayerPoints;
    }

    public int getFieldWidth()
    {
        return mFieldWidth;
    }

    public int getFieldHeight()
    {
        return mFieldHeight;
    }

    public Point getPassingDirection()
    {
        return mPassingDirection.clone();
    }

    public boolean isPassing()
    {
        return mPassing;
    }

    /**
     * Resets the ball by settings its location to the center of the field, clearing the movement
     * direction, and clearing all previously stored ball locations.
     */
    private void resetBall()
    {
        mBallLocation = new Point(mFieldWidth / 2.0, mFieldHeight / 2.0);
        resetPassingDirection();
    }

    private void resetPassingDirection()
    {
        mPreviousBallLocations.clear();
        mPassingDirection.x = 0;
        mPassingDirection.y = 0;
    }

    /**
     * Updates the internal state of the soccer game with a new location for the ball.
     * @param newLocation The new location of the ball.
     */
    public void updateBallLocation(Point newLocation)
    {
        if (newLocation == null)
        {
            return;
        }

        mBallLocation = newLocation;

        boolean outOfBounds = checkForOutOfBounds();

        // If we are in the middle of passing and the ball is out of bounds, then we should bounce
        // off the walls.
        //if (mPassing && outOfBounds)
        //{
        //    bounceOffWalls();
        //}
        // Otherwise if a goal is scored or if we are not passing and the ball is out of bounds we
        // should reset the ball and change the turn.
        //else if (checkForGoal() || (!mPassing && outOfBounds))
        if (checkForGoal() || outOfBounds)
        {
            resetBall();
            changeTurn();
            return;
        }

        // If the ball is not being passed, then update the movement direction. We don't want to do
        // this while the ball is being passed because the ball's direction does not change while it
        // is being passed.
        if (!mPassing)
        {
            updatePassingDirection();
        }
    }

    private void bounceOffWalls()
    {
        if (mBallLocation.x < BOUNDS_BUFFER || mBallLocation.x > mFieldWidth - BOUNDS_BUFFER)
        {
            mPassingDirection.x *= -1;
        }
        else if (mBallLocation.y < BOUNDS_BUFFER || mBallLocation.y > mFieldHeight - BOUNDS_BUFFER)
        {
            mPassingDirection.y *= -1;
        }
    }

    /**
     * Changes the turn to the next player.
     */
    private void changeTurn()
    {
        if (mCurrentTurn == Turn.RED)
        {
            mCurrentTurn = Turn.BLUE;
        }
        else
        {
            mCurrentTurn = Turn.RED;
        }

        if (mDelegate != null)
        {
            mDelegate.onChangedTurn(mCurrentTurn);
        }
    }

    /**
     * Checks to see if the ball is currently inside the goal.
     * @return true if the ball is currently inside the goal, false otherwise.
     */
    private boolean checkForGoal()
    {
        // If it's the red player's turn and the ball is inside the blue goal, then a goal has been
        // scored and the red player gets a point.
        if (mCurrentTurn == Turn.RED)
        {
            if (mBallLocation.inside(mBlueGoal))
            {
                mRedPlayerPoints++;
                // If there is a delegate, let the delegate know that a goal was scored so it can do
                // whatever else it wants (e.g. display a notification).
                if (mDelegate != null)
                {
                    mDelegate.onGoalScored(Turn.RED);
                }
                return true;
            }
        }
        // Otherwise, if it's the blue player's turn and the ball is inside the red goal, then a
        // goal has been scored and the blue player gets a point.
        else
        {
            if (mBallLocation.inside(mRedGoal))
            {
                mBluePlayerPoints++;
                // If there is a delegate, let the delegate know that a goal was scored so it can do
                // whatever else it wants (e.g. display a notification).
                if (mDelegate != null)
                {
                    mDelegate.onGoalScored(Turn.BLUE);
                }
                return true;
            }
        }

        return false;
    }

    /**
     * Checks to see if the ball is currently out of bounds.
     * @return true if the ball is out of bounds, false otherwise.
     */
    private boolean checkForOutOfBounds()
    {
        if (mBallLocation.x <= BOUNDS_BUFFER || mBallLocation.y <= BOUNDS_BUFFER ||
                mBallLocation.x >= mFieldWidth - BOUNDS_BUFFER ||
                mBallLocation.y >= mFieldHeight - BOUNDS_BUFFER)
        {
            mDelegate.onOutOfBounds();
            return true;
        }

        return false;
    }

    /**
     * Recomputes the movement direction of the ball using an average of the previous known
     * locations of the ball.
     * TODO: Don't completely recompute the average every time. Use the old average to avoid
     * needless computation.
     */
    private void updatePassingDirection()
    {
        // Add the current location to the previous locations list so it can be used for finding
        // the movement direction of the ball.
        mPreviousBallLocations.add(mBallLocation);

        // If the number of stored previous locations has exceeded the defined limit, remove the
        // oldest previous location.
        if (mPreviousBallLocations.size() > PREVIOUS_LOCATIONS_TO_TRACK)
        {
            mPreviousBallLocations.remove(0);
        }

        int numPreviousLocations = mPreviousBallLocations.size();
        // If we only have one previous location, then we cannot compute any directions since we
        // need two points to define a line.
        if (numPreviousLocations == 1)
        {
            return;
        }

        ArrayList<Point> directionVectors = new ArrayList<Point>();

        // Compute the directions pairwise between the previous locations of the ball
        for (int i = 0; i < numPreviousLocations - 1; i++)
        {
            Point previousPoint = mPreviousBallLocations.get(i);
            Point nextPoint = mPreviousBallLocations.get(i + 1);
            Point directionVector = new Point(nextPoint.x - previousPoint.x,
                    nextPoint.y - previousPoint.y);

            double magnitude = Math.sqrt(Math.pow(directionVector.x, 2) +
                    Math.pow(directionVector.y, 2));

            // Normalize the direction vector to get a unit vector in that direction
            directionVector.x = directionVector.x / magnitude;
            directionVector.y = directionVector.y / magnitude;

            directionVectors.add(directionVector);
        }

        // Reset the passing direction.
        mPassingDirection.x = 0;
        mPassingDirection.y = 0;

        double numDirectionVectors = directionVectors.size();

        // Sum up all of the direction vectors
        for (Point directionVector : directionVectors)
        {
            mPassingDirection.x += directionVector.x;
            mPassingDirection.y += directionVector.y;
        }

        // Divide to compute an average.
        mPassingDirection.x /= numDirectionVectors;
        mPassingDirection.y /= numDirectionVectors;

        double directionMagnitude = Math.sqrt(Math.pow(mPassingDirection.x, 2) +
                Math.pow(mPassingDirection.y, 2));

        // Normalize the direction so that it is just a unit vector.
        mPassingDirection.x /= directionMagnitude;
        mPassingDirection.y /= directionMagnitude;
    }
}
