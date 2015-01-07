package edu.stanford.riedel_kruse.bioticgames;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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

    public void startGame(View view)
    {
        Intent intent = new Intent(this, GameActivity.class);
        startActivity(intent);
    }

    public void startTutorial(View view)
    {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_TUTORIAL_MODE, true);
        startActivity(intent);
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



}
