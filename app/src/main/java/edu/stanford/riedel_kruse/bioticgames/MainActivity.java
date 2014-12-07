package edu.stanford.riedel_kruse.bioticgames;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

/**
 * Created by dchiu on 12/7/14.
 */
public class MainActivity extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void playGame(View view)
    {
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
    }
}
