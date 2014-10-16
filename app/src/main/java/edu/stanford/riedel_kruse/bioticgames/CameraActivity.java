package edu.stanford.riedel_kruse.bioticgames;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
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
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractorMOG;

import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends Activity { //implements CvCameraViewListener2 {
    public static String TAG = "edu.stanford.riedel-kruse.bioticgames.CameraActivity";

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch(status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeResource(getResources(), R.drawable.euglena, options);

                    options.inSampleSize = calculateInSampleSize(options, 100, 100);

                    options.inJustDecodeBounds = false;
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

    //private CameraBridgeViewBase mOpenCvCameraView;

    private Camera mCamera;
    private ImageView mContoursImage;
    private ImageView mGrayscaleImage;
    private ImageView mOriginalImage;
    private CameraPreview mPreview;
    private ImageView mHSVThresholdedImage;

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight)
    {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth)
        {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > reqHeight &&
                    (halfWidth / inSampleSize) > reqWidth)
            {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Camera getCameraInstance()
    {
        Camera c = null;
        try
        {
            c = Camera.open();
        }
        catch (Exception e)
        {

        }

        return c;
    }

    /** Activity lifecycle callbacks */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mCamera = getCameraInstance();
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setZoom(parameters.getMaxZoom());
        mCamera.setParameters(parameters);

        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);

        /*mContoursImage = (ImageView) findViewById(R.id.contours_image);
        mGrayscaleImage = (ImageView) findViewById(R.id.grayscale_image);
        mHSVThresholdedImage = (ImageView) findViewById(R.id.hsv_thresholded_image);
        mOriginalImage = (ImageView) findViewById(R.id.original_image);*/

        /*mOpenCvCameraView = (CameraBridgeViewBase)findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);*/
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        /*if (mOpenCvCameraView != null)
        {
            mOpenCvCameraView.disableView();
        }*/
    }

    @Override
    public void onPause()
    {
        super.onPause();
        /*if (mOpenCvCameraView != null)
        {
            mOpenCvCameraView.disableView();
        }*/
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, mLoaderCallback);
    }

    /** End activity lifecycle callbacks */

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

    public void setCameraBitmap(Bitmap bitmap)
    {
        Log.d(TAG, "setCameraBitmap called.");
        ((ImageView) findViewById(R.id.camera_bitmap)).setImageBitmap(processImage(bitmap));
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

    /** CvCameraViewListener2 Interface */

    /*public void onCameraViewStarted(int width, int height) {}

    public void onCameraViewStopped() {}

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mBackgroundSubtractor.apply(inputFrame.gray(), mForegroundMask);
        //return inputFrame.gray();
        return mForegroundMask;
    }*/

    /** End CvCameraViewListener2 */
}
