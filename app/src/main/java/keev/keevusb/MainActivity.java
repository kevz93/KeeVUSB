package keev.keevusb;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.wch.wchusbdriver.CH34xAndroidDriver;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "keev.keev.usb";
    private static final String ACTION_USB_PERMISSION ="keev.keevusb.USB_PERMISSION";

    /*Thread to read the data*/
    public USBreadThread USBhandlerThread; // TODO: add method
    protected final Object ThreadLock = new Object();

    /*Declare UART interface variable */
    public CH34xAndroidDriver uartInterface;


    //Text stuff :
    EditText readText;
    EditText writeText;

    //Button :
    Button sendButton;
    // buffers :
    byte[] writeBuffer;
    char[] readBuffer;
    int actualNumBytes;

    int numBytes;
    byte count;
    int status;
    byte writeIndex = 0;
    byte readIndex = 0;

    int baudRate;       // baud rate
    byte baudRate_byte; // baud rate //send to hardware by AOA
    byte stopBit;       // 1:1stop bits, 2:2 stop bits
    byte dataBit;       // 8:8bit, 7: 7bit 6: 6bit 5: 5bit
    byte parity;        // 0: none, 1: odd, 2: even, 3: mark, 4: space
    byte flowControl;   // 0:none, 1: flow control(CTS,RTS)
    //byte timeout;     // time out

    public Context global_context;
    public boolean isConfigured = false;
    public boolean READ_ENABLE = false;
    public SharedPreferences sharePrefSettings;
    Drawable originalDrawable;
    public String act_string;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Editable text objects :
        readText = (EditText) findViewById(R.id.ReadValues);
        writeText = (EditText) findViewById(R.id.WriteValues);

        global_context = this;

        sendButton = (Button) findViewById(R.id.SendButton);

        // Making Pixie dust chest :
        writeBuffer = new byte[512];
        readBuffer =new char[512];

        // Setting up flask for Alchemy  :
        baudRate = 9600;
        stopBit = 1;
        dataBit = 8;
        parity = 0;
        flowControl = 0;



        boolean flags;

        if(isConfigured == false) {
            isConfigured = true;
            sendButton.setEnabled(true);
            if(uartInterface.isConnected()) {
                flags = uartInterface.UartInit();
                if(!flags) {
                    Log.d(TAG, "Init UART Error");
                    Toast.makeText(global_context, "Init UART Error", Toast.LENGTH_SHORT).show();
                } else {
                    if(uartInterface.SetConfig(baudRate, dataBit, stopBit, parity, flowControl)) {
                        Log.d(TAG, "Configured");
                    }
                }
            }


        }

        sendButton.setOnClickListener(new OnClickedSendButton()); // TODO : Make this Automatic
        sendButton.setEnabled(false);

        // New UART Interface :

        uartInterface = new CH34xAndroidDriver(
                (UsbManager)getSystemService(Context.USB_SERVICE), this, ACTION_USB_PERMISSION);

        //
        act_string = getIntent().getAction();

        if(-1 != act_string.indexOf("android.intent.action.MAIN"))
        {
            Log.d(TAG, "android.intent.action.MAIN");
        } else if(-1 != act_string.indexOf("android.hardware.usb.action.USB_DEVICE_ATTACHED"))
        {
            Log.d(TAG, "android.hardware.usb.action.USB_DEVICE_ATTACHED");
        }

        if(!uartInterface.UsbFeatureSupported())
        {
            Toast.makeText(this, "No Support USB host API", Toast.LENGTH_SHORT)
                    .show();
            readText.setText("No Support USB host API");
            uartInterface = null;
        }

        // for Keyboard visibility settings :
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

    if(READ_ENABLE == false){
        READ_ENABLE = true;
        USBhandlerThread = new USBreadThread(handler);
        USBhandlerThread.start();
    }


    }

// TODO : Make the sending, an automatic method instead of OnClick
    public class OnClickedSendButton implements View.OnClickListener{
        @Override
        public void onClick(View v){

            int count_int;
            int NumBytes = 0;
            int mLen = 0;

            // Writing from writetext in GUI to the buffer
            if(writeText.length() != 0){
                NumBytes = writeText.length();
                for(count_int = 0; count_int<NumBytes; count_int++){
                    writeBuffer[count_int] = (byte) writeText.getText().charAt(count_int);
                }
            }
            // Writing data :
            try{

                mLen = uartInterface.WriteData(writeBuffer, NumBytes);
            } catch(IOException e){
                Toast.makeText(global_context, "WriteData Error", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
            if(NumBytes != mLen) {
                Toast.makeText(global_context, "WriteData Error", Toast.LENGTH_SHORT).show();
            }
            Log.d(TAG, "WriteData Length is " + mLen);
        }
    }

    //----------------OnResume, STOP , DETROY and all stuff here :--------------------
    public void onHomePressed() {
        onBackPressed();
    }

    public void onBackPressed() {
        super.onBackPressed();
    }

    protected void onResume() {
        super.onResume();
        if(2 == uartInterface.ResumeUsbList())
        {
            uartInterface.CloseDevice();
            Log.d(TAG, "Enter onResume Error");
        }
    }
    //Stop reading USB on onSTOP()
    protected void onStop() {
        if(READ_ENABLE == true) {
            READ_ENABLE = false;
        }
        super.onStop();
    }

    protected void onDestroy() {
        if(uartInterface != null) {
            if(uartInterface.isConnected()) {
                uartInterface.CloseDevice();
            }
            uartInterface = null;
        }

        super.onDestroy();
    }

    protected void onPause() {
        super.onPause();
    }

    //--------------------------------------------------------------------------

    final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            if (actualNumBytes != 0x00) {
                readText.append(String.copyValueOf(readBuffer, 0,
                        actualNumBytes));
                actualNumBytes = 0;
            }

        }
    };

    // USB input data handler
    private class USBreadThread extends Thread{
       // Handler usbHandler;

        // Contructor :
        Handler usbhandler;
        USBreadThread(Handler h){
            usbhandler = h;
            this.setPriority(Thread.MIN_PRIORITY);
        }

        public void run(){
            while(READ_ENABLE){
                //init Message
                Message msg = usbhandler.obtainMessage();

                try{

                    Thread.sleep(50);

                }catch(InterruptedException e){
                    Log.d(TAG,"THREAAAADDDDD!!!!!");
                }

                synchronized (ThreadLock){
                    if(uartInterface != null) {
                        // Read data
                        actualNumBytes = uartInterface.ReadData(readBuffer, 64);

                        if(actualNumBytes>0)
                        {
                            usbhandler.sendMessage(msg);
                        }
                    }
                }
            }
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
}
