package edu.stanford.riedel_kruse.bioticgames;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
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
    public static final long MILLISECONDS_PER_TURN = 30 * 1000;
    public static final int DEFAULT_BALL_RADIUS = 60;
    public static final int DEFAULT_GOAL_WIDTH = 40;
    public static final int DEFAULT_GOAL_HEIGHT = 400;
    public static final int GOAL_OFFSET = 50;
    /**
     * How close the ball has to be to the edge of the screen to be considered out of bounds.
     * Required because the ball cannot leave the screen without crashing.
     */
    public static final int BOUNDS_BUFFER = 20;
    public static final int BOUNCE_BUFFER = 85;
    public static final int PREVIOUS_LOCATIONS_TO_TRACK = 15;
    public static final int PREVIOUS_LOCATIONS_TO_TRACK_VELOCITY = 20;
    public static final double FRAMES_PER_PASS = 20;
    public static final double PASS_DISTANCE = 500;
    public static final double FRAMES_PER_BOUNCE = 10;
    public static final double BOUNCE_DISTANCE = 300;
    public static final String TAG = "edu.stanford.riedel-kruse.bioticgames.SoccerGame";
    public static final int NO_PASS_POINTS = 2;
    public static final int NUM_TURNS = 6;
    public static final double VELOCITY_SCALE = 15;
    public boolean mSoundIsBounced = false;
    public boolean mSoundOutOfBounds = false;
    public boolean mIsBouncing = false;
    public Point mBouncingDirection;
    public boolean mBouncedOnceX = false;
    public boolean mBouncedOnceY = false;

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
    private int mScore;
    private int mWinningScore;
    private Turn mCurrentTurn;
    private boolean mPassing;
    private int mPassingFrames;
    private int mBouncingFrames;
    private long mTimeLeftInTurn;
    private int turnCount = 0;
    private double velocity = 0;
    private double mMaxBlueVelocity = 0;
    private double mMaxRedVelocity = 0;
    private Point mVelocityVector = new Point (0,0);
    private boolean mCountdownPaused;

    private SoccerGameDelegate mDelegate;

    private Rect mRedGoal;
    private Rect mBlueGoal;

    private List<Point> mVelocityBallLocations;
    private static final double velRejectThreshold = 15;

    public static MediaPlayer mSoundBounce;
    public static SoundPool mSoundEffects;

    public SoccerGame(int fieldWidth, int fieldHeight, int winningScore,
                      SoccerGameDelegate delegate)
    {
        mFieldWidth = fieldWidth;
        mFieldHeight = fieldHeight;

        mWinningScore = winningScore;

        mDelegate = delegate;

        mPassingDirection = new Point(0, 0);
        mPreviousBallLocations = new ArrayList<Point>();
        mVelocityBallLocations = new ArrayList<Point>();

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
        mScore = 0;
        turnCount = 0;
        if(mCurrentTurn != Turn.RED)
        {
            changeTurn();
        }
        else
        {
            changeTurn();
            changeTurn();
        }
        mTimeLeftInTurn = MILLISECONDS_PER_TURN;

        // Reset the goal locations, heights, and widths.
        mRedGoal.x = GOAL_OFFSET;
        mBlueGoal.x = mFieldWidth - DEFAULT_GOAL_WIDTH - GOAL_OFFSET;
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
        mVelocityVector = new Point(0,0);
        velocity = 0;
        mPassing = true;
        mPassingFrames = 0;
    }

    public void passingFrame(long timeDelta)
    {

        //if you want the timer to stop when passing, delete
        /*if (!mCountdownPaused)
        {
            mTimeLeftInTurn -= timeDelta;
        }*/

        // If the time in the turn ran out, give control to the other player.
        if (mTimeLeftInTurn <= 0)
        {
            changeTurn();
        }

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

        updateBallLocation(newLocation, timeDelta);
    }

    public void bouncingFrame(long timeDelta)
    {

        //if you want the timer to stop when passing, delete
        /*if (!mCountdownPaused)
        {
            mTimeLeftInTurn -= timeDelta;
        }*/

        // If the time in the turn ran out, give control to the other player.
        if (mTimeLeftInTurn <= 0)
        {
            changeTurn();
        }

        if (mBouncingFrames >= FRAMES_PER_BOUNCE)
        {
            mIsBouncing = false;
            mBouncingFrames = 0;
            // Just finished passing, so we should clear all data about the movement direction.
            // Otherwise the player will be able to spam the pass button and continuously pass in
            // the same direction.
            resetPassingDirection();
            return;
        }

        Point newLocation = new Point(mBallLocation.x + ((float) BOUNCE_DISTANCE / FRAMES_PER_BOUNCE) *
                mBouncingDirection.x, mBallLocation.y + ((float) BOUNCE_DISTANCE / FRAMES_PER_BOUNCE) *
                mBouncingDirection.y);

        mBouncingFrames++;

        updateBallLocation(newLocation, timeDelta);
    }

    public Point getBallLocation()
    {
        return mBallLocation.clone();
    }

    public int getBallRadius()
    {
        return mBallRadius;
    }

    public Turn getCurrentTurn()
    {
        return mCurrentTurn;
    }

    public int getScore() { return mScore; }

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

    /**
     * Resets the ball by settings its location to the center of the field, clearing the movement
     * direction, and clearing all previously stored ball locations.
     */
    private void resetBall()
    {
        mBallLocation = new Point(mFieldWidth / 2.0, mFieldHeight / 2.0);
        resetPassingDirection();
        mPassing = false;
        mIsBouncing = false;
    }

    public void resetPassingDirection()
    {
        mPreviousBallLocations.clear();
        mPassingDirection.x = 0;
        mPassingDirection.y = 0;
        mVelocityBallLocations.clear();
        mVelocityVector = new Point(0,0);
        velocity = 0;
        mDelegate.onZeroVelocity();
    }

    public void resetBouncingDirection()
    {
        mBouncingDirection = new Point(0,0);
        mIsBouncing = false;
    }

    /**
     * Updates the internal state of the soccer game with a new location for the ball.
     * @param newLocation The new location of the ball.
     */
    public void updateBallLocation(Point newLocation, long timeDelta)
    {
        mSoundIsBounced = false;

        if (newLocation == null)
        {
            //if you want the timer to stop when the ball is stagnant, delete

            if (!mCountdownPaused)
            {
                mTimeLeftInTurn -= timeDelta;
            }

            // If the time in the turn ran out, give control to the other player.
            if (mTimeLeftInTurn <= 0)
            {
                changeTurn();
            }

            resetPassingDirection();

            return;
        }

        mBallLocation = newLocation;


        // Otherwise if a goal is scored.
        if (checkForGoal())
        {
            resetBall();
            return;
        }

        checkForGameOver();

        boolean outOfBounds = checkForOutOfBounds();
        boolean bounceBounds = checkForBounceBounds();
        // If we are in the middle of passing and the ball is out of bounds, then we should bounce
        // off the walls.
        if ((mPassing || mIsBouncing) && bounceBounds)
        {
            bounceOffWalls();
        }
        else if (!(mPassing || mIsBouncing) && outOfBounds)
        {
            mSoundOutOfBounds = true;
            mDelegate.onOutOfBounds();
            resetBall();
            changeTurn();
            return;
        }
        else if(!bounceBounds){
            mBouncedOnceX = false;
            mBouncedOnceY = false;
        }

        // If the ball is not being passed, then update the movement direction. We don't want to do
        // this while the ball is being passed because the ball's direction does not change while it
        // is being passed.
        if (!mPassing)
        {
            updatePassingDirection();
            updateVelocity();
        }

        if (!mCountdownPaused)
        {
            mTimeLeftInTurn -= timeDelta;
        }

        // If the time in the turn ran out, give control to the other player.
        if (mTimeLeftInTurn <= 0)
        {
            changeTurn();
        }
    }

    private void bounceOffWalls()
    {
        mSoundIsBounced = true;

        if (mBallLocation.x < BOUNCE_BUFFER || mBallLocation.x > mFieldWidth - BOUNCE_BUFFER)
        {
            if(!mBouncedOnceX) {
                if (mPassing) {
                    mPassingDirection.x *= -1;
                }
                if (mIsBouncing) {
                    mBouncingDirection.x *= -1;
                }

                mBouncedOnceX = true;
            }
        }
        else if (mBallLocation.y < BOUNCE_BUFFER || mBallLocation.y > mFieldHeight - BOUNCE_BUFFER)
        {
            if(!mBouncedOnceY) {
                if (mPassing) {
                    mPassingDirection.y *= -1;
                }
                if (mIsBouncing) {
                    mBouncingDirection.y *= -1;
                }
                mBouncedOnceY = true;
            }
        }
    }

    /**
     * Changes the turn to the next player.
     */
    private void changeTurn()
    {
        turnCount++;

        mPassing = false;
        resetPassingDirection();
        resetBouncingDirection();

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
        if(!mIsBouncing) {
            if (mCurrentTurn == Turn.RED) {
                if (mBallLocation.inside(mBlueGoal)) {
                    if (!mPassing && !mIsBouncing) {
                        mScore += NO_PASS_POINTS;
                    } else {
                        mScore++;
                    }
                    // If there is a delegate, let the delegate know that a goal was scored so it can do
                    // whatever else it wants (e.g. display a notification).
                    if (mDelegate != null) {
                        mDelegate.onGoalScored(Turn.RED);
                    }
                    return true;
                }
            }
            // Otherwise, if it's the blue player's turn and the ball is inside the red goal, then a
            // goal has been scored and the blue player gets a point.
            else {
                if (mBallLocation.inside(mRedGoal)) {
                    if (!mPassing && !mIsBouncing) {
                        mScore += NO_PASS_POINTS;
                    } else {
                        mScore++;
                    }
                    // If there is a delegate, let the delegate know that a goal was scored so it can do
                    // whatever else it wants (e.g. display a notification).
                    if (mDelegate != null) {
                        mDelegate.onGoalScored(Turn.BLUE);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private void checkForGameOver() {
        if (mScore >= mWinningScore) {
            mDelegate.onGameOver();
        }
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

        mSoundOutOfBounds = false;
        return false;
    }

    private boolean checkForBounceBounds()
    {

        if (mBallLocation.x <= BOUNCE_BUFFER || mBallLocation.y <= BOUNCE_BUFFER ||
                mBallLocation.x >= mFieldWidth - BOUNCE_BUFFER ||
                mBallLocation.y >= mFieldHeight - BOUNCE_BUFFER)
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
        if(!checkDiscontinuousVelocity()){
            mVelocityBallLocations.add(mBallLocation);
        }
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
            if(magnitude == 0){
                directionVector.x = 0;
                directionVector.y = 0;
            }else {
                directionVector.x = directionVector.x / magnitude;
                directionVector.y = directionVector.y / magnitude;
            }

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

    public boolean turnCountGreaterThan()
    {
        if (turnCount > NUM_TURNS)
        {
            return true;
        }

        return false;
    }

    public void updateVelocity()
    {
        if (mVelocityBallLocations.size() > PREVIOUS_LOCATIONS_TO_TRACK_VELOCITY)
        {
            mVelocityBallLocations.remove(0);
        }

        int numPreviousLocations = mVelocityBallLocations.size();

        //if for some reason mVelocityBallLocations is empty, initialize it to the current position
        if(numPreviousLocations == 0){
            mVelocityBallLocations.add(mBallLocation);
            return;
        }
        // If we only have one previous location, then we cannot compute any directions since we
        // need two points to define a line. We automatically reject the first one as well...
        if (numPreviousLocations <= 3)
        {
            return;
        }


        for (int i = 2; i < numPreviousLocations - 1; i++)
        {
            Point previousPoint = mVelocityBallLocations.get(i);
            Point nextPoint = mVelocityBallLocations.get(i + 1);
            Point directionVector = new Point(nextPoint.x - previousPoint.x,
                    nextPoint.y - previousPoint.y);

            mVelocityVector = new Point(mVelocityVector.x + directionVector.x, mVelocityVector.y + directionVector.y);
        }

        mVelocityVector = new Point(mVelocityVector.x/numPreviousLocations, mVelocityVector.y/numPreviousLocations);
        velocity = VELOCITY_SCALE * (Math.sqrt(Math.pow(mVelocityVector.x, 2) +
                Math.pow(mVelocityVector.y, 2)));

        if (velocity > 0) {
            mDelegate.onNonzeroVelocity();
        }
        else {
            mDelegate.onZeroVelocity();
        }

        updateMaxVelocities();
    }

    private void updateMaxVelocities()
    {
        if(mCurrentTurn == Turn.RED)
        {
            if(mMaxRedVelocity<velocity){
                mMaxRedVelocity = velocity;
            }
        }
        if(mCurrentTurn == Turn.BLUE)
        {
            if(mMaxBlueVelocity<velocity){
                mMaxBlueVelocity=velocity;
            }
        }
    }

    public double getVelocity()
    {
        return roundDown2(velocity);
        //return velocity;
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

    public boolean checkDiscontinuousVelocity(){
        if(mPreviousBallLocations.size() != 0){
            if(calcBallStepDistance(mPreviousBallLocations.get(mPreviousBallLocations.size() - 1),
                    mBallLocation) > velRejectThreshold) {
                mVelocityBallLocations.clear();
                return true;
            }
        }
        return false;
    }

    public void pauseGame(){}

    public double calcBallStepDistance(Point prevLoc,Point currentLoc){
        double distance = Math.sqrt(
                Math.pow((currentLoc.x - prevLoc.x),2) + Math.pow((currentLoc.y - prevLoc.y),2));
        return distance;
    }

    public void setSoundOutOfBoundsFalse(){
        mSoundOutOfBounds = false;
    }

    public boolean isTherePassingDirection(){
        if(mPassingDirection.x != 0 || mPassingDirection.y !=0){
            return true;
        }

        return false;
    }

    public boolean isBouncing(){
        return mIsBouncing;
    }

    public void bounceBall(){
        if (mIsBouncing)
        {
            return;
        }
        mVelocityVector = new Point(0,0);
        velocity = 0;
        mIsBouncing = true;
        mBouncingFrames = 0;
        mBouncingDirection = new Point(Math.random()-0.5, Math.random()-0.5);
        double bounceMagnitude = Math.sqrt(Math.pow(mBouncingDirection.x,2) + Math.pow(mBouncingDirection.y,2));
        mBouncingDirection = new Point(mBouncingDirection.x/bounceMagnitude, mBouncingDirection.y/bounceMagnitude);
    }
}