# Link to LudusScope paper
http://journals.plos.org/plosone/article?id=info%3Adoi/10.1371/journal.pone.0162602

# Link to STL Files
https://www.thingiverse.com/thing:1721580

# Biotic Soccer Game for Android
This repository includes sample code that utilizes the Biotic Games SDK to create a simple Soccer Game.

## Set up
### Android Studio
1. From the Android Studio options menu, select VCS > Checkout from Version Control > GitHub.
2. In the prompt box, enter https://github.com/riedel-kruse-lab/biotic_games_android.git as the "Vcs Repository URL." Change "Parent Directory" and "Directory Name" to place the project where you would like it on your computer.
3. Once the project is cloned from GitHub, Android Studio will ask if you would like to open `build.gradle` from the project directory. Click "Yes."
4. In the prompt box, make sure "Use default gradle wrapper (recommended) is selected. Clik "OK."
5. Running the Build > Make Project right now will yield an error that syas "NDK not configured." To resolve this, open `local.properties` in the root directory of the project and add a line that says `ndk.dir=[PATH TO YOUR NDK DIRECTORY]`.
6. Click the Run button and select either an emulator or an attached device.
