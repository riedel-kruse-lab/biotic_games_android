package edu.stanford.riedel_kruse.bioticgames;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
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
import org.opencv.video.BackgroundSubtractor;
import org.opencv.video.BackgroundSubtractorMOG;
import org.opencv.video.BackgroundSubtractorMOG2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2
{
    /** Activity lifecycle callbacks */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (DEBUG_MODE)
        {
            mDebugImageViews = new ImageView[NUM_DEBUG_VIEWS];
            createDebugViews(NUM_DEBUG_VIEWS);
        }

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mCentroids = new ArrayList<Point>();
        mContours = new ArrayList<MatOfPoint>();

        mRandom = new Random();
    }

    private void createDebugViews(int numViews)
    {
        LinearLayout layout = (LinearLayout) findViewById(R.id.camera_activity_layout);

        for (int i = 0; i < numViews; i++)
        {
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
    public void onPause()
    {
        super.onPause();
        disableCameraView();
    }

    @Override
    public void onResume()
    {
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

    /** End activity lifecycle callbacks */

    private void disableCameraView()
    {
        if (mOpenCvCameraView != null)
        {
            mOpenCvCameraView.disableView();
        }
    }

    private void debugShowMat(Mat mat)
    {
        debugShowMat(mat, 0);
    }

    private void debugShowMat(Mat mat, final int viewIndex)
    {
        if (DEBUG_MODE)
        {
            int width = mat.cols();
            int height = mat.rows();
            if (mDebugBitmap == null)
            {
                mDebugBitmap = Bitmap.createBitmap(width, height,
                        Bitmap.Config.ARGB_8888);
            }

            if (mDebugBitmap.getWidth() != width)
            {
                mDebugBitmap.setWidth(width);
            }

            if (mDebugBitmap.getHeight() != height)
            {
                mDebugBitmap.setHeight(height);
            }

            Utils.matToBitmap(mat, mDebugBitmap);

            runOnUiThread(new Runnable()
            {
                public void run()
                {
                    mDebugImageViews[viewIndex].setImageBitmap(mDebugBitmap);
                }
            });
        }
    }

    private void drawROI(Mat img)
    {
        Core.circle(img, mTrackedCentroid, ROI_RADIUS, new Scalar(0, 0, 255));
    }

    private void drawGoals(Mat img)
    {
        int height = img.rows();
        float margin = (height - GOAL_HEIGHT) / 2;


        //draw GOAL 1
        if (mGoal1TopLeft == null)
        {
            mGoal1TopLeft = new Point(0, margin);
        }

        if (mGoal1BottomRight == null)
        {
            mGoal1BottomRight = new Point(GOAL_WIDTH, GOAL_HEIGHT + margin);
        }

        if (mGoal1LArmTopLeft == null)
        {
            mGoal1LArmTopLeft = new Point(GOAL_WIDTH, margin);
        }

        if(mGoal1LArmBottomRight == null)
        {
            mGoal1LArmBottomRight = new Point(GOAL_WIDTH + GOAL_EMPTY_WIDTH, GOAL_WIDTH + margin);
        }

        if(mGoal1RArmTopLeft == null)
        {
            mGoal1RArmTopLeft = new Point(GOAL_WIDTH,GOAL_HEIGHT - GOAL_WIDTH + margin);
        }

        if(mGoal1RArmBottomRight==null)
        {
            mGoal1RArmBottomRight = new Point(GOAL_WIDTH+GOAL_EMPTY_WIDTH, GOAL_HEIGHT + margin);
        }


        //draw GOAL 2
        if (mGoal2TopLeft == null)
        {
            mGoal2TopLeft = new Point(img.cols() - GOAL_WIDTH, margin);
        }

        if (mGoal2BottomRight == null)
        {
            mGoal2BottomRight = new Point(img.cols(), GOAL_HEIGHT + margin);
        }

        if (mGoal2LArmTopLeft == null)
        {
            mGoal2LArmTopLeft = new Point(img.cols() - GOAL_WIDTH - GOAL_EMPTY_WIDTH, margin);
        }

        if(mGoal2LArmBottomRight == null)
        {
            mGoal2LArmBottomRight = new Point(img.cols() - GOAL_WIDTH, GOAL_WIDTH + margin);
        }

        if(mGoal2RArmTopLeft == null)
        {
            mGoal2RArmTopLeft = new Point(img.cols() - GOAL_WIDTH - GOAL_EMPTY_WIDTH,GOAL_HEIGHT - GOAL_WIDTH + margin);
        }

        if(mGoal2RArmBottomRight==null)
        {
            mGoal2RArmBottomRight = new Point(img.cols() - GOAL_WIDTH, GOAL_HEIGHT + margin);
        }

        Core.rectangle(img, mGoal1TopLeft, mGoal1BottomRight, new Scalar(0, 0, 255), -1);
        Core.rectangle(img, mGoal1LArmTopLeft, mGoal1LArmBottomRight, new Scalar(0,0,255),-1);
        Core.rectangle(img, mGoal1RArmTopLeft, mGoal1RArmBottomRight, new Scalar(0,0,255),-1);

        Core.rectangle(img, mGoal2TopLeft, mGoal2BottomRight, new Scalar(255, 0, 0), -1);
        Core.rectangle(img, mGoal2LArmTopLeft, mGoal2LArmBottomRight, new Scalar(255,0,0),-1);
        Core.rectangle(img, mGoal2RArmTopLeft, mGoal2RArmBottomRight, new Scalar(255,0,0),-1);
    }

    private void checkGoalReached(Mat img)
    {
        if (mGoal1Rect == null)
        {
            mGoal1Rect = new Rect(mGoal1TopLeft, mGoal1RArmBottomRight);
        }

        if (mGoal2Rect == null)
        {
            mGoal2Rect = new Rect(mGoal2LArmTopLeft, mGoal2BottomRight);
        }

        if (mTrackedCentroid.inside(mGoal1Rect))
        {

            //Reset ball randomly
            resetBall(img);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Blue Player Goal!", Toast.LENGTH_SHORT).show();
                }
            });


        }

        if (mTrackedCentroid.inside(mGoal2Rect))
        {

            //Reset ball randomly
            resetBall(img);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Red Player Goal!", Toast.LENGTH_SHORT).show();
                }
            });

        }
    }

    private Mat processFrame(Mat frameGray, Mat frameRgba)
    {
        // Update the background subtraction model
        //mBackgroundSubtractor.apply(frameGray, mForegroundMask);
        //mBackgroundSubtractor2.apply(frameGray, mForegroundMask2);

        drawGoals(frameRgba);

        if (mTrackedCentroid != null)
        {
            checkGoalReached(frameRgba);
        }

        // TODO: Limit to ROI if there is one. If there isn't one, don't.

        Imgproc.cvtColor(frameRgba, mForegroundMask, Imgproc.COLOR_BGR2HSV);

        Mat roi = null;

        // If an ROI is defined, use that ROI.
        if (mROITopLeft != null && mROIBottomRight != null)
        {
            roi = mForegroundMask.submat((int)mROITopLeft.y, (int)mROIBottomRight.y,
                    (int)mROITopLeft.x, (int)mROIBottomRight.x);
        }
        // Otherwise the ROI is the entire matrix.
        else
        {
            roi = mForegroundMask;
            mROITopLeft = new Point(0, 0);
            mROIBottomRight = new Point(mForegroundMask.width(), mForegroundMask.height());
        }

        Core.inRange(roi, new Scalar(50, 50, 0), new Scalar(96, 200, 255),
                roi);

        reduceNoise(roi);

        debugShowMat(roi);

        findContours(roi);
        if (DEBUG_MODE)
        {
            Imgproc.drawContours(roi, mContours, -1, new Scalar(255, 0, 0), 1);
        }

        findContourCentroids();
        if (DEBUG_MODE)
        {
            for (Point centroid : mCentroids)
            {
                Core.circle(frameRgba, new Point(centroid.x + mROITopLeft.x, centroid.y + mROITopLeft.y), 4, new Scalar(0, 255, 0));
            }
        }



        // If we aren't yet tracking a centroid, place in center.
        if (mTrackedCentroid == null)
        {
            resetBall(frameRgba);
        }

        // If we are already tracking a centroid, find the centroid in the current image that is
        // closest to the one that we are tracking.
        else
        {
            // If no centroids were found, just return the image since we can't do any point tracking.
            if (mCentroids.size() == 0)
            {
                drawROI(frameRgba);
                Tapped = false;
            }
            else {
                double minDistance = Double.MAX_VALUE;
                Point closestCentroid = null;
                for (Point centroid : mCentroids) {
                    // Translate all of the centroid points to be in image coordinates instead of ROI
                    // coordinates.
                    centroid.x = centroid.x + mROITopLeft.x;
                    centroid.y = centroid.y + mROITopLeft.y;
                    double distance = Math.sqrt(Math.pow(centroid.x - mTrackedCentroid.x, 2) +
                            Math.pow(centroid.y - mTrackedCentroid.y, 2));

                    if (distance < minDistance) {
                        minDistance = distance;
                        closestCentroid = centroid;
                    }
                }

                drawBall(mTrackedCentroid, closestCentroid, frameRgba);

                mTrackedCentroid = closestCentroid;
                updateROI(mTrackedCentroid, mForegroundMask.width(), mForegroundMask.height());

                outOfBounds(frameRgba);

                if (Tapped) {
                    Tapped = false;
                    //"throw" the ball
                    if (mTrackedCentroid.x + THROW_DISTANCE * directionVector.x < 0 + GOAL_WIDTH ||
                            mTrackedCentroid.y + THROW_DISTANCE * directionVector.y < 0 + GOAL_WIDTH ||
                            mTrackedCentroid.x + THROW_DISTANCE * directionVector.x > frameRgba.cols() - GOAL_WIDTH ||
                            mTrackedCentroid.y + THROW_DISTANCE * directionVector.y > frameRgba.rows() - GOAL_WIDTH) {

                        resetBall(frameRgba);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Out of Bounds!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Pass!", Toast.LENGTH_SHORT).show();
                            }
                        });
                        //throwBallAnimation(frameRgba);
                        throwBallInstant();
                        //ballToTap();
                    }
                }
                updateROI(mTrackedCentroid, mForegroundMask.width(), mForegroundMask.height());
            }
        }

        Core.circle(frameRgba, mTrackedCentroid, 4, new Scalar(0, 0, 255));

        drawROI(frameRgba);





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

    private void reduceNoise(Mat mat)
    {
        Size size = new Size(5, 5);
        Mat structuringElement = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, size);

        Imgproc.dilate(mat, mat, structuringElement);
        Imgproc.erode(mat, mat, structuringElement);

        Imgproc.erode(mat, mat, structuringElement);
        Imgproc.dilate(mat, mat, structuringElement);

        //Imgproc.blur(mForegroundMask, mForegroundMask, new Size(4, 4));
    }

    private void findContours(Mat img)
    {
        mContours.clear();
        Mat hierarchy = new Mat();
        Imgproc.findContours(img, mContours, hierarchy, Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_SIMPLE);
    }

    private void findContourCentroids()
    {
        mCentroids.clear();
        for (MatOfPoint contour : mContours)
        {
            Moments p = Imgproc.moments(contour, false);
            Point centroid = new Point(p.get_m10() / p.get_m00(), p.get_m01() / p.get_m00());
            mCentroids.add(centroid);
        }
    }

    private void updateROI(Point centroid, int maxWidth, int maxHeight)
    {
        // TODO: Make sure that these points don't go off the edge of the image.
        mROITopLeft = new Point(Math.max(centroid.x - ROI_WIDTH / 2, 0), Math.max(centroid.y - ROI_HEIGHT / 2, 0));
        mROIBottomRight = new Point(Math.min(centroid.x + ROI_WIDTH / 2, maxWidth), Math.min(centroid.y + ROI_HEIGHT / 2, maxHeight));
    }

    private void drawBall(Point previousCenter, Point newCenter, Mat img)
    {
        // Vector for the direction
        directionVector = new Point(newCenter.x - previousCenter.x,
                newCenter.y - previousCenter.y);

        double magnitude = Math.sqrt(Math.pow(directionVector.x, 2) +
                Math.pow(directionVector.y, 2));

        // Normalize the direction vector to get a unit vector in that direction, then multiply by
        // the distance that we want, which is the radius of the ROI because the newCenter should be
        // the center of the ROI.
        directionVector.x = directionVector.x / magnitude * ROI_RADIUS;
        directionVector.y = directionVector.y / magnitude * ROI_RADIUS;

        Point ballLocation = new Point(newCenter.x + directionVector.x,
                newCenter.y + directionVector.y);

        Core.circle(img, ballLocation, BALL_RADIUS, new Scalar(255, 0, 0));
    }

    private void resetBall(Mat img)
    {
        mTrackedCentroid = new Point (img.cols()/2, img.rows()/2);
        updateROI(mTrackedCentroid, mForegroundMask.width(), mForegroundMask.height());
    }

    private void throwBallInstant()
    {
        mTrackedCentroid = new Point(mTrackedCentroid.x + THROW_DISTANCE
                * directionVector.x, mTrackedCentroid.y + THROW_DISTANCE
                * directionVector.y);
     }

    private void throwBallAnimation(Mat img)
    {
        int frames = 0;
        double dirY = directionVector.y;
        double dirX = directionVector.x;

        while (frames < FRAMES_PER_THROW)
        {
            mTrackedCentroid = new Point(mTrackedCentroid.x + (THROW_DISTANCE / FRAMES_PER_THROW)
                    * dirX, mTrackedCentroid.y + (THROW_DISTANCE / FRAMES_PER_THROW)
                    * dirY);

            drawROI(img);

            frames++;
        }
    }

    private void ballToTap()
    {
        mTrackedCentroid = new Point(tapX, tapY);
    }

    private void outOfBounds(Mat img)
    {
        if(mTrackedCentroid.x <= GOAL_WIDTH || mTrackedCentroid.y <= GOAL_WIDTH
                || mTrackedCentroid.x >= img.cols()-GOAL_WIDTH || mTrackedCentroid.y >= img.rows()-GOAL_WIDTH)
        {
            resetBall(img);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Out of Bounds!", Toast.LENGTH_SHORT).show();
                }
            });
        }

    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        Tapped = true;
        tapX = event.getX();
        tapY = event.getY();

        return super.onTouchEvent(event);
    }

    public static final String TAG = "edu.stanford.riedel-kruse.bioticgames.CameraActivity";
    public static final int BALL_RADIUS = 15;
    public static final boolean DEBUG_MODE = true;
    public static final int NUM_DEBUG_VIEWS = 1;
    public static final int ROI_WIDTH = 100;
    public static final int ROI_HEIGHT = ROI_WIDTH;
    public static final int GOAL_HEIGHT = 400;
    public static final int GOAL_WIDTH = 10;
    public static final int GOAL_EMPTY_WIDTH = 40;  //Width of empty space of the goal
    public static final int ROI_RADIUS= 50; //Radius of the circle drawn around the ROI (region of interest)
    public static final int THROW_DISTANCE = 3;
    public static final int FRAMES_PER_THROW = 10;

    private ImageView[] mDebugImageViews;
    private Bitmap mDebugBitmap;
    private CameraBridgeViewBase mOpenCvCameraView;
    private BackgroundSubtractorMOG mBackgroundSubtractor;
    private BackgroundSubtractorMOG2 mBackgroundSubtractor2;
    private Mat mForegroundMask;
    private Mat mForegroundMask2;
    private List<Point> mCentroids;
    private Point mTrackedCentroid;
    private List<MatOfPoint> mContours;
    private Random mRandom;
    private Point mROITopLeft;
    private Point mROIBottomRight;

    //private Point closestCentroid;

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

    private Point directionVector;

    private Rect mGoal1Rect;
    private Rect mGoal2Rect;

    public Boolean Tapped = false;
    public float tapX = 0;
    public float tapY = 0;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch(status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    mBackgroundSubtractor = new BackgroundSubtractorMOG();
                    mBackgroundSubtractor2 = new BackgroundSubtractorMOG2();
                    mForegroundMask = new Mat();
                    mForegroundMask2 = new Mat();
                    mOpenCvCameraView.enableView();
                    break;
                }
                default:
                {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    /** CvCameraViewListener2 Interface */

    public void onCameraViewStarted(int width, int height) {}

    public void onCameraViewStopped() {}

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        return processFrame(inputFrame.gray(), inputFrame.rgba());
    }

    /** End CvCameraViewListener2 */
}

/* to turn off autofocus:
http://answers.opencv.org/question/21377/how-turn-off-autofocus-with-camerabridgeviewbase/
 */