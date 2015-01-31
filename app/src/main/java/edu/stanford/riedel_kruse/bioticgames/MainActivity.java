package edu.stanford.riedel_kruse.bioticgames;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

/**
 * Main activity for the application. Displays the menu screen and launches other activities.
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    /**
     * Starts the GameActivity. Activated by button press (see activity_main.xml).
     *
     * @param view
     */
    public void startGame(View view) {
        Intent intent = new Intent(this, GameActivity.class);
        startActivity(intent);
    }

    /**
     * Starts the GameActivity in tutorial mode. Activated by button press (see activity_main.xml)
     *
     * @param view
     */
    public void startTutorial(View view) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_TUTORIAL_MODE, true);
        startActivity(intent);
    }
}
