package edu.stanford.riedel_kruse.bioticgames;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

import java.util.List;

import edu.stanford.riedel_kruse.bioticgamessdk.BioticGameActivity;
import edu.stanford.riedel_kruse.bioticgamessdk.ImageProcessing;
import edu.stanford.riedel_kruse.bioticgamessdk.MathUtil;

public class GameActivity extends BioticGameActivity implements SoccerGameDelegate {
    public static final String TAG = "edu.stanford.riedel-kruse.bioticgames.GameActivity";
    public static final String EXTRA_TUTORIAL_MODE =
            "edu.stanford.riedel-kruse.bioticgames.GameActivity.TUTORIAL_MODE";
    public static final boolean DEBUG_MODE = true;
    public static final int NUM_DEBUG_VIEWS = 1;
    public static final int GOAL_HEIGHT = 400;
    public static final int GOAL_WIDTH = 10;
    public static final int GOAL_EMPTY_WIDTH = 40;  //Width of empty space of the goal
    public static final int SWAP_TIME = 5000;
    public static final int MILLISECONDS_BEFORE_BALL_AUTO_ASSIGN = 5000;

    private Tutorial mTutorial;
    private boolean mTutorialMode;

    private boolean mDrawBall;
    private boolean mTracking;
    private boolean mDrawDirection;
    private boolean mDrawGoals;
    private boolean mDisplayVelocity;
    private boolean mDrawBlinkingArrow;
    private boolean mCountingDown;

    private SoccerGame mSoccerGame;

    private ImageView[] mDebugImageViews;

    private boolean mSwapping;
    private long mSwapCountdown;

    private Point mGoal1TopLeft;
    private Point mGoal1BottomRight;
    private Point mGoal1LArmTopLeft;
    private Point mGoal1LArmBottomRight;
    private Point mGoal1RArmTopLeft;
    private Point mGoal1RArmBottomRight;

    private Point mGoal2TopLeft;
    private Point mGoal2BottomRight;
    private Point mGoal2LArmTopLeft;
    private Point mGoal2LArmBottomRight;
    private Point mGoal2RArmTopLeft;
    private Point mGoal2RArmBottomRight;

    public float tapX = 0;
    public float tapY = 0;

