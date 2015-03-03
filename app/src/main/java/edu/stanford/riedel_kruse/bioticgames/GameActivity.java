package edu.stanford.riedel_kruse.bioticgames;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import edu.stanford.riedel_kruse.bioticgamessdk.BioticGameActivity;
import edu.stanford.riedel_kruse.bioticgamessdk.ImageProcessing;
import edu.stanford.riedel_kruse.bioticgamessdk.MathUtil;

public class GameActivity extends BioticGameActivity implements SoccerGameDelegate {
    public static final String TAG = "edu.stanford.riedel-kruse.bioticgames.GameActivity";
    public static final String EXTRA_TUTORIAL_MODE =
            "edu.stanford.riedel-kruse.bioticgames.GameActivity.TUTORIAL_MODE";
    public static final int GOAL_HEIGHT = 400;
    public static final int GOAL_WIDTH = 10;
    public static final int GOAL_EMPTY_WIDTH = 40;  //Width of empty space of the goal
    public static final int SWAP_TIME = 5000;
    public static final int MILLISECONDS_BEFORE_BALL_AUTO_ASSIGN = 5000;

    private Button mActionButton;

    private long mTime;
    private ScheduledExecutorService mScheduledTaskExecutor;

    private Tutorial mTutorial;
    private boolean mTutorialMode;

    private boolean mDrawBall;
    private boolean mTracking;
    private boolean mDrawDirection;
    private boolean mDrawGoals;
    private boolean mDrawCentroids;
    private boolean mDisplayVelocity;
    private boolean mDrawBlinkingArrow;
    private boolean mCountingDown;
    private boolean mDisplayActionButton;

    private SoccerGame mSoccerGame;

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

    private final int mGoalBoxWidth = 200;
    private final int mGoalBoxHeight = 250;

    private Point mFieldTopLeft;
    private Point mFieldBottomRight;
    private Point mFieldTopCenter;
    private Point mFieldBottomCenter;
    private Point mLeftGoalBoxTopLeft;
    private Point mLeftGoalBoxBottomRight;
    private Point mRightGoalBoxTopLeft;
    private Point mRightGoalBoxBottomRight;

    private final int mGoalOffset = 18;

    private final int mFieldOffset = 15;

    private Mat mCurrentMask;

    //below are the variables used in drawing a soccer ball
    private int mSoccerBallRadius = 30;
    private List<MatOfPoint> mPentagons;

    //below is sound stuff
    private SoundPool mSoundEffects;
    private int soundIds[];
    private AudioManager mAudioManager;
    private boolean plays = false, loaded = false;
    private float actVolume, maxVolume, volume;

    private boolean mPassSoundPlayed = false;
    private boolean mSoundGoalScored = false;
    private boolean mBounceSoundPlayed = false;

    private boolean mGoalScored = false;
    private double mGoalMessageStartTime = 0;

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
        mDrawCentroids = true;
        mDisplayVelocity = true;
        mDrawBlinkingArrow = true;
        mCountingDown = true;

        mSoundEffects = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        soundIds = new int[10];
        soundIds[0] = mSoundEffects.load(this, R.raw.wallbounce, 1);
        soundIds[1] = mSoundEffects.load(this, R.raw.laser, 1);
        soundIds[2] = mSoundEffects.load(this, R.raw.explosion, 1);
        soundIds[3] = mSoundEffects.load(this, R.raw.crowdcheer, 1);
        soundIds[4] = mSoundEffects.load(this, R.raw.powerup, 1);
        // AudioManager audio settings for adjusting the volume
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        actVolume = (float) mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        maxVolume = (float) mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume = actVolume / maxVolume;
        //Hardware buttons setting to adjust the media sound
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mActionButton = (Button) findViewById(R.id.action_button);

        // If we're in tutorial mode, show the tutorial layout.
        if (mTutorialMode) {
            mTutorial = new Tutorial();
            findViewById(R.id.tutorialLayout).setVisibility(View.VISIBLE);
            updateTutorialViews();
        }

        mTime = 0;

        mScheduledTaskExecutor = Executors.newScheduledThreadPool(1);

