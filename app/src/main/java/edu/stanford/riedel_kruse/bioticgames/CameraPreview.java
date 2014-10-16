package edu.stanford.riedel_kruse.bioticgames;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by dchiu on 10/8/14.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback,
        Camera.PreviewCallback
{
    public static String TAG = "edu.stanford.riedel_kruse.bioticgames.CameraPreview";

    private SurfaceHolder mHolder;
    private Camera mCamera;
    private CameraActivity mActivity;

    public CameraPreview(CameraActivity context, Camera camera)
    {
        super(context);
        mActivity = context;
        mCamera = camera;

        // Set SurfaceHolder.Callback so we are notificed when underlying surface is created and
        // destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder)
    {
        try
        {
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();
        }
        catch (IOException e)
        {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height)
    {
        if (mHolder.getSurface() == null)
        {
            // Preview surface does not exist.
            return;
        }

        // Stop preview before making changes.
        try
        {
            mCamera.stopPreview();
        }
        catch (Exception e)
        {

        }

        try
        {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error starting camera preview", e);
        }
        /*Camera.Parameters parameters = mCamera.getParameters();

        // Sets the size of the camera preview.
        parameters.setPreviewSize(mPreviewWidth, mPreviewHeight);
        // Sets the size of the pictures taken.
        parameters.setPictureSize(mPreviewWidth, mPreviewHeight);

        mCamera.setParameters(parameters);
        mCamera.startPreview();*/
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder)
    {
        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera)
    {
        Camera.Parameters parameters = camera.getParameters();

        int imageFormat = parameters.getPreviewFormat();

        int width = parameters.getPreviewSize().width;
        int height = parameters.getPreviewSize().height;

        YuvImage yuv = new YuvImage(bytes, imageFormat, width, height, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);

        Bitmap bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
        mActivity.setCameraBitmap(bitmap);
    }
}