    /**
     * Activity lifecycle callbacks
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_game);
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mTutorialMode = intent.getBooleanExtra(EXTRA_TUTORIAL_MODE, false);

        mDrawBall = true;
        mTracking = true;
        mDrawDirection = true;
        mDrawGoals = true;
        mDisplayVelocity = true;
        mDrawBlinkingArrow = true;
        mCountingDown = true;

        // If we're in tutorial mode, show the tutorial layout.
        if (mTutorialMode) {
            mTutorial = new Tutorial();
            findViewById(R.id.tutorialLayout).setVisibility(View.VISIBLE);
            updateTutorialViews();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (DEBUG_MODE) {
            mDebugImageViews = new ImageView[NUM_DEBUG_VIEWS];
            createDebugViews(NUM_DEBUG_VIEWS);
        }
    }

    private void createDebugViews(int numViews) {
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.zoomView);

        for (int i = 0; i < numViews; i++) {
            ImageView imageView = new ImageView(this);
            imageView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT, 1));
            layout.addView(imageView);
            mDebugImageViews[i] = imageView;
        }
    }

    private Point findClosestEuglenaToBall(Mat frame) {
        // Get the model data about the ball.
        Point ballLocation = mSoccerGame.getBallLocation();
        int ballRadius = mSoccerGame.getBallRadius();

        // Create a region of interest based on the location of the ball.
        Rect roi = new Rect();
        roi.x = Math.max((int) ballLocation.x - ballRadius, 0);
        roi.y = Math.max((int) ballLocation.y - ballRadius, 0);
        roi.width = Math.min(ballRadius * 2, mSoccerGame.getFieldWidth() - roi.x);
        roi.height = Math.min(ballRadius * 2, mSoccerGame.getFieldHeight() - roi.y);

        // Find all things that look like Euglena in the region of interest.
        List<Point> euglenaLocations = ImageProcessing.findEuglenaInRoi(frame, roi);

        // Find the location of the Euglena that is closest to the ball.
        return MathUtil.findClosestPoint(ballLocation, euglenaLocations);
    }

    @Override
    protected void updateGame(Mat frame, long timeDelta) {
        // Flip the input frame so that it's orientation matches the orientation of the phone
        // TODO: This sort of operation should be handled by the SDK automatically depending on
        // the orientation of the phone.
        Core.flip(frame, frame, -1);

        // TODO: Is there a way to pre-init this without having to do a check on every frame?
        initSoccerGame(frame.cols(), frame.rows());

        // If the game is over, then there's no need to process anything.
        if (mSoccerGame.isGameOver()) {
            return;
        }

        // If we're swapping, the game is essentially paused, so just update the swap countdown and
        // return early.
        if (mSwapping) {
            mSwapCountdown -= timeDelta;
            mSwapping = mSwapCountdown > 0;
            return;
        }
        // If we're passing the ball, then just tell the game how much time has passed.
        else if (mSoccerGame.isPassing()) {
            mSoccerGame.moveBallDuringPass(timeDelta);
        }
        // If we're not passing and we are tracking, then go ahead and find the closest Euglena to
        // the ball.
        else if (mTracking) {
            Point closestEuglenaLocation = findClosestEuglenaToBall(frame);

            // Update the ball's location to be at the closest Euglena's location.
            mSoccerGame.updateBallLocation(closestEuglenaLocation);
        }

        mSoccerGame.checkCollisions();
        mSoccerGame.updateTime(timeDelta);
    }

    @Override
    protected Mat drawGame(Mat frame) {
        if (mCountingDown && !mSoccerGame.isPassing()) {
            blinkingArrow(frame);
        }

        drawBallBlinker(frame);
        drawGoals(frame);
        drawPassingDirection(frame);
        if (mSwapping) {
            updateSwapCountdown();
        } else {
            updateCountdown();
        }

        displayVelocity(frame);
        drawScaleBar(frame);
        return frame;
    }

    private void initSoccerGame(int cols, int rows) {
        if (mSoccerGame == null) {
            mSoccerGame = new SoccerGame(cols, rows, this);
            if (!mCountingDown) {
                mSoccerGame.pauseCountdown();
            }
        }
    }

    @Override
    protected int getCameraViewResourceId() {
        return R.id.camera_view;
    }

    public void onChangedTurn(final SoccerGame.Turn currentTurn) {
        // TODO: Freeze the game for some time so players can switch without stress.
        mSwapping = true;
        mSwapCountdown = SWAP_TIME;
        updateSwapCountdown();
    }

    public void onGoalScored(final SoccerGame.Turn currentTurn) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String message = "";
                if (currentTurn == SoccerGame.Turn.RED) {
                    message += "Red";
                } else {
                    message += "Blue";
                }

                message += " Player Scored ";
                message += mSoccerGame.getPointsScored() + " Points!";

                Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
        });

        updateScoreViews();
    }

    public void onPickupButtonPressed(final SoccerGame.Turn currentTurn) {
        updateScoreViews();
    }

    public void onOutOfBounds() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                Toast toast = Toast.makeText(getApplicationContext(), "Out of Bounds!", Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
        });
    }

    private void drawBall(Mat img) {
        if (!mDrawBall) {
            return;
        }

        Scalar color;
        SoccerGame.Turn currentTurn = mSoccerGame.getCurrentTurn();
        if (currentTurn == SoccerGame.Turn.RED) {
            // Red
            color = new Scalar(255, 68, 68);
        } else {
            // Blue
            color = new Scalar(51, 181, 229);
        }
        Core.circle(img, mSoccerGame.getBallLocation(), mSoccerGame.getBallRadius(), color, 10);
    }

    private void drawGoals(Mat img) {
        if (!mDrawGoals) {
            return;
        }

        int height = mSoccerGame.getFieldHeight();
        float margin = (height - GOAL_HEIGHT) / 2;


        //draw GOAL 1
        if (mGoal1TopLeft == null) {
            mGoal1TopLeft = new Point(0, margin);
        }

        if (mGoal1BottomRight == null) {
            mGoal1BottomRight = new Point(GOAL_WIDTH, GOAL_HEIGHT + margin);
        }

        if (mGoal1LArmTopLeft == null) {
            mGoal1LArmTopLeft = new Point(GOAL_WIDTH, margin);
        }

        if (mGoal1LArmBottomRight == null) {
            mGoal1LArmBottomRight = new Point(GOAL_WIDTH + GOAL_EMPTY_WIDTH, GOAL_WIDTH + margin);
        }

        if (mGoal1RArmTopLeft == null) {
            mGoal1RArmTopLeft = new Point(GOAL_WIDTH, GOAL_HEIGHT - GOAL_WIDTH + margin);
        }

        if (mGoal1RArmBottomRight == null) {
            mGoal1RArmBottomRight = new Point(GOAL_WIDTH + GOAL_EMPTY_WIDTH, GOAL_HEIGHT + margin);
        }


        //draw GOAL 2
        if (mGoal2TopLeft == null) {
            mGoal2TopLeft = new Point(img.cols() - GOAL_WIDTH, margin);
        }

        if (mGoal2BottomRight == null) {
            mGoal2BottomRight = new Point(img.cols(), GOAL_HEIGHT + margin);
        }

        if (mGoal2LArmTopLeft == null) {
            mGoal2LArmTopLeft = new Point(img.cols() - GOAL_WIDTH - GOAL_EMPTY_WIDTH, margin);
        }

        if (mGoal2LArmBottomRight == null) {
            mGoal2LArmBottomRight = new Point(img.cols() - GOAL_WIDTH, GOAL_WIDTH + margin);
        }

        if (mGoal2RArmTopLeft == null) {
            mGoal2RArmTopLeft = new Point(img.cols() - GOAL_WIDTH - GOAL_EMPTY_WIDTH,
                    GOAL_HEIGHT - GOAL_WIDTH + margin);
        }

        if (mGoal2RArmBottomRight == null) {
            mGoal2RArmBottomRight = new Point(img.cols() - GOAL_WIDTH, GOAL_HEIGHT + margin);
        }

        Core.rectangle(img, mGoal1TopLeft, mGoal1BottomRight, new Scalar(51, 181, 229), -1);
        Core.rectangle(img, mGoal1LArmTopLeft, mGoal1LArmBottomRight, new Scalar(51, 181, 229), -1);
        Core.rectangle(img, mGoal1RArmTopLeft, mGoal1RArmBottomRight, new Scalar(51, 181, 229), -1);

        Core.rectangle(img, mGoal2TopLeft, mGoal2BottomRight, new Scalar(255, 68, 68), -1);
        Core.rectangle(img, mGoal2LArmTopLeft, mGoal2LArmBottomRight, new Scalar(255, 68, 68), -1);
        Core.rectangle(img, mGoal2RArmTopLeft, mGoal2RArmBottomRight, new Scalar(255, 68, 68), -1);
    }

    private void drawPassingDirection(Mat img) {
        if (!mDrawDirection) {
            return;
        }

        Point ballLocation = mSoccerGame.getBallLocation();
        Point passingDirection = mSoccerGame.getPassingDirection();
        int ballRadius = mSoccerGame.getBallRadius();

        passingDirection.x *= ballRadius;
        passingDirection.y *= ballRadius;

        Point endPoint = new Point(ballLocation.x, ballLocation.y);
        endPoint.x += passingDirection.x;
        endPoint.y += passingDirection.y;

        Core.line(img, ballLocation, endPoint, new Scalar(0, 255, 0));
    }

    private void updateCountdown() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                long timeLeft = mSoccerGame.getTimeLeftInTurn() / 1000;
                TextView countView = (TextView) findViewById(R.id.countDown);
                countView.setText("Countdown: " + timeLeft);
            }
        });

    }

    private void updateSwapCountdown() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                long timeLeft = mSwapCountdown / 1000;
                TextView countView = (TextView) findViewById(R.id.countDown);
                countView.setText("Swap: " + timeLeft);
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mSoccerGame.passBall();
        return super.onTouchEvent(event);
    }

    public void updateScoreViews() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView textView = (TextView) findViewById(R.id.bPoints);
                textView.setText("  " + mSoccerGame.getBluePlayerPoints());
                TextView textView2 = (TextView) findViewById(R.id.rPoints);
                textView2.setText(mSoccerGame.getRedPlayerPoints() + "  ");
            }
        });
    }

    public void onNewGamePressed(View v) {
        mSoccerGame.reset();
        updateScoreViews();
    }

    public void onInstructionsPressed(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Instructions");
        builder.setMessage("Try to score the ball into the other player's goal to win! " +
                        "You get 3 points for carrying the ball into the goal, and 1 point for passing it in.\n\n" +
                        "Control the Euglena with the joystick, and pass the ball by tapping the Pass button. \n\n" +
                        "When time runs out, there is a short pause to hand off the controller to the other player."
        );
        builder.setCancelable(false);
        builder.setPositiveButton("Got it!", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        builder.show();
    }

    public void infoButtonPressed(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("About Euglena:");
        builder.setMessage("Euglena are a single-celled, photosynthetic organism! " +
                        "Euglena can be controlled by light simuli. Can you tell if the Euglena seek or avoid the light?"
        );
        builder.setCancelable(false);
        builder.setPositiveButton("I feel smarter already!", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        builder.show();
    }

    public void creditsButtonPressed(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Credits");
        builder.setMessage("Honesty Kim: etc\nDaniel Chiu: Programming\n" +
                        "Seung Ah Lee: Optics\nAlice Chung: Euglena Biology\nSherwin Xia: Electronics\n" +
                        "Lukas Gerber: Sticker Microfluidics\nNate Cira: Microfluidics\n" +
                        "Ingmar Riedel-Kruse: Advisor"
        );
        builder.setCancelable(false);
        builder.setPositiveButton("Good job guys!", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        builder.show();
    }

    public void blinkingArrow(Mat img) {
        if (!mDrawBlinkingArrow) {
            return;
        }

        // TODO: It's not clear what this if statement is trying to accomplish. Needs a refactor or
        // a comment.
        if ((mSoccerGame.getTimeLeftInTurn() / 1000) % 2 == 0) {
            Scalar color;
            SoccerGame.Turn currentTurn = mSoccerGame.getCurrentTurn();
            if (currentTurn == SoccerGame.Turn.RED) {
                // Red
                color = new Scalar(255, 68, 68);
                Point point1 = new Point(img.cols() / 2 - 100, img.rows() / 2);
                Point point2 = new Point(img.cols() / 2 + 100, img.rows() / 2);
                Core.line(img, point1, point2, color, 3);
                Point point3 = new Point(img.cols() / 2 + 50, img.rows() / 2 + 50);
                Point point4 = new Point(img.cols() / 2 + 50, img.rows() / 2 - 50);
                Core.line(img, point3, point2, color, 3);
                Core.line(img, point4, point2, color, 3);
            } else {
                // Blue
                color = new Scalar(51, 181, 229);
                Point point1 = new Point(img.cols() / 2 - 100, img.rows() / 2);
                Point point2 = new Point(img.cols() / 2 + 100, img.rows() / 2);
                Core.line(img, point1, point2, color, 3);
                Point point3 = new Point(img.cols() / 2 - 50, img.rows() / 2 + 50);
                Point point4 = new Point(img.cols() / 2 - 50, img.rows() / 2 - 50);
                Core.line(img, point3, point1, color, 3);
                Core.line(img, point4, point1, color, 3);
            }
        }
    }

    public void troubleShootButtonPressed(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Troubleshooting");
        builder.setMessage("If there are no Euglena in the field of view, try moving the stage around, flushing " +
                        "the syringe, or adjusting the focusing knob.\n\n" + "If the Euglena aren't moving the way you " +
                        "expect them to (i.e. sluggish), keep in mind these are real living organisms! They may " +
                        "be off their circadian rhythms or may need be tired."
        );
        builder.setCancelable(false);
        builder.setPositiveButton("Everything makes sense now!", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        builder.show();
    }

    public void drawBallBlinker(Mat img) {
        if ((mSoccerGame.getTimeLeftInTurn() / 1000) > 5) {
            drawBall(img);
        } else {
            if ((mSoccerGame.getTimeLeftInTurn() / 200) % 2 == 1) {
                drawBall(img);
            }
        }
    }

    public void onGameOver() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String msg = mSoccerGame.getWinningPlayer();

                AlertDialog.Builder builder = new AlertDialog.Builder(GameActivity.this);
                builder.setTitle("Game Over!");
                builder.setMessage(msg
                );
                builder.setCancelable(false);
                builder.setPositiveButton("Keep playing!", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mSoccerGame.resumeCountdown();
                    }
                });
                builder.show();

                mSoccerGame.reset();
                updateScoreViews();
            }
        });
    }

    public void displayVelocity(Mat img) {
        if (!mDisplayVelocity) {
            return;
        }

        String velocityString = Double.toString(mSoccerGame.getSpeed());
        Point mStringLocation = new Point(mSoccerGame.getFieldWidth() / 10, mSoccerGame.getFieldHeight() / 1.05);

        Core.putText(img, velocityString + "um/sec", mStringLocation,
                1, 4, new Scalar(200, 200, 250), 5);
    }

    public void drawScaleBar(Mat img) {
        Core.line(img, new Point(mSoccerGame.getFieldWidth() / 1.3, mSoccerGame.getFieldHeight() / 1.1),
                new Point(mSoccerGame.getFieldWidth() / 1.3 + 150, mSoccerGame.getFieldHeight() / 1.1),
                new Scalar(200, 200, 250), 3);
        Core.putText(img, "100 um", new Point(mSoccerGame.getFieldWidth() / 1.33, mSoccerGame.getFieldHeight() / 1.04),
                1, 3, new Scalar(200, 200, 250), 4);
    }

    public void updateTutorialViews() {
        if (!mTutorialMode) {
            return;
        }

        TextView tutorialTextView = (TextView) findViewById(R.id.tutorialText);
        tutorialTextView.setText(mTutorial.getCurrentStringResource());
        tutorialTextView.setTextSize(20);
        mDrawBall = mTutorial.shouldDrawBall();
        mDrawDirection = mTutorial.shouldDrawDirection();
        mTracking = mTutorial.shouldTrack();
        mDrawGoals = mTutorial.shouldDrawGoals();
        mDrawBlinkingArrow = mTutorial.shouldDrawBlinkingArrow();
        mCountingDown = mTutorial.shouldCountDown();
        if (mCountingDown) {
            if (mSoccerGame != null) {
                mSoccerGame.resumeCountdown();
            }
        } else {
            if (mSoccerGame != null) {
                mSoccerGame.pauseCountdown();
            }
        }
        final boolean displayScores = mTutorial.shouldDisplayScores();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView redScore = (TextView) findViewById(R.id.rPoints);
                TextView blueScore = (TextView) findViewById(R.id.bPoints);

                if (displayScores) {
                    redScore.setVisibility(View.VISIBLE);
                    blueScore.setVisibility(View.VISIBLE);
                } else {
                    redScore.setVisibility(View.INVISIBLE);
                    blueScore.setVisibility(View.INVISIBLE);
                }
            }
        });
        final boolean displayCountdown = mTutorial.shouldDisplayCountdown();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView countdown = (TextView) findViewById(R.id.countDown);

                if (displayCountdown) {
                    countdown.setVisibility(View.VISIBLE);
                } else {
                    countdown.setVisibility(View.INVISIBLE);
                }
            }
        });
        mDisplayVelocity = mTutorial.shouldDisplayVelocity();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Button button = (Button) findViewById(R.id.tutorialButton);

                button.setText(mTutorial.getButtonTextResource());
            }
        });
    }

    public void tutorialButtonPressed(View view) {
        if (!mTutorialMode) {
            return;
        }

        mTutorial.advance();
        updateTutorialViews();

        if (mTutorial.finished()) {
            mTutorialMode = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    LinearLayout tutorialLayout = (LinearLayout) findViewById(R.id.tutorialLayout);
                    tutorialLayout.setVisibility(View.GONE);
                }
            });

            backToMainMenu(view);
        }
    }

    public void backToMainMenu(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }
}

/* to turn off autofocus:
http://answers.opencv.org/question/21377/how-turn-off-autofocus-with-camerabridgeviewbase/
 */