        mScheduledTaskExecutor.scheduleAtFixedRate(new Runnable() {
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTime++;
                        TextView textView = (TextView) findViewById(R.id.time);
                        textView.setText("Time: " + mTime);
                    }
                });
            }
        }, 1, 1, TimeUnit.SECONDS);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected int getCameraViewResourceId() {
        return R.id.camera_view;
    }

    @Override
    public void onChangedDirection(SoccerGame.Direction currentDirection) {

    }

    public void onGoalScored(final SoccerGame.Direction currentDirection) {
        if (!mDrawGoals) {
            return;
        }
        mSoundGoalScored = true;

        mGoalScored = true;

        updateScoreView();
    }

    public void onGameOver() {

        stopTimer();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(GameActivity.this);
                builder.setTitle("Game Over!");
                builder.setCancelable(false);
                builder.setPositiveButton("Keep playing!", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mSoccerGame.resumeCountdown();
                    }
                });
                builder.show();

                mSoccerGame.reset();
            }
        });
    }

    public void onNonzeroVelocity() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActionButton.setText("Pass");
            }
        });
    }

    public void onZeroVelocity() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActionButton.setText("Bounce");
            }
        });
    }

    public void stopTimer() {
        mScheduledTaskExecutor.shutdownNow();
    }

    public void displayGoalMessage(Mat img, double time) {
        if (mGoalMessageStartTime == 0) {
            Core.putText(img, "G    ", new Point(img.cols() / 2 - 100, img.rows() / 2),
                    1, 8, new Scalar(217, 221, 5), 5);
            mGoalMessageStartTime = time;
        } else if (time < mGoalMessageStartTime + 500) {
            Core.putText(img, "G      ", new Point(img.cols() / 2 - 220, img.rows() / 2 + 50),
                    1, 8, new Scalar(217, 221, 5), 10);
        } else if (mGoalMessageStartTime + 1000 > time && time > mGoalMessageStartTime + 500) {
            Core.putText(img, "GO     ", new Point(img.cols() / 2 - 220, img.rows() / 2 + 50),
                    1, 8, new Scalar(217, 221, 5), 10);
        } else if (mGoalMessageStartTime + 1500 > time && time > mGoalMessageStartTime + 1000) {
            Core.putText(img, "GOA    ", new Point(img.cols() / 2 - 220, img.rows() / 2 + 50),
                    1, 8, new Scalar(217, 221, 5), 10);
        } else if (mGoalMessageStartTime + 2000 > time && time > mGoalMessageStartTime + 1500) {
            Core.putText(img, "GOAL   ", new Point(img.cols() / 2 - 220, img.rows() / 2 + 50),
                    1, 8, new Scalar(217, 221, 5), 10);
        } else if (mGoalMessageStartTime + 3500 > time && time > mGoalMessageStartTime + 2000) {
            Core.putText(img, "GOAL!!!", new Point(img.cols() / 2 - 220, img.rows() / 2 + 50),
                    1, 8, new Scalar(217, 221, 5), 10);
        } else if (time > mGoalMessageStartTime + 3500) {
            mGoalMessageStartTime = 0;
            mGoalScored = false;
        }
    }

    public void onPickupButtonPressed(final SoccerGame.Direction currentDirection) {
        updateScoreView();
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
        if (!mDrawBall || mSoccerGame.isPassing() || mSoccerGame.isBouncing()) {
            return;
        }

        Scalar color;
        SoccerGame.Direction currentDirection = mSoccerGame.getCurrentTurn();
        if (currentDirection == SoccerGame.Direction.RIGHT) {
            // Red
            color = new Scalar(255, 68, 68);
        } else {
            // Blue
            color = new Scalar(51, 181, 229);
        }
        Core.circle(img, mSoccerGame.getBallLocation(), mSoccerGame.getBallRadius(), color, 3);
    }

    private void drawField(Mat img) {
        int width = img.cols();
        int height = img.rows();

        mFieldTopLeft = new Point(mFieldOffset, height - mFieldOffset);
        mFieldBottomRight = new Point(width - mFieldOffset, mFieldOffset);

        mFieldTopCenter = new Point(width / 2, height - mFieldOffset);
        mFieldBottomCenter = new Point(width / 2, mFieldOffset);

        mLeftGoalBoxTopLeft = new Point(mFieldOffset, height / 2 + mGoalBoxHeight);
        mLeftGoalBoxBottomRight = new Point(mFieldOffset + mGoalBoxWidth, height / 2 - mGoalBoxHeight);

        mRightGoalBoxTopLeft = new Point(width - mGoalBoxWidth, height / 2 + mGoalBoxHeight);
        mRightGoalBoxBottomRight = new Point(width - mFieldOffset, height / 2 - mGoalBoxHeight);

        Scalar fieldColor;
        fieldColor = new Scalar(235, 235, 235);

        Core.rectangle(img, mFieldTopLeft, mFieldBottomRight, fieldColor, 3);
        Core.line(img, mFieldTopCenter, mFieldBottomCenter, fieldColor, 3);
        Core.circle(img, new Point(width / 2, height / 2), 105, fieldColor, 3);
        Core.rectangle(img, mLeftGoalBoxTopLeft, mLeftGoalBoxBottomRight, fieldColor, 3);
        Core.rectangle(img, mRightGoalBoxBottomRight, mRightGoalBoxTopLeft, fieldColor, 3);
    }

    private void drawGoals(Mat img) {
        if (!mDrawGoals) {
            return;
        }

        drawField(img);

        int height = mSoccerGame.getFieldHeight();
        float margin = (height - GOAL_HEIGHT) / 2;


        //draw GOAL 1
        if (mGoal1TopLeft == null) {
            mGoal1TopLeft = new Point(0 + mGoalOffset, margin);
        }

        if (mGoal1BottomRight == null) {
            mGoal1BottomRight = new Point(GOAL_WIDTH + mGoalOffset, GOAL_HEIGHT + margin);
        }

        if (mGoal1LArmTopLeft == null) {
            mGoal1LArmTopLeft = new Point(GOAL_WIDTH + mGoalOffset, margin);
        }

        if (mGoal1LArmBottomRight == null) {
            mGoal1LArmBottomRight = new Point(GOAL_WIDTH + GOAL_EMPTY_WIDTH + mGoalOffset, GOAL_WIDTH + margin);
        }

        if (mGoal1RArmTopLeft == null) {
            mGoal1RArmTopLeft = new Point(GOAL_WIDTH + mGoalOffset, GOAL_HEIGHT - GOAL_WIDTH + margin);
        }

        if (mGoal1RArmBottomRight == null) {
            mGoal1RArmBottomRight = new Point(GOAL_WIDTH + GOAL_EMPTY_WIDTH + mGoalOffset, GOAL_HEIGHT + margin);
        }


        //draw GOAL 2
        if (mGoal2TopLeft == null) {
            mGoal2TopLeft = new Point(img.cols() - GOAL_WIDTH - mGoalOffset, margin);
        }

        if (mGoal2BottomRight == null) {
            mGoal2BottomRight = new Point(img.cols() - mGoalOffset, GOAL_HEIGHT + margin);
        }

        if (mGoal2LArmTopLeft == null) {
            mGoal2LArmTopLeft = new Point(img.cols() - GOAL_WIDTH - GOAL_EMPTY_WIDTH - mGoalOffset, margin);
        }

        if (mGoal2LArmBottomRight == null) {
            mGoal2LArmBottomRight = new Point(img.cols() - GOAL_WIDTH - mGoalOffset, GOAL_WIDTH + margin);
        }

        if (mGoal2RArmTopLeft == null) {
            mGoal2RArmTopLeft = new Point(img.cols() - GOAL_WIDTH - GOAL_EMPTY_WIDTH - mGoalOffset,
                    GOAL_HEIGHT - GOAL_WIDTH + margin);
        }

        if (mGoal2RArmBottomRight == null) {
            mGoal2RArmBottomRight = new Point(img.cols() - GOAL_WIDTH - mGoalOffset, GOAL_HEIGHT + margin);
        }

        Core.rectangle(img, mGoal1TopLeft, mGoal1BottomRight, new Scalar(51, 181, 229), -1);
        Core.rectangle(img, mGoal1LArmTopLeft, mGoal1LArmBottomRight, new Scalar(51, 181, 229), -1);
        Core.rectangle(img, mGoal1RArmTopLeft, mGoal1RArmBottomRight, new Scalar(51, 181, 229), -1);

        Core.rectangle(img, mGoal2TopLeft, mGoal2BottomRight, new Scalar(255, 68, 68), -1);
        Core.rectangle(img, mGoal2LArmTopLeft, mGoal2LArmBottomRight, new Scalar(255, 68, 68), -1);
        Core.rectangle(img, mGoal2RArmTopLeft, mGoal2RArmBottomRight, new Scalar(255, 68, 68), -1);
    }

    private void setPassingDirection(Mat img) {
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

        drawSoccerBall(img, endPoint);
    }

    private void simulateButtonPress(final Button button) {
        // Run the button's onClick functionality.
        button.performClick();

        // Make the button look pressed.
        button.setPressed(true);
        button.invalidate();

        // Reset the button to normal after a small delay.
        button.postDelayed(new Runnable() {
           public void run() {
               button.setPressed(false);
               button.invalidate();
           }
        }, 100);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mTutorialMode && !mDisplayActionButton) {
            return super.onTouchEvent(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                simulateButtonPress(mActionButton);
        }

        return super.onTouchEvent(event);
    }

    public void updateScoreView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView scoreText = (TextView) findViewById(R.id.score);
                scoreText.setText("Score: " + mSoccerGame.getScore());
            }
        });
    }

    public void onNewGamePressed(View v) {
        mSoccerGame.reset();
        updateScoreView();
    }

    @Override
    protected void initGame(int width, int height) {
        mSoccerGame = new SoccerGame(width, height, 5, this);
        if (!mCountingDown) {
            mSoccerGame.pauseCountdown();
        }
    }

    private Rect roiForBall() {
        // Get the model data about the ball.
        Point ballLocation = mSoccerGame.getBallLocation();
        int ballRadius = mSoccerGame.getBallRadius();

        // Create a region of interest based on the location of the ball.
        Rect roi = new Rect();
        roi.x = Math.max((int) ballLocation.x - ballRadius, 0);
        roi.y = Math.max((int) ballLocation.y - ballRadius, 0);
        roi.width = Math.min(ballRadius * 2, mSoccerGame.getFieldWidth() - roi.x);
        roi.height = Math.min(ballRadius * 2, mSoccerGame.getFieldHeight() - roi.y);

        return roi;
    }

    private Point findClosestEuglenaToBall(Mat frame) {
        // Get the model data about the ball.
        Point ballLocation = mSoccerGame.getBallLocation();

        Rect roi = roiForBall();

        // Find all things that look like Euglena in the region of interest.
        List<Point> euglenaLocations = ImageProcessing.findEuglenaInRoi(frame, roi);

        // Find the location of the Euglena that is closest to the ball.
        return MathUtil.findClosestPoint(ballLocation, euglenaLocations);
    }

    private void updateZoomView(Mat frame) {
        Rect roi = roiForBall();

        Mat zoomMat = new Mat(frame, roi);
        final Bitmap zoomBitmap = Bitmap.createBitmap(zoomMat.cols(), zoomMat.rows(),
                Bitmap.Config.ARGB_8888);

        Utils.matToBitmap(zoomMat, zoomBitmap);

        runOnUiThread(new Runnable() {
            public void run() {
                ImageView zoomView = (ImageView) findViewById(R.id.zoomView);
                zoomView.setImageBitmap(zoomBitmap);
            }
        });
    }

    @Override
    protected void updateGame(Mat frame, long timeDelta) {
        updateZoomView(frame);
        if (mSoccerGame.isPassing()) {
            mSoccerGame.passingFrame(timeDelta);
        } else if (mSoccerGame.isBouncing()) {
            mSoccerGame.bouncingFrame(timeDelta);
        } else {
            // Move the ball to the closest centroid to the ball.
            if (mTracking) {
                Point closestEuglenaLocation = findClosestEuglenaToBall(frame);
                if (closestEuglenaLocation != null) {
                    mSoccerGame.updateBallLocation(closestEuglenaLocation, timeDelta);
                }
            }

            blinkingArrow(frame);
        }

        drawGoals(frame);
        drawBallBlinker(frame);
        setPassingDirection(frame);
        if (mGoalScored) {
            displayGoalMessage(frame, System.currentTimeMillis());
        }

        displayVelocity(frame);

        drawScaleBar(frame);

        playSoundEffects();
    }

    public void blinkingArrow(Mat img) {
        if (!mDrawBlinkingArrow) {
            return;
        }

        if ((mSoccerGame.getTimeLeftInTurn() / 1000) % 2 == 0) {
            Scalar color;
            SoccerGame.Direction currentDirection = mSoccerGame.getCurrentTurn();
            if (currentDirection == SoccerGame.Direction.RIGHT) {
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
                        "be off their circadian rhythms or may just be tired."
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

    public void displayVelocity(Mat img) {
        if (!mDisplayVelocity) {
            return;
        }

        String velocityString = Double.toString(mSoccerGame.getVelocity());
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
        tutorialTextView.setTextSize(18);
        mDrawBall = mTutorial.shouldDrawBall();
        mDrawDirection = mTutorial.shouldDrawDirection();
        mTracking = mTutorial.shouldTrack();
        mDrawGoals = mTutorial.shouldDrawGoals();
        mDrawCentroids = mTutorial.shouldDrawCentroids();
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

        mDisplayVelocity = mTutorial.shouldDisplayVelocity();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Button button = (Button) findViewById(R.id.tutorialButton);

                button.setText(mTutorial.getButtonTextResource());
            }
        });

        mDisplayActionButton = mTutorial.shouldDisplayActionButton();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Button button = (Button) findViewById(R.id.action_button);

                if (mDisplayActionButton) {
                    button.setVisibility(View.VISIBLE);
                } else {
                    button.setVisibility(View.INVISIBLE);
                }
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

    public void drawSoccerBall(Mat img, Point ballCoordinates) {
        //first, statically draw a soccer ball
        //Draws filled, white circle
        Core.circle(img, ballCoordinates, mSoccerBallRadius, new Scalar(255, 255, 255), -1);
        //Draws black outline of soccerball
        Core.circle(img, ballCoordinates, mSoccerBallRadius, new Scalar(0, 0, 0), 5);
        //Draw pentagons
        mPentagons = new ArrayList<MatOfPoint>();
        mPentagons.add(0, new MatOfPoint(new Point(ballCoordinates.x, ballCoordinates.y - 12),
                new Point(ballCoordinates.x - 11, ballCoordinates.y - 4),
                new Point(ballCoordinates.x - 6, ballCoordinates.y + 9),
                new Point(ballCoordinates.x + 6, ballCoordinates.y + 9),
                new Point(ballCoordinates.x + 11, ballCoordinates.y - 4)));
        mPentagons.add(1, new MatOfPoint(new Point(ballCoordinates.x - 10, ballCoordinates.y - 20),
                new Point(ballCoordinates.x - 20, ballCoordinates.y - 12),
                new Point(ballCoordinates.x - 25, ballCoordinates.y - 12),
                new Point(ballCoordinates.x - 18, ballCoordinates.y - 19),
                new Point(ballCoordinates.x - 10, ballCoordinates.y - 25)));
        mPentagons.add(2, new MatOfPoint(new Point(ballCoordinates.x + 10, ballCoordinates.y - 20),
                new Point(ballCoordinates.x + 20, ballCoordinates.y - 12),
                new Point(ballCoordinates.x + 25, ballCoordinates.y - 12),
                new Point(ballCoordinates.x + 18, ballCoordinates.y - 19),
                new Point(ballCoordinates.x + 10, ballCoordinates.y - 25)));
        mPentagons.add(3, new MatOfPoint(new Point(ballCoordinates.x - 23, ballCoordinates.y + 3),
                new Point(ballCoordinates.x - 16, ballCoordinates.y + 16),
                new Point(ballCoordinates.x - 19, ballCoordinates.y + 18),
                new Point(ballCoordinates.x - 26, ballCoordinates.y + 11),
                new Point(ballCoordinates.x - 27, ballCoordinates.y + 3)));
        mPentagons.add(4, new MatOfPoint(new Point(ballCoordinates.x + 23, ballCoordinates.y + 3),
                new Point(ballCoordinates.x + 16, ballCoordinates.y + 16),
                new Point(ballCoordinates.x + 19, ballCoordinates.y + 18),
                new Point(ballCoordinates.x + 26, ballCoordinates.y + 11),
                new Point(ballCoordinates.x + 27, ballCoordinates.y + 3)));
        mPentagons.add(5, new MatOfPoint(new Point(ballCoordinates.x - 6, ballCoordinates.y + 22),
                new Point(ballCoordinates.x + 6, ballCoordinates.y + 22),
                new Point(ballCoordinates.x + 8, ballCoordinates.y + 27),
                new Point(ballCoordinates.x - 8, ballCoordinates.y + 27)));
        Core.fillPoly(img, mPentagons, new Scalar(150, 150, 150));
    }

    private void playSoundEffects() {
        if (mSoccerGame.mSoundIsBounced) {
            mSoundEffects.play(soundIds[0], 1, 1, 1, 0, 1);
        }

        if (mSoccerGame.isPassing() && !mPassSoundPlayed && mSoccerGame.isTherePassingDirection()) {
            mSoundEffects.play(soundIds[1], 1, 1, 1, 0, 1);
            mPassSoundPlayed = true;
        } else if (!mSoccerGame.isPassing()) {
            mPassSoundPlayed = false;
        }

        if (mSoccerGame.mSoundOutOfBounds) {
            mSoundEffects.play(soundIds[2], 1, 1, 1, 0, 1);
            mSoccerGame.setSoundOutOfBoundsFalse();
        }

        if (mSoundGoalScored) {
            mSoundEffects.play(soundIds[3], 1, 1, 1, 0, 1);
            mSoundGoalScored = false;
        }

        if (mSoccerGame.isBouncing() && !mBounceSoundPlayed) {
            mSoundEffects.play(soundIds[4], 1, 1, 1, 0, 1);
            mBounceSoundPlayed = true;
        } else if (!mSoccerGame.isBouncing()) {
            mBounceSoundPlayed = false;
        }
    }

    public void actionButtonPressed(View v) {
        if (mSoccerGame.getVelocity() > 0) {
            mSoccerGame.passBall();
        }
        else {
            mSoccerGame.bounceBall();
        }
    }
}