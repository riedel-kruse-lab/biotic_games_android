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

import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2
{
    /** Activity lifecycle callbacks */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mDebugImageView = (ImageView) findViewById(R.id.debug_view);
        if (DEBUG_MODE)
        {
            mDebugImageView.setVisibility(View.VISIBLE);
        }

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mCentroids = new ArrayList<Point>();
        mContours = new ArrayList<MatOfPoint>();
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

            Handler mainHandler = new Handler(getMainLooper());
            mainHandler.post(new Runnable()
            {
                public void run()
                {
                    mDebugImageView.setImageBitmap(mDebugBitmap);
                }
            });
        }
    }

    private Mat processFrame(Mat frameGray, Mat frameRgba)
    {
        // Update the background subtraction model
        mBackgroundSubtractor.apply(frameGray, mForegroundMask);

        debugShowMat(mForegroundMask);

        findContours();
        if (DEBUG_MODE)
        {
            Imgproc.drawContours(frameRgba, mContours, -1, new Scalar(255, 0, 0), 1);
        }

        findContourCentroids();
        if (DEBUG_MODE)
        {
            for (Point centroid : mCentroids)
            {
                Core.circle(frameRgba, centroid, 4, new Scalar(0, 255, 0));
            }
        }

        return frameRgba;
    }

    private void findContours()
    {
        mContours.clear();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mForegroundMask, mContours, hierarchy, Imgproc.RETR_TREE,
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

    private Mat processForeground()
    {

        return mForegroundMask;
    }

    private Bitmap processImage(Bitmap bitmap)
    {
        Mat originalMat = new Mat();

        Utils.bitmapToMat(bitmap, originalMat);

        Point p1 = new Point(200, 200);
        Point p2 = new Point(500, 500);
        Rect roi = new Rect(p1, p2);
        Mat roiMat = new Mat(originalMat, roi);

        Mat hsvMat = new Mat();
        Imgproc.cvtColor(roiMat, hsvMat, Imgproc.COLOR_BGR2HSV);

        Mat hsvThresholdedMat = new Mat();
        Core.inRange(hsvMat, new Scalar(50, 15, 0), new Scalar(96, 175, 255), hsvThresholdedMat);

        Imgproc.erode(hsvThresholdedMat, hsvThresholdedMat, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3)));
        Imgproc.dilate(hsvThresholdedMat, hsvThresholdedMat, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3)));

        Imgproc.dilate(hsvThresholdedMat, hsvThresholdedMat, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(2, 2)));
        //Imgproc.erode(hsvThresholdedMat, hsvThresholdedMat, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(2, 2)));

        Bitmap hsvThresholdedBitmap = Bitmap.createBitmap(
                hsvThresholdedMat.cols(), hsvThresholdedMat.rows(),
                Bitmap.Config.ARGB_8888);

        Utils.matToBitmap(hsvThresholdedMat, hsvThresholdedBitmap);

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(hsvThresholdedMat, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        Imgproc.drawContours(roiMat, contours, -1, new Scalar(255, 0, 0), 1);

        roiMat.copyTo(originalMat.submat(roi));

        Core.circle(originalMat, new Point(100, 100), 99, new Scalar(255, 0, 0), -1);
        Core.rectangle(originalMat, p1, p2, new Scalar(0, 255, 0));

        Bitmap contoursBitmap = Bitmap.createBitmap(
                originalMat.cols(), originalMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(originalMat, contoursBitmap);

        return contoursBitmap;
    }

    public static final String TAG = "edu.stanford.riedel-kruse.bioticgames.CameraActivity";
    public static final boolean DEBUG_MODE = true;

    private ImageView mDebugImageView;
    private Bitmap mDebugBitmap;
    private CameraBridgeViewBase mOpenCvCameraView;
    private BackgroundSubtractorMOG mBackgroundSubtractor;
    private Mat mForegroundMask;
    private List<Point> mCentroids;
    private List<MatOfPoint> mContours;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch(status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    mBackgroundSubtractor = new BackgroundSubtractorMOG();
                    mForegroundMask = new Mat();
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
