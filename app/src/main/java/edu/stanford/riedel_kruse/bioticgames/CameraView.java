package edu.stanford.riedel_kruse.bioticgames;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;

import org.opencv.android.JavaCameraView;
import org.opencv.highgui.VideoCapture;

/**
 * Created by dchiu on 10/16/14.
 */
public class CameraView extends JavaCameraView
{
    public CameraView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        //Camera.Parameters params = mCamera.getParameters();
        //params.setZoom(params.getMaxZoom());
    }

    @Override
    public boolean connectCamera(int width, int height)
    {
        boolean retValue = super.connectCamera(width, height);

        Camera.Parameters params = mCamera.getParameters();
        params.setZoom(params.getMaxZoom());
        mCamera.setParameters(params);

        return retValue;
    }
}
