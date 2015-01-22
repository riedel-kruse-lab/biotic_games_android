package edu.stanford.riedel_kruse.bioticgames;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Layout;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
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

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.riedel_kruse.bioticgamessdk.BioticGameActivity;

public class GameActivity extends BioticGameActivity implements SoccerGameDelegate
{
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
    private boolean mDrawCentroids;
    private boolean mDisplayVelocity;
    private boolean mDrawBlinkingArrow;
    private boolean mCountingDown;

    private SoccerGame mSoccerGame;

    private ImageView[] mDebugImageViews;
    private Bitmap mDebugBitmap;
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mImgProcMat;
    private List<Point> mCentroids;
    private List<MatOfPoint> mContours;
    private Rect mROI;

    private ImageView mZoomViews;


    private long mLastTimestamp;
    private boolean mSwapping;
    private long mSwapCountdown;

    private Point mPrevBallLocation;
    private long mTimeWithoutMovingCountdown;

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
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mTutorialMode = intent.getBooleanExtra(EXTRA_TUTORIAL_MODE, false);

        setContentView(R.layout.activity_game);

        mDrawBall = true;
        mTracking = true;
        mDrawDirection = true;
        mDrawGoals = true;
        mDrawCentroids = true;
        mDisplayVelocity = true;
        mDrawBlinkingArrow = true;
        mCountingDown = true;

        // If we're in tutorial mode, show the tutorial layout.
        if (mTutorialMode)
        {
            mTutorial = new Tutorial();
            findViewById(R.id.tutorialLayout).setVisibility(View.VISIBLE);
            updateTutorialViews();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (DEBUG_MODE) {
            mDebugImageViews = new ImageView[NUM_DEBUG_VIEWS];
            createDebugViews(NUM_DEBUG_VIEWS);
        }

        //createZoomViews();

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mCentroids = new ArrayList<Point>();
        mContours = new ArrayList<MatOfPoint>();

        // -1 to indicate that this value is not yet initialized.
        mLastTimestamp = -1;
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

//    private void createZoomViews() {
//        LinearLayout layout = (LinearLayout) findViewById(R.id.camera_activity_layout);
//
//        ImageView imageView = new ImageView(this);
//        imageView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
//                LayoutParams.MATCH_PARENT, 1));
//        layout.addView(imageView);
//        mZoomViews = imageView;
//    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disableCameraView();
    }

