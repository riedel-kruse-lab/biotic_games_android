package edu.stanford.riedel_kruse.bioticgames;

import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dchiu on 11/8/14.
 */
public class SoccerGame
{
    public static final long MILLISECONDS_PER_TURN = 30 * 1000;
    public static final int DEFAULT_BALL_RADIUS = 50;
    public static final int DEFAULT_GOAL_WIDTH = 40;
    public static final int DEFAULT_GOAL_HEIGHT = 400;

    /**
     * How close the ball has to be to the edge of the screen to be considered out of bounds.
     * Required because the ball cannot technically actually leave the screen.
     */
    public static final int BOUNDS_BUFFER = 10;
    public static final int PREVIOUS_LOCATIONS_TO_TRACK = 20;
    public static final int PREVIOUS_LOCATIONS_TO_TRACK_VELOCITY = 30;
    public static final long MILLISECONDS_PER_PASS = 3 * 1000;
    // TODO: Need to play with this value to see what feels right.
    public static final double PASS_VELOCITY = 1;
    public static final String TAG = "edu.stanford.riedel-kruse.bioticgames.SoccerGame";
    public static final int NO_PASS_POINTS = 3;
    public static final int NUM_TURNS_PER_GAME = 6;
    public static final double VELOCITY_SCALE = 10;

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
    private long mPassingTime;
    private long mTimeLeftInTurn;
    private int pointsScored;
    private boolean pickupButtonPressed = false;
    private int mTurnCount = 0;
    private double velocity = 0;
    private double mMaxBlueVelocity = 0;
    private double mMaxRedVelocity = 0;
    private Point mVelocityVector = new Point (0,0);
    private boolean mCountdownPaused;
    private boolean mGameOver;

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

        mCountdownPaused = false;

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
        mTurnCount = 0;
        if(mCurrentTurn != Turn.RED)
        {
            changeTurn();
        }
        // TODO: This is pretty hacky
        else
        {
            changeTurn();
            changeTurn();
        }
        mTimeLeftInTurn = MILLISECONDS_PER_TURN;

        // Reset the goal locations, heights, and widths.
        mRedGoal.x = 0;
        mBlueGoal.x = mFieldWidth - DEFAULT_GOAL_WIDTH;
        mRedGoal.y = mBlueGoal.y = (mFieldHeight - DEFAULT_GOAL_HEIGHT) / 2;
        mRedGoal.width = mBlueGoal.width = DEFAULT_GOAL_WIDTH;
        mRedGoal.height = mBlueGoal.height = DEFAULT_GOAL_HEIGHT;

