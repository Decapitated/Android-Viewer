package dev.decapitated.android_client;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.socket.client.IO;
import io.socket.client.Socket;

public class MainActivity extends AppCompatActivity {

    private Button connect;
    private Button sendImage;
    private EditText input;
    private TextView feed;

    private InputMethodManager inputManager;

    private MediaProjectionManager mediaManager;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private Display display;
    private Point displaySize = new Point();
    private DisplayMetrics displayMetrics = new DisplayMetrics();

    private Socket socket;
    private boolean connected = false;
    private boolean sharing = false;


    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> cappedHandle;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        display = getWindowManager().getDefaultDisplay();
        display.getSize(displaySize);
        display.getMetrics(displayMetrics);
        imageReader = ImageReader.newInstance(displaySize.x, displaySize.y, PixelFormat.RGBA_8888, 2);
        //Get permission for media projection
        mediaManager = (MediaProjectionManager)getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE);
        Intent recordPerm = mediaManager.createScreenCaptureIntent();
        startActivityForResult(recordPerm, 1000);

        //Getting View Elements//
        connect = findViewById(R.id.connect);
        sendImage = findViewById(R.id.send_image);
        input = findViewById(R.id.input);
        inputManager = (InputMethodManager) this.getSystemService(this.INPUT_METHOD_SERVICE);
        feed = findViewById(R.id.feed);
        //--------------------//

        //Triggered when 'Connect' button is clicked
        connect.setOnClickListener((event) -> {
            Thread temp = new Thread(()->{
                try {
                    feed.append("Connecting...\n");
                    socket = IO.socket("http://127.0.0.1:5555");

                    socket.on(Socket.EVENT_CONNECT, args -> {
                        feed.append("Connected to Server.\n");
                        setElementVisibility(connect, false);
                        setElementVisibility(sendImage, true);
                    });
                    socket.on(Socket.EVENT_DISCONNECT, args -> {
                        feed.append("Server Closed.\n");
                        socket.disconnect();
                        connected = false;
                        sharing = false;
                        setElementVisibility(connect,true);
                        setElementVisibility(sendImage, false);
                    });
                    socket.on("message", args -> feed.append(args[0]+": "+args[1]+"\n"));
                    socket.connect();
                } catch (URISyntaxException e) {
                    feed.append("Could not connect to Server.\n");
                }
            });
            temp.start();
        });

        sendImage.setOnClickListener((event) -> {
            sharing = !sharing;
            if(sharing) {
                sendThread.start();
            }
        });



        input.setOnEditorActionListener((v, actionId, event) -> {
            if(actionId == EditorInfo.IME_ACTION_SEND){
                feed.append("Sending Message...\n");
                String temp = v.getText().toString(); //Get user input
                socket.emit("message", "Android", temp); //Send message
                feed.append(temp+"\n"); //Add message to the feed

                //Closes the keyboard//
                inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
                input.getText().clear();
                v.clearFocus();
                //------------------//
            }
            return true;
        });
    }

    Thread sendThread = new Thread(() -> {
        cappedHandle = scheduler.scheduleAtFixedRate(() -> {
            if(sharing) {
                Image image = imageReader.acquireLatestImage();

                if (image != null) {
                    final Image.Plane[] planes = image.getPlanes();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * image.getWidth();

                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(), Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    byte[] byteArray;
                    if (bitmap != null) {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
                        byteArray = byteArrayOutputStream.toByteArray();
                        String encoded = Base64.encodeToString(byteArray, Base64.DEFAULT);
                        socket.emit("frame", encoded);
                    }
                    image.close();
                }
            }else{
                this.cappedHandle.cancel(true);
            }
        },0,1000/60, TimeUnit.MILLISECONDS);
    });

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK){
            mediaProjection = mediaManager.getMediaProjection(resultCode, data);
            virtualDisplay = mediaProjection.createVirtualDisplay("screen", displaySize.x, displaySize.y, displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);
        }else{
            System.exit(-1);
        }
    }

    private void setElementVisibility(View element, boolean visible){
        MainActivity.this.runOnUiThread(()->{
            if(visible){
                element.setVisibility(View.VISIBLE);
            }else{
                element.setVisibility(View.INVISIBLE);
            }
        });
    }
}
