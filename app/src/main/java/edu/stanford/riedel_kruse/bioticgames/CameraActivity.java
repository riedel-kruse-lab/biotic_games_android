package edu.stanford.riedel_kruse.bioticgames;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
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
        Core.rectangle(img, mROITopLeft, mROIBottomRight, new Scalar(0, 0, 255));
    }

    private void drawGoals(Mat img)
    {
        int height = img.rows();
        float margin = (height - GOAL_HEIGHT) / 2;

        if (mGoal1TopLeft == null)
        {
            mGoal1TopLeft = new Point(0, margin);
        }

        if (mGoal1BottomRight == null)
        {
            mGoal1BottomRight = new Point(GOAL_WIDTH, GOAL_HEIGHT + margin);
        }

        if (mGoal2TopLeft == null)
        {
            mGoal2TopLeft = new Point(img.cols() - GOAL_WIDTH, margin);
        }

        if (mGoal2BottomRight == null)
        {
            mGoal2BottomRight = new Point(img.cols(), GOAL_HEIGHT + margin);
        }

        Core.rectangle(img, mGoal1TopLeft, mGoal1BottomRight, new Scalar(0, 0, 255), -1);
        Core.rectangle(img, mGoal2TopLeft, mGoal2BottomRight, new Scalar(0, 0, 255), -1);
    }

    private void checkGoalReached(Mat img)
    {
        if (mGoal1Rect == null)
        {
            mGoal1Rect = new Rect(mGoal1TopLeft, mGoal1BottomRight);
        }

        if (mGoal2Rect == null)
        {
            mGoal2Rect = new Rect(mGoal2TopLeft, mGoal2BottomRight);
        }

        if (mTrackedCentroid.inside(mGoal1Rect) || mTrackedCentroid.inside(mGoal2Rect))
        {
            Toast.makeText(getApplicationContext(), "Goal!", Toast.LENGTH_LONG).show();
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

        Core.inRange(roi, new Scalar(50, 15, 0), new Scalar(96, 175, 255),
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

        // If no centroids were found, just return the image since we can't do any point tracking.
        if (mCentroids.size() == 0)
        {
            return frameRgba;
        }

        // If we aren't yet tracking a centroid, pick one of the ones that was located at random.
        if (mTrackedCentroid == null)
        {
            mTrackedCentroid = mCentroids.get(mRandom.nextInt(mCentroids.size()));
            updateROI(mTrackedCentroid, mForegroundMask.width(), mForegroundMask.height());
        }
        // If we are already tracking a centroid, find the centroid in the current image that is
        // closest to the one that we are tracking.
        else
        {
            double minDistance = Double.MAX_VALUE;
            Point closestCentroid = null;
            for (Point centroid : mCentroids)
            {
                // Translate all of the centroid points to be in image coordinates instead of ROI
                // coordinates.
                centroid.x = centroid.x + mROITopLeft.x;
                centroid.y = centroid.y + mROITopLeft.y;
                double distance = Math.sqrt(Math.pow(centroid.x - mTrackedCentroid.x, 2) +
                        Math.pow(centroid.y - mTrackedCentroid.y, 2));

                if (distance < minDistance)
                {
                    minDistance = distance;
                    closestCentroid = centroid;
                }
            }

            mTrackedCentroid = closestCentroid;
            updateROI(mTrackedCentroid, mForegroundMask.width(), mForegroundMask.height());
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

    public static final String TAG = "edu.stanford.riedel-kruse.bioticgames.CameraActivity";
    public static final boolean DEBUG_MODE = true;
    public static final int NUM_DEBUG_VIEWS = 1;
    public static final int ROI_WIDTH = 100;
    public static final int ROI_HEIGHT = ROI_WIDTH;
    public static final int GOAL_HEIGHT = 400;
    public static final int GOAL_WIDTH = 50;

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

    private Point mGoal1TopLeft;
    private Point mGoal1BottomRight;

    private Point mGoal2TopLeft;
    private Point mGoal2BottomRight;

    private Rect mGoal1Rect;
    private Rect mGoal2Rect;

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