        mGameOver = false;
    }

    public void passBall()
    {
        // If the ball is already being passed, do nothing.
        if (mPassing)
        {
            return;
        }
        velocity = 0;
        mPassing = true;
        mPassingTime = 0;
    }

    public Point getBallLocation()
    {
        return mBallLocation.clone();
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

    public long getTimeLeftInTurn()
    {
        return mTimeLeftInTurn;
    }

    public boolean isPassing()
    {
        return mPassing;
    }

    public boolean isGameOver() {
        return mGameOver;
    }

    /**
     * Resets the ball by settings its location to the center of the field, clearing the movement
     * direction, and clearing all previously stored ball locations.
     */
    private void resetBall()
    {
        mBallLocation = new Point(mFieldWidth / 2.0, mFieldHeight / 2.0);
        resetPassingDirection();
        mPassing = false;
    }

    private void resetPassingDirection()
    {
        mPreviousBallLocations.clear();
        mPassingDirection.x = 0;
        mPassingDirection.y = 0;
    }

    public void updateTime(long timeDelta) {
        if (!mCountdownPaused) {
            mTimeLeftInTurn -= timeDelta;
        }

        if (mTimeLeftInTurn <= 0) {
            changeTurn();
        }
    }

    /**
     * Updates the internal state of the soccer game with a new location for the ball.
     * @param newLocation The new location of the ball.
     */
    public void updateBallLocation(Point newLocation) {
        checkIfPickupButtonPressed();

        if (newLocation == null) {
            mVelocityVector = new Point (0,0);
            velocity = 0;

            return;
        }

        mBallLocation = newLocation;
        updatePassingDirection();
        updateVelocity();
    }

    public void moveBallDuringPass(long timeDelta) {
        // Using constant for how fast ball should move when passed, figure out how far ball would
        // move in the amount of time elapsed in timeDelta
        // Then move the ball that distance.

        double distance = PASS_VELOCITY * timeDelta;

        // TODO: Check this math. This doesn't look quite right.
        mBallLocation.x += distance * mPassingDirection.x;
        mBallLocation.y += distance * mPassingDirection.y;

        // TODO: Ball should move for a certain amount of time each pass. If that much time has
        // elapsed, stop passing.

        mPassingTime += timeDelta;
        if (mPassingTime >= MILLISECONDS_PER_PASS) {
            mPassing = false;
            mPassingTime = 0;
            resetPassingDirection();
        }
    }

    public void checkCollisions() {
        // If a goal is scored.
        if (checkForGoal())
        {
            resetBall();
            return;
        }

        boolean outOfBounds = checkForOutOfBounds();
        // If we are in the middle of passing and the ball is out of bounds, then we should bounce
        // off the walls.
        if (mPassing && outOfBounds)
        {
            bounceOffWalls();
        }
        else if (!mPassing && outOfBounds)
        {
            mDelegate.onOutOfBounds();
            resetBall();
            changeTurn();
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
        mTurnCount++;

        // If we've exceeded the number of turns per game, then the game is over! Let the delegate
        // handle the rest from here, since the delegate will probably want to display things.
        if(mTurnCount > NUM_TURNS_PER_GAME) {
            mGameOver = true;
            // TODO: Maybe need to add some state to show that the game has ended?
            if (mDelegate != null) {
                mDelegate.onGameOver();
            }
            return;
        }

        mPassing = false;

        mVelocityVector = new Point (0,0);
        velocity = 0;

        if (mCurrentTurn == Turn.RED)
        {
            mCurrentTurn = Turn.BLUE;
        }
        else
        {
            mCurrentTurn = Turn.RED;
        }

        // Reset the amount of time left in the turn.
        mTimeLeftInTurn = MILLISECONDS_PER_TURN;

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
                if (!mPassing)
                {
                    mRedPlayerPoints += NO_PASS_POINTS;
                    pointsScored = NO_PASS_POINTS;
                }
                else
                {
                    mRedPlayerPoints++;
                    pointsScored = 1;
                }
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
                if (pickupButtonPressed)
                {
                    mBluePlayerPoints -= 1;
                    pickupButtonPressed = false;
                }
                if (!mPassing)
                {
                    mBluePlayerPoints += NO_PASS_POINTS;
                    pointsScored = NO_PASS_POINTS;
                }
                else
                {
                    mBluePlayerPoints++;
                    pointsScored = 1;
                }
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

    public int getPointsScored()
    {
       return pointsScored;
    }

    private void checkIfPickupButtonPressed()
    {
        if(pickupButtonPressed)
        {

            if(mCurrentTurn == Turn.RED)
            {
                mRedPlayerPoints -= 1;
                pointsScored = -1;
                if (mDelegate != null)
                {
                    mDelegate.onPickupButtonPressed(Turn.RED);
                }
            }
            else
            {
                mBluePlayerPoints -= 1;
                pointsScored = -1;
                if (mDelegate != null)
                {
                    mDelegate.onPickupButtonPressed(Turn.BLUE);
                }
            }
            pickupButtonPressed = false;
        }
    }

    public void setPickupButtonPressedTrue()
    {
        pickupButtonPressed = true;
    }

    public boolean turnCountGreaterThan()
    {
        if(mTurnCount > NUM_TURNS_PER_GAME)
        {
            return true;
        }

        return false;
    }

    public String getWinningPlayer()
    {
        if(mRedPlayerPoints>mBluePlayerPoints)
        {
            return "Red Player Wins!\n\n" +
                    "Red Player Stats:\n" + "   Points: " + mRedPlayerPoints +
                    "\n   Max Speed: " + roundDown2(mMaxRedVelocity) + " um/s\n\n"+
                    "Blue Player Stats:\n" + "   Points: " + mBluePlayerPoints +
                    "\n   Max Speed: " + roundDown2(mMaxBlueVelocity) + " um/s";
        }
        if(mBluePlayerPoints>mRedPlayerPoints)
        {
            return "Blue Player Wins!\n\n" +
                    "Red Player Stats:\n" + "   Points: " + mRedPlayerPoints +
                    "\n   Max Speed: " + roundDown2(mMaxRedVelocity) + " um/s\n\n"+
                    "Blue Player Stats:\n" + "   Points: " + mBluePlayerPoints +
                    "\n   Max Speed: " + roundDown2(mMaxBlueVelocity) + " um/s";
        }
        else
        {
            return "Tie!\n\n" +
                    "Red Player Stats:\n" + "   Points: " + mRedPlayerPoints +
                    "\n   Max Speed: " + roundDown2(mMaxRedVelocity) + " um/s\n\n"+
                    "Blue Player Stats:\n" + "   Points: " + mBluePlayerPoints +
                    "\n   Max Speed: " + roundDown2(mMaxBlueVelocity) + " um/s";

        }
    }

    public Rect returnMBlueGoal()
    {
        return mBlueGoal;
    }

    public void updateVelocity()
    {
        if (mPreviousBallLocations.size() > PREVIOUS_LOCATIONS_TO_TRACK_VELOCITY)
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


        for (int i = 0; i < numPreviousLocations - 1; i++)
        {
            Point previousPoint = mPreviousBallLocations.get(i);
            Point nextPoint = mPreviousBallLocations.get(i + 1);
            Point directionVector = new Point(nextPoint.x - previousPoint.x,
                    nextPoint.y - previousPoint.y);

            mVelocityVector = new Point(mVelocityVector.x + directionVector.x, mVelocityVector.y + directionVector.y);
        }

        mVelocityVector = new Point(mVelocityVector.x/numPreviousLocations, mVelocityVector.y/numPreviousLocations);
        velocity = VELOCITY_SCALE * (Math.sqrt(Math.pow(mVelocityVector.x, 2) +
                Math.pow(mVelocityVector.y, 2)));

        updateMaxVelocities();
    }

    private void updateMaxVelocities()
    {
        if(mCurrentTurn == Turn.RED)
        {
            if(mMaxRedVelocity<velocity&&velocity<100){
                mMaxRedVelocity = velocity;
            }
        }
        if(mCurrentTurn == Turn.BLUE)
        {
            if(mMaxBlueVelocity<velocity&&velocity<100){
                mMaxBlueVelocity=velocity;
            }
        }
    }

    public double getVelocity()
    {
        return roundDown2(velocity);
    }

    public double getMaxBlueVelocity(){
        return mMaxBlueVelocity;
    }

    public double getMaxRedVelocity(){
        return mMaxRedVelocity;
    }

    public void pauseCountdown()
    {
        mCountdownPaused = true;
    }

    public void resumeCountdown()
    {
        mCountdownPaused = false;
    }

    public boolean returnCountdownPaused(){
        return mCountdownPaused;
    }

    public static double roundDown2(double d)
    {
        return (long) (d * 1e2) / 1e2;
    }
}