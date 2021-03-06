package com.Group17;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GameActivity extends AppCompatActivity {

    private GameView gameView;
    private BluetoothService myService;
    private boolean isServiceBound;
    private ServiceConnection serviceConnection;
    private  Intent serviceIntent;
    private JSONArray song;
    private Point point;
    private GameActivity context;
    private String songName;
    private boolean repeatingGame;
    private int tempoBPM=140;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        songName = intent.getStringExtra("songName");
        int partKey = intent.getIntExtra("partKey",-1); //make sure the intent passes this in based on user input
        this.repeatingGame = intent.getBooleanExtra("repeatingSong", false);
        tempoBPM = intent.getIntExtra("tempo", 140);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        point = new Point();
        getWindowManager().getDefaultDisplay().getSize(point);
        song = null;
        context = this;

        if (songName.equals("tempo_game_reserved_name")){
            RandomTempoGenerator myRTG = new RandomTempoGenerator(50, intent.getDoubleExtra("density",0.5)); //no reason to get length from user
            song=myRTG.randomSong;
        }
        else{
            try {
                String jtxt = loadJSONFromAsset(this);
                JSONObject json = new JSONObject(jtxt);
                JSONObject songObject = json.getJSONObject(songName);

                JSONObject jSettings = MemoryInterface.readFile("userSettings.txt");
                String tempoOverrideString = jSettings.getString("Tempo Override");
                if ("off".equals(tempoOverrideString)){
                    if (songObject.has("bpm")){
                        tempoBPM = songObject.getInt("bpm");
                    }
                }
                else{
                    tempoBPM = Integer.parseInt(tempoOverrideString);
                }

                if (partKey == -1) {
                    song = songObject.getJSONArray("song");
                }
                else{
                    JSONArray tempSong = songObject.getJSONArray("song");
                    int a= (int)((songObject.getJSONArray("parts")).getJSONArray(partKey)).get(0);
                    int b= (int)((songObject.getJSONArray("parts")).getJSONArray(partKey)).get(1);
                    song = new JSONArray();
                    for (int i=a;i<=b;i++){
                        song.put(tempSong.getJSONArray(i));
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        serviceIntent=new Intent(getApplicationContext(),BluetoothService.class);
        bindService();

    }



    @Override
    protected void onPause() {
        super.onPause();
        gameView.pause();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        unbindService();
        Log.d("GameActivity", "GA on destroy");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(gameView != null) {
            gameView.resume();
        }
    }

    public String loadJSONFromAsset(Context context) {
        String json = null;
        try {
            InputStream is = context.getAssets().open("songs.json");

            int size = is.available();

            byte[] buffer = new byte[size];

            is.read(buffer);

            is.close();

            json = new String(buffer, "UTF-8");

        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;

    }

    private void bindService(){
        if(serviceConnection==null){
            serviceConnection=new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    BluetoothService.BluetoothServiceBinder myServiceBinder=(BluetoothService.BluetoothServiceBinder)iBinder;
                    myService=myServiceBinder.getService();
                    isServiceBound=true;
                    play();
                }
                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    isServiceBound=false;
                }
            };
        }
        bindService(serviceIntent,serviceConnection,Context.BIND_AUTO_CREATE);
    }

    private void unbindService(){
        if(isServiceBound){
            unbindService(serviceConnection);
            isServiceBound=false;
        }
    }

    public void play()
    {
        if(myService  != null && myService.isRunning())
        {
            Log.d("ACTIVITY SetBluetooth", "Not NULL");
            if(song != null)
            {
                gameView = new GameView(this, point.x, point.y, song, myService, songName, repeatingGame, tempoBPM);
                setContentView(gameView);
                //gameView.beginUnthreadedGame();
                gameView.beginGame();

            }
        }
        else
        {
            Log.d("ACTIVITY SetBluetooth", "NULL");
        }
    }

    @Override //throw this in the game as well
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    MemoryInterface.checkWritePermission(this);
                } else {
                    Toast.makeText(getApplicationContext(),"Unable to get permissions: READ",Toast.LENGTH_SHORT).show();
                    this.finish();
                }
                return;
            }
            case 2: {

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    gameView.setPermissions();
                } else {
                    Toast.makeText(getApplicationContext(),"Unable to get permissions: WRITE",Toast.LENGTH_SHORT).show();
                    this.finish();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }


}
