package edu.stanford.riedel_kruse.bioticgames;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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

    public void startGame(View view)
    {
        finish();
        Intent intent = new Intent(this, GameActivity.class);
        startActivity(intent);
    }

    public void startTutorial(View view)
    {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_TUTORIAL_MODE, true);
        startActivity(intent);
    }

    public void onInstructionsPressed(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Instructions");
        builder.setMessage("Try to score the ball into the other player's goal to win! " +
                        "You get 2 points for carrying the ball into the goal, and 1 point for passing it in.\n\n" +
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


    public void infoButtonPressed(View v) {
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

    public void creditsButtonPressed(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Credits");
        builder.setMessage("Honesty Kim: Messing Things Up\nDaniel Chiu: Programming\n" +
                        "Seung Ah Lee: Optics\nAlice Chung: Euglena Biology\nSherwin Xia: Electronics\n" +
                        "Lukas Gerber: Sticker Microfluidics\nNate Cira: Microfluidics\n" +
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