    @Override
    public void onPause() {
        super.onPause();
        disableCameraView();
    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallback);
    }

    @Override
    protected int getCameraViewResourceId()
    {
        return 0;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.camera, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * End activity lifecycle callbacks
     */

    private void disableCameraView() {
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    private void debugShowMat(Mat mat) {
        debugShowMat(mat, 0);
    }

    private void debugShowMat(Mat mat, final int viewIndex) {
        if (DEBUG_MODE) {
            int width = mat.cols();
            int height = mat.rows();
            if (mDebugBitmap == null) {
                mDebugBitmap = Bitmap.createBitmap(width, height,
                        Bitmap.Config.ARGB_8888);
            }

            if (mDebugBitmap.getWidth() != width) {
                mDebugBitmap.setWidth(width);
            }

            if (mDebugBitmap.getHeight() != height) {
                mDebugBitmap.setHeight(height);
            }

            Utils.matToBitmap(mat, mDebugBitmap);

            runOnUiThread(new Runnable() {
                public void run() {
                    mDebugImageViews[viewIndex].setImageBitmap(mDebugBitmap);
                }
            });
        }
    }

    /*
     * SoccerGameDelegate functions.
     */

    public void onChangedTurn(final SoccerGame.Turn currentTurn)
    {
        // TODO: Freeze the game for some time so players can switch without stress.
        mSwapping = true;
        mSwapCountdown = SWAP_TIME;
        updateSwapCountdown();
    }

    public void onGoalScored(final SoccerGame.Turn currentTurn)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String message = "";
                if (currentTurn == SoccerGame.Turn.RED)
                {
                    message += "Red";
                }
                else
                {
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

    public void onPickupButtonPressed(final SoccerGame.Turn currentTurn)
    {
        /*runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String message = "";
                if (currentTurn == SoccerGame.Turn.RED)
                {
                    message += "Red";
                }
                else
                {
                    message += "Blue";
                }

                message += " Player Picked Up Ball, But Lost ";
                message += mSoccerGame.getPointsScored() + " Point!";

                Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
        });*/

        updateScoreViews();
    }

    public void onOutOfBounds()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                Toast toast = Toast.makeText(getApplicationContext(), "Out of Bounds!", Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
        });
    }

    private Mat processFrame(Mat frameGray, Mat frameRgba) {
        // If a soccer game instance is not defined, create one.
        if (mSoccerGame == null)
        {
            mSoccerGame = new SoccerGame(frameRgba.cols(), frameRgba.rows(), this);
            if (!mCountingDown)
            {
                mSoccerGame.pauseCountdown();
            }
        }

        long currentTimestamp = System.currentTimeMillis();

        long timeDelta;

        if (mLastTimestamp == -1)
        {
            timeDelta = 0;
        }
        else
        {
            timeDelta = currentTimestamp - mLastTimestamp;
        }

        mLastTimestamp = currentTimestamp;

        if(mSoccerGame.turnCountGreaterThan())
        {
            mSoccerGame.pauseCountdown();
            showWinner();
        }


        if (mSwapping && mCountingDown && !mSoccerGame.returnCountdownPaused())
        {
            mSwapCountdown -= timeDelta;
        }
        else if (mSoccerGame.isPassing())
        {
            mSoccerGame.passingFrame(timeDelta);
        }
        else
        {
            // Convert the frame to the right format for HSV processing.
            Imgproc.cvtColor(frameRgba, mImgProcMat, Imgproc.COLOR_BGR2HSV);

            // Define the ROI based on the location of the ball.
            Point ballLocation = mSoccerGame.getBallLocation();
            int ballRadius = mSoccerGame.getBallRadius();

            /*if (mPrevBallLocation != null && mPrevBallLocation.x == ballLocation.x &&
                    mPrevBallLocation.y == ballLocation.y)
            {
                mTimeWithoutMovingCountdown -= timeDelta;
            }
            else
            {
                mTimeWithoutMovingCountdown = MILLISECONDS_BEFORE_BALL_AUTO_ASSIGN;
            }*/

            mPrevBallLocation = ballLocation;

            if (mTimeWithoutMovingCountdown <= 0 || mPrevBallLocation.x == ballLocation.x &&
                    mPrevBallLocation.y == ballLocation.y)
            {
                // Choose the entire field as the ROI.
                scanDoubleROI();
                mTimeWithoutMovingCountdown = 1;
            }
            else
            {
                mROI.x = Math.max((int) ballLocation.x - ballRadius, 0);
                mROI.y = Math.max((int) ballLocation.y - ballRadius, 0);
                mROI.width = Math.min(ballRadius * 2, mSoccerGame.getFieldWidth() - mROI.x);
                mROI.height = Math.min(ballRadius * 2, mSoccerGame.getFieldHeight() - mROI.y);
            }

            Mat roiMat = mImgProcMat.submat(mROI.y, mROI.y + mROI.height, mROI.x, mROI.x + mROI.width);

            Mat zoomMat = frameRgba.submat(mROI.y, mROI.y + mROI.height, mROI.x, mROI.x + mROI.width);
            if (mTimeWithoutMovingCountdown > 0)
            {
                debugShowMat(zoomMat);
            }

            // Threshold based on hue and saturation (color detection) to eliminate things that are not
            // euglena.
            Core.inRange(roiMat, new Scalar(50, 50, 0), new Scalar(96, 200, 255),
                    roiMat);
            // Reduce noise in the ROI by using morphological opening and closing.
            reduceNoise(roiMat);
            // DEBUG: Show what the roiMat looks like so it can be visually debugged.
            // TODO: There is a weird bug here when the roiMat is the entire image where the Bitmap
            // is apparently not big enough.


            // Detect contours in the ROI to find the shapes of euglena.
            findContours(roiMat);
            if (DEBUG_MODE) {
                Imgproc.drawContours(roiMat, mContours, -1, new Scalar(255, 0, 0), 1);
            }

            // Find the centroids associated with the detected contours.
            findContourCentroids();
            /*if (DEBUG_MODE && mDrawCentroids) {
                for (Point centroid : mCentroids) {
                    Core.circle(frameRgba, new Point(centroid.x + mROI.x, centroid.y + mROI.y), 4,
                            new Scalar(0, 255, 0));
                }
            }*/

            double minDistance = Double.MAX_VALUE;
            Point closestCentroid = null;
            for (Point centroid : mCentroids) {
                // Translate all of the centroid points to be in image coordinates instead of ROI
                // coordinates.
                centroid.x = centroid.x + mROI.x;
                centroid.y = centroid.y + mROI.y;
                double distance = Math.sqrt(Math.pow(centroid.x - ballLocation.x, 2) +
                        Math.pow(centroid.y - ballLocation.y, 2));

                if (distance < minDistance) {
                    minDistance = distance;
                    closestCentroid = centroid;
                }
            }

            // Move the ball to the closest centroid to the ball.
            if (mTracking)
            {
                mSoccerGame.updateBallLocation(closestCentroid, timeDelta);
            }

            blinkingArrow(frameRgba);
        }

        //drawBall(frameRgba);
        drawBallBlinker(frameRgba);
        drawGoals(frameRgba);
        drawPassingDirection(frameRgba);
        if (mSwapping)
        {
            updateSwapCountdown();
            if (mSwapCountdown <= 0)
            {
                mSwapping = false;
            }
        }
        else
        {
            updateCountdown();
        }

        displayVelocity(frameRgba);

        drawScaleBar(frameRgba);

        return frameRgba;
    }

    private void reduceNoise(Mat mat) {
        Size size = new Size(5, 5);
        Mat structuringElement = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, size);

        Imgproc.dilate(mat, mat, structuringElement);
        Imgproc.erode(mat, mat, structuringElement);

        Imgproc.erode(mat, mat, structuringElement);
        Imgproc.dilate(mat, mat, structuringElement);
    }

    private void findContours(Mat img) {
        mContours.clear();
        Mat hierarchy = new Mat();
        Imgproc.findContours(img, mContours, hierarchy, Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_SIMPLE);
    }

    private void findContourCentroids() {
        mCentroids.clear();
        for (MatOfPoint contour : mContours) {
            Moments p = Imgproc.moments(contour, false);
            Point centroid = new Point(p.get_m10() / p.get_m00(), p.get_m01() / p.get_m00());
            mCentroids.add(centroid);
        }
    }

    private void drawBall(Mat img)
    {
        if (!mDrawBall)
        {
            return;
        }

        Scalar color;
        SoccerGame.Turn currentTurn = mSoccerGame.getCurrentTurn();
        if (currentTurn == SoccerGame.Turn.RED)
        {
            // Red
            color = new Scalar(255, 68, 68);
        }
        else
        {
            // Blue
            color = new Scalar(51, 181, 229);
        }
        Core.circle(img, mSoccerGame.getBallLocation(), mSoccerGame.getBallRadius(), color, 10);
    }

    private void drawGoals(Mat img) {
        if (!mDrawGoals)
        {
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
        if (!mDrawDirection)
        {
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

    private void updateCountdown()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                long timeLeft = mSoccerGame.getTimeLeftInTurn() / 1000;
                TextView countView = (TextView) findViewById(R.id.countDown);
                countView.setText("Countdown: " + timeLeft);
            }
        });

    }

    private void updateSwapCountdown()
    {
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
        //Tapped = true;
        tapX = event.getX();
        tapY = event.getY();

        Point tappedPoint = new Point(tapX, tapY);

        mSoccerGame.passBall();

        return super.onTouchEvent(event);
    }

    public void updateScoreViews()
    {
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

    public void onNewGamePressed(View v)
    {
        mSoccerGame.reset();
        updateScoreViews();
    }

    public void onInstructionsPressed(View v)
    {
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



    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    mImgProcMat = new Mat();
                    mROI = new Rect();
                    mOpenCvCameraView.enableView();
                    break;
                }
                default: {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    /**
     * CvCameraViewListener2 Interface
     */

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat flippedFrame = inputFrame.rgba();

        return processFrame(inputFrame.gray(), flippedFrame);
    }

    @Override
    protected Mat processCameraFrame(Mat inputFrame)
    {
        // Flip the input frame so that it's orientation matches the orientation of the phone
        // TODO: This sort of operation should be handled by the SDK automatically depending on
        // the orientation of the phone.
        Core.flip(inputFrame, inputFrame, -1);
        return null;
    }

    public void infoButtonPressed(View v)
    {
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

    public void creditsButtonPressed(View v)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Credits");
        builder.setMessage("Honesty Kim: etc\nDaniel Chiu: Programming\n" +
                "Seung Ah Lee: Optics\nAlice Chung: Euglena Biology\nSherwin Xia: Electronics\n" +
                        "Lukas Gerber: Sticker Microfluidics\nNate Cira: Microfluidics\n"+
                "Ingmar Riedel-Kruse: Advisor"
        );
        builder.setCancelable(false);
        builder.setPositiveButton("Good job guys!", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        builder.show();
    }

    /*public void pickupButtonPressed(View v)
    {
        mSoccerGame.setPickupButtonPressedTrue();

        mTimeWithoutMovingCountdown = 0;

        Toast toast = Toast.makeText(getApplicationContext(), "-1 Point!", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }*/

    public void scanWholeViewForEuglena()
    {
        mROI.x = mSoccerGame.getFieldWidth()/4;
        mROI.y = mSoccerGame.getFieldHeight()/4;
        mROI.width = mSoccerGame.getFieldWidth()/2;
        mROI.height = mSoccerGame.getFieldHeight()/2;
    }

    public void blinkingArrow(Mat img)
    {
        if (!mDrawBlinkingArrow)
        {
            return;
        }

        if((mSoccerGame.getTimeLeftInTurn() / 1000) % 2 == 0) {
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

    public void troubleShootButtonPressed(View v)
    {
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

    public void drawBallBlinker(Mat img)
    {
        if((mSoccerGame.getTimeLeftInTurn() / 1000) > 5)
        {
            drawBall(img);
        }
        else
        {
            if((mSoccerGame.getTimeLeftInTurn() / 200) % 2 == 1)
            {
                drawBall(img);
            }
        }
    }

    public void showWinner()
    {
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

    public void displayVelocity(Mat img)
    {
        if (!mDisplayVelocity)
        {
            return;
        }

        String velocityString = Double.toString(mSoccerGame.getVelocity());
        Point mStringLocation = new Point(mSoccerGame.getFieldWidth() / 10, mSoccerGame.getFieldHeight() / 1.05);

        Core.putText(img, velocityString + "um/sec", mStringLocation,
                1, 4, new Scalar(200,200,250), 5);
    }

    public void drawScaleBar(Mat img)
    {
        Core.line(img, new Point(mSoccerGame.getFieldWidth()/1.3, mSoccerGame.getFieldHeight()/1.1),
                new Point(mSoccerGame.getFieldWidth()/1.3 + 150, mSoccerGame.getFieldHeight()/1.1),
                new Scalar(200,200,250), 3);
        Core.putText(img, "100 um", new Point(mSoccerGame.getFieldWidth()/1.33, mSoccerGame.getFieldHeight()/1.04),
                1, 3, new Scalar(200,200,250), 4);
    }

    public void updateTutorialViews()
    {
        if (!mTutorialMode)
        {
            return;
        }

        TextView tutorialTextView = (TextView) findViewById(R.id.tutorialText);
        tutorialTextView.setText(mTutorial.getCurrentStringResource());
        tutorialTextView.setTextSize(20);
        mDrawBall = mTutorial.shouldDrawBall();
        mDrawDirection = mTutorial.shouldDrawDirection();
        mTracking = mTutorial.shouldTrack();
        mDrawGoals = mTutorial.shouldDrawGoals();
        mDrawCentroids = mTutorial.shouldDrawCentroids();
        mDrawBlinkingArrow = mTutorial.shouldDrawBlinkingArrow();
        mCountingDown = mTutorial.shouldCountDown();
        if (mCountingDown)
        {
            if (mSoccerGame != null)
            {
                mSoccerGame.resumeCountdown();
            }
        }
        else
        {
            if (mSoccerGame != null)
            {
                mSoccerGame.pauseCountdown();
            }
        }
        final boolean displayScores = mTutorial.shouldDisplayScores();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView redScore = (TextView) findViewById(R.id.rPoints);
                TextView blueScore = (TextView) findViewById(R.id.bPoints);

                if (displayScores)
                {
                    redScore.setVisibility(View.VISIBLE);
                    blueScore.setVisibility(View.VISIBLE);
                }
                else
                {
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

                if (displayCountdown)
                {
                    countdown.setVisibility(View.VISIBLE);
                }
                else
                {
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

    public void tutorialButtonPressed(View view)
    {
        if (!mTutorialMode)
        {
            return;
        }

        mTutorial.advance();
        updateTutorialViews();

        if (mTutorial.finished())
        {
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

    public void backToMainMenu(View view)
    {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    public void scanDoubleROI(){
        Point ballLocation = mSoccerGame.getBallLocation();
        int ballRadius = mSoccerGame.getBallRadius();

        mROI.x = Math.max((int) ballLocation.x - ballRadius *2, 0);
        mROI.y = Math.max((int) ballLocation.y - ballRadius *2, 0);
        mROI.width = Math.min(ballRadius * 4, mSoccerGame.getFieldWidth() - mROI.x);
        mROI.height = Math.min(ballRadius * 4, mSoccerGame.getFieldHeight() - mROI.y);
    }


    /** End CvCameraViewListener2 */
}

/* to turn off autofocus:
http://answers.opencv.org/question/21377/how-turn-off-autofocus-with-camerabridgeviewbase/
 */