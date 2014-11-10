package edu.stanford.riedel_kruse.bioticgames;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
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
import org.opencv.video.BackgroundSubtractorMOG;
import org.opencv.video.BackgroundSubtractorMOG2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2,
        SoccerGameDelegate
{
    /**
     * Activity lifecycle callbacks
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (DEBUG_MODE) {
            mDebugImageViews = new ImageView[NUM_DEBUG_VIEWS];
            createDebugViews(NUM_DEBUG_VIEWS);
        }

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mCentroids = new ArrayList<Point>();
        mContours = new ArrayList<MatOfPoint>();

        // -1 to indicate that this value is not yet initialized.
        mLastTimestamp = -1;
    }

    private void createDebugViews(int numViews) {
        LinearLayout layout = (LinearLayout) findViewById(R.id.camera_activity_layout);

        for (int i = 0; i < numViews; i++) {
            ImageView imageView = new ImageView(this);
            imageView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT, 1));
            layout.addView(imageView);
            mDebugImageViews[i] = imageView;
        }
    }

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

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView textView = (TextView) findViewById(R.id.playerTurn);
                if (currentTurn == SoccerGame.Turn.RED) {
                    textView.setText("Turn: Red");
                } else {
                    textView.setText("Turn: Blue");
                }
            }
        });
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

                message += " Player Goal!";

                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        updateScoreViews();
    }

    public void onOutOfBounds()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Out of Bounds!",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Mat processFrame(Mat frameGray, Mat frameRgba) {
        // If a soccer game instance is not defined, create one.
        if (mSoccerGame == null)
        {
            mSoccerGame = new SoccerGame(frameRgba.cols(), frameRgba.rows(), this);
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


        if (mSwapping)
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

            mROI.x = Math.max((int) ballLocation.x - ballRadius, 0);
            mROI.y = Math.max((int) ballLocation.y - ballRadius, 0);
            mROI.width = Math.min(ballRadius * 2, mSoccerGame.getFieldWidth() - mROI.x);
            mROI.height = Math.min(ballRadius * 2, mSoccerGame.getFieldHeight() - mROI.y);

            //Log.d(TAG, "ballLocation.x " + ballLocation.x);
            //Log.d(TAG, "ballLocation.y " + ballLocation.y);
            //Log.d(TAG, "mROI.x " + mROI.x);
            //Log.d(TAG, "mROI.y " + mROI.y);
            //Log.d(TAG, "mROI.width " + mROI.width);
            //Log.d(TAG, "mROI.height " + mROI.height);

            Mat roiMat = mImgProcMat.submat(mROI.y, mROI.y + mROI.height, mROI.x, mROI.x + mROI.width);

            // Threshold based on hue and saturation (color detection) to eliminate things that are not
            // euglena.
            Core.inRange(roiMat, new Scalar(50, 50, 0), new Scalar(96, 200, 255),
                    roiMat);
            // Reduce noise in the ROI by using morphological opening and closing.
            reduceNoise(roiMat);
            // DEBUG: Show what the roiMat looks like so it can be visually debugged.
            debugShowMat(roiMat);

            // Detect contours in the ROI to find the shapes of euglena.
            findContours(roiMat);
            if (DEBUG_MODE) {
                Imgproc.drawContours(roiMat, mContours, -1, new Scalar(255, 0, 0), 1);
            }

            // Find the centroids associated with the detected contours.
            findContourCentroids();
            if (DEBUG_MODE) {
                for (Point centroid : mCentroids) {
                    Core.circle(frameRgba, new Point(centroid.x + mROI.x, centroid.y + mROI.y), 4,
                            new Scalar(0, 255, 0));
                }
            }

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
            mSoccerGame.updateBallLocation(closestCentroid, timeDelta);
        }

        drawBall(frameRgba);
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
        // TODO: Draw power-ups

//        drawDirection(frameRgba);
//        updateROI(mTrackedCentroid, mImgProcMat.width(), mImgProcMat.height());
//        // TODO: Update the ball's location to the closest centroid's location.
//
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                time2 = System.currentTimeMillis();
//                if (playerSwapBuffer) {
//                    countTime = (int) (BUFFER_TIME + time - time2) / 1000;
//                    if (countTime == 0) {
//                        playerSwapBuffer = false;
//                        time = System.currentTimeMillis();
//                        time2 = System.currentTimeMillis();
//                    }
//
//                    TextView countView = (TextView) findViewById(R.id.countDown);
//                    countView.setText("Swap!: " + countTime);
//                } else {
//                    countTime = (int) (TIMER_TURN + time - time2) / 1000;
//                    if (countTime == 0) {
//                        swapPlayers();
//                    }
//                    TextView countView = (TextView) findViewById(R.id.countDown);
//                    countView.setText("Countdown: " + countTime);
//                }
//            }
//        });
//
//
//        drawGoals(frameRgba);
//
//        if (mTrackedCentroid != null) {
//            checkGoalReached(frameRgba);
//        }
//
//        // TODO: Limit to ROI if there is one. If there isn't one, don't.
//
//        Mat roi = null;
//
//        // If an ROI is defined, use that ROI.
//        if (mROITopLeft != null && mROIBottomRight != null) {
//            roi = mImgProcMat.submat((int) mROITopLeft.y, (int) mROIBottomRight.y,
//                    (int) mROITopLeft.x, (int) mROIBottomRight.x);
//        }
//        // Otherwise the ROI is the entire matrix.
//        else {
//            roi = mImgProcMat;
//            mROITopLeft = new Point(0, 0);
//            mROIBottomRight = new Point(mImgProcMat.width(), mImgProcMat.height());
//        }
//
//        // If we aren't yet tracking a centroid, place in center.
//        if (mTrackedCentroid == null) {
//            resetBall(frameRgba);
//        }
//
//        // If we are already tracking a centroid, find the centroid in the current image that is
//        // closest to the one that we are tracking.
//        else {
//            if (passing) {
//                throwBallAnimation(frameRgba);
//            } else if (playerSwapBuffer) {
//                //do nothing
//
//            } else {
//
//                if (resetGame) {
//                    resetBall(frameRgba);
//                    blueTurn = true;
//                    redTurn = true;
//                    playerSwapBuffer = true;
//                    time = System.currentTimeMillis();
//                    time2 = System.currentTimeMillis();
//                    countTime = 0;
//                    resetGame = false;
//                }
//                // If no centroids were found, just return the image since we can't do any point tracking.
//                if (mCentroids.size() == 0) {
//                    drawROI(frameRgba);
//                    Tapped = false;
//                }
//
//
//
//            /*if(playerSwapBuffer)
//            {
//                time = System.currentTimeMillis();
//                time2 = System.currentTimeMillis();
//
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        while (timeGap > 0) {
//                            long gap = BUFFER_TIME + time2 - time;
//                            timeGap = ((int) (long) gap)/1000;
//                            time = System.currentTimeMillis();
//                            TextView textView = (TextView) findViewById(R.id.countDown);
//                            textView.setText("Countdown:" + (timeGap + 1));
//                        }
//                    }
//
//                });
//
//                playerSwapBuffer = false;
//            }*/
//                else {
//                    double minDistance = Double.MAX_VALUE;
//                    Point closestCentroid = null;
//                    for (Point centroid : mCentroids) {
//                        // Translate all of the centroid points to be in image coordinates instead of ROI
//                        // coordinates.
//                        centroid.x = centroid.x + mROITopLeft.x;
//                        centroid.y = centroid.y + mROITopLeft.y;
//                        double distance = Math.sqrt(Math.pow(centroid.x - mTrackedCentroid.x, 2) +
//                                Math.pow(centroid.y - mTrackedCentroid.y, 2));
//
//                        if (distance < minDistance) {
//                            minDistance = distance;
//                            closestCentroid = centroid;
//                        }
//                    }
//
//                    mTrackedCentroid = closestCentroid;
//                    mTrackedCentroids.add(mTrackedCentroid);
//                    if (mTrackedCentroids.size() > 10) {
//                        mTrackedCentroids.remove(0);
//                    }
//                    drawDirection(frameRgba);
//                    updateROI(mTrackedCentroid, mImgProcMat.width(), mImgProcMat.height());
//
//                    outOfBounds(frameRgba);
//
//                    if (Tapped) {
//                        Tapped = false;
//                        //"throw" the ball
//                        /*if (mTrackedCentroid.x + THROW_DISTANCE * mAverageDirectionVector.x < 0 + GOAL_WIDTH ||
//                                mTrackedCentroid.y + THROW_DISTANCE * mAverageDirectionVector.y < 0 + GOAL_WIDTH ||
//                                mTrackedCentroid.x + THROW_DISTANCE * mAverageDirectionVector.x > frameRgba.cols() - GOAL_WIDTH ||
//                                mTrackedCentroid.y + THROW_DISTANCE * mAverageDirectionVector.y > frameRgba.rows() - GOAL_WIDTH) {
//
//                            resetBall(frameRgba);
//
//                            runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    Toast.makeText(getApplicationContext(), "Out of Bounds!", Toast.LENGTH_SHORT).show();
//                                }
//                            });
//                        } else {*/
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                Toast.makeText(getApplicationContext(), "Pass!", Toast.LENGTH_SHORT).show();
//                            }
//                        });
//                        passing = true;
//                        dirY = mAverageDirectionVector.y;
//                        dirX = mAverageDirectionVector.x;
//
//
//                        dirMagnitude = Math.sqrt(dirY * dirY + dirX * dirX);
//                        normDirX = dirX / dirMagnitude;
//                        normDirY = dirY / dirMagnitude;
//
//                        throwBallAnimation(frameRgba);
//                        //throwBallInstant();
//                        //ballToTap();
//                        //}
//                    }
//                    updateROI(mTrackedCentroid, mImgProcMat.width(), mImgProcMat.height());
//                }
//            }
//        }
//
//        updateROI(mTrackedCentroid, mImgProcMat.width(), mImgProcMat.height());
//        Core.circle(frameRgba, mTrackedCentroid, 4, new Scalar(0, 0, 255));
//
//        drawROI(frameRgba);


        //  TRACKING A SINGLE ENTITY:
        //  Pick a centroid to track at random
        //  Reduce region of interest to a box around the centroid
        //  On each frame:
        //      Find the centroids in the region of interest
        //      Figure out which centroid is closest to the current centroid
        //      Replace the current centroid with the closest centroid found.
        //      Update the region of interest for the new centroid

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
        Scalar color;
        SoccerGame.Turn currentTurn = mSoccerGame.getCurrentTurn();
        if (currentTurn == SoccerGame.Turn.RED)
        {
            // Red
            color = new Scalar(255, 0, 0);
        }
        else
        {
            // Blue
            color = new Scalar(0, 0, 255);
        }
        Core.circle(img, mSoccerGame.getBallLocation(), mSoccerGame.getBallRadius(), color);
    }

    private void drawGoals(Mat img) {
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

        Core.rectangle(img, mGoal1TopLeft, mGoal1BottomRight, new Scalar(255, 0, 0), -1);
        Core.rectangle(img, mGoal1LArmTopLeft, mGoal1LArmBottomRight, new Scalar(255, 0, 0), -1);
        Core.rectangle(img, mGoal1RArmTopLeft, mGoal1RArmBottomRight, new Scalar(255, 0, 0), -1);

        Core.rectangle(img, mGoal2TopLeft, mGoal2BottomRight, new Scalar(0, 0, 255), -1);
        Core.rectangle(img, mGoal2LArmTopLeft, mGoal2LArmBottomRight, new Scalar(0, 0, 255), -1);
        Core.rectangle(img, mGoal2RArmTopLeft, mGoal2RArmBottomRight, new Scalar(0, 0, 255), -1);
    }

    private void drawPassingDirection(Mat img) {
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

    /*private void throwBallInstant() {
        mTrackedCentroid = new Point(mTrackedCentroid.x + THROW_DISTANCE
                * mAverageDirectionVector.x, mTrackedCentroid.y + THROW_DISTANCE
                * mAverageDirectionVector.y);
    }

    private void throwBallAnimation(Mat img) {

        Tapped = false;

        if (frames < FRAMES_PER_THROW) {
            mTrackedCentroid = new Point(mTrackedCentroid.x + (THROW_DISTANCE / FRAMES_PER_THROW)
                    * normDirX, mTrackedCentroid.y + (THROW_DISTANCE / FRAMES_PER_THROW)
                    * normDirY);

            frames++;
        } else {
            passing = false;
            frames = 0;
        }

        if (mTrackedCentroid.x < 0 + GOAL_WIDTH || mTrackedCentroid.x > img.cols() - GOAL_WIDTH) {
            normDirX = -1 * normDirX;
        } else if (mTrackedCentroid.y < 0 + GOAL_WIDTH || mTrackedCentroid.y > img.rows() - GOAL_WIDTH) {
            normDirY = -1 * normDirY;
        }
    }*/

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //Tapped = true;
        tapX = event.getX();
        tapY = event.getY();

        return super.onTouchEvent(event);
    }

    public void passButtonPressed(View v) {
        mSoccerGame.passBall();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Pass!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void updateScoreViews()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView textView = (TextView) findViewById(R.id.bPoints);
                textView.setText("Blue Player: \n" + mSoccerGame.getBluePlayerPoints());
                TextView textView2 = (TextView) findViewById(R.id.rPoints);
                textView2.setText("Red Player: \n" + mSoccerGame.getRedPlayerPoints());
            }
        });
    }

    public void onNewGamePressed(View v)
    {
        mSoccerGame.reset();
        updateScoreViews();
    }

    public static final String TAG = "edu.stanford.riedel-kruse.bioticgames.CameraActivity";
    public static final boolean DEBUG_MODE = true;
    public static final int NUM_DEBUG_VIEWS = 1;
    public static final int GOAL_HEIGHT = 400;
    public static final int GOAL_WIDTH = 10;
    public static final int GOAL_EMPTY_WIDTH = 40;  //Width of empty space of the goal
    public static final int SWAP_TIME = 5000;

    private SoccerGame mSoccerGame;

    private ImageView[] mDebugImageViews;
    private Bitmap mDebugBitmap;
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mImgProcMat;
    private List<Point> mCentroids;
    private List<MatOfPoint> mContours;
    private Rect mROI;

    private long mLastTimestamp;
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
        return processFrame(inputFrame.gray(), inputFrame.rgba());
    }

    /** End CvCameraViewListener2 */
}

/* to turn off autofocus:
http://answers.opencv.org/question/21377/how-turn-off-autofocus-with-camerabridgeviewbase/
 */