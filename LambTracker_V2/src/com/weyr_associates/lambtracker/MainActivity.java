package com.weyr_associates.lambtracker;

import java.text.DecimalFormat;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
// import android.content.BroadcastReceiver; commented out becasue not used also testing GitHub push
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;


public class MainActivity extends Activity {
	Button btnService;
	TextView textStat, textInfo1, textInfo2, textLog, textBytes;
	
	ScrollView svLog;
	private Boolean KeepScreenOn = false;
	DecimalFormat df = new DecimalFormat();
	ImageView mLogoImage;
	
	Messenger mService = null;
	boolean mIsBound;
	
	// added this to hold the statement I send with the intents
//	public final static String LASTEID = "com.weyr_associates.lambtracker.LASTEID";
	
	final Messenger mMessenger = new Messenger(new IncomingHandler());
	// variable to hold the string
	public String LastEID ;
	
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case eidService.MSG_UPDATE_STATUS:
				Bundle b1 = msg.getData();
				textStat.setText(b1.getString("stat"));
				textInfo1.setText(b1.getString("info1")); // info1 contains the Last EID tag read	
				textInfo2.setText(b1.getString("info2")); // contains the time the tag was read
				
				break;
			case eidService.MSG_NEW_EID_FOUND:
				Bundle b2 = msg.getData();
				textStat.setText(b2.getString("stat"));
				textInfo1.setText(b2.getString("info1")); // info1 contains the Last EID tag read	
				textInfo2.setText(b2.getString("info2")); // contains the time the tag was read
				LastEID = (b2.getString("info1"));
//				We have a good whole EID number so send it to the LookUpSheep component	
				lookUpSheep ();	
//		Test sending to doSheep instead
//				doSheepTasks (v);
				break;			
			case eidService.MSG_UPDATE_LOG_APPEND:
				Bundle b3 = msg.getData();
				LogMessage(b3.getString("logappend"));
				break;
			case eidService.MSG_UPDATE_LOG_FULL:
				Bundle b4 = msg.getData();
				textLog.setText(b4.getString("logfull"));
				svLog.post(new Runnable() { 
				    public void run() { 
				    	svLog.fullScroll(ScrollView.FOCUS_DOWN); 
				    } 
				});
				break;
			case eidService.MSG_THREAD_SUICIDE:
				Log.i("Activity", "Service informed Activity of Suicide.");
				doUnbindService();
				stopService(new Intent(MainActivity.this, eidService.class));
				LogMessage("Service Stopped");
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}
	
	public ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = new Messenger(service);
			try {
				//Register client with service
				Message msg = Message.obtain(null, eidService.MSG_REGISTER_CLIENT);
				msg.replyTo = mMessenger;
				mService.send(msg);

				//Request a status update.
				msg = Message.obtain(null, eidService.MSG_UPDATE_STATUS, 0, 0);
				mService.send(msg);
				
				//Request full log from service.
				msg = Message.obtain(null, eidService.MSG_UPDATE_LOG_FULL, 0, 0);
				mService.send(msg);
				
			} catch (RemoteException e) {
				// In this case the service has crashed before we could even do anything with it
			}
		}
		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been unexpectedly disconnected - process crashed.
			mService = null;
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name_long);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		btnService = (Button)findViewById(R.id.btnService);
		textStat = (TextView)findViewById(R.id.textStat);textStat.setText("Disconnected");
		textInfo1 = (TextView)findViewById(R.id.textInfo1);textInfo1.setText("");
		textInfo2 = (TextView)findViewById(R.id.textInfo2);textInfo2.setText("");
		textLog = (TextView)findViewById(R.id.textLog);
		svLog = (ScrollView)findViewById(R.id.svLog);
		textLog.setText(SetDefaultStatusText());
		textBytes = (TextView)findViewById(R.id.textBytes);
		mLogoImage = (ImageView)findViewById(R.id.logo_image);
		
		btnService.setOnClickListener(ListenerBtnService);
		textInfo1.setOnClickListener(ListenerToggleDisplayMsgType);
		textInfo2.setOnClickListener(ListenerToggleDisplayMsgType);
		
		restoreMe(savedInstanceState);
		Log.i("LambTracker", "At Restore Prefs.");
		CheckIfServiceIsRunning();
	}
	private String SetDefaultStatusText() {
		String t = "Contact: oogiem@desertweyr.com"; 
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			return "Version: " + packageInfo.versionName + "\n" + t;
		} catch (PackageManager.NameNotFoundException e) {
			return t;	
		}
	}
	@Override
	public void onResume() {
		super.onResume();
		
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		KeepScreenOn = preferences.getBoolean("keepscreenon", false);
		Log.i("Activity", "Resume" );
		
		if (mIsBound) { // Request a status update.
			if (mService != null) {
				Log.i("Activity", "Resume Bound" );
				try {
					//Request service reload preferences, in case those changed
					Message msg = Message.obtain(null, eidService.MSG_RELOAD_PREFERENCES, 0, 0);
					msg.replyTo = mMessenger;
					mService.send(msg);
				} catch (RemoteException e) {}
			}
		}
		
		if (KeepScreenOn) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		if (mService != null) {
		try {
			//Start eidService sending tags
			Message msg = Message.obtain(null, eidService.MSG_SEND_ME_TAGS);
			msg.replyTo = mMessenger;
			mService.send(msg);
			
		} catch (RemoteException e) {
			// In this case the service has crashed before we could even do anything with it
		}
		}
	}
	
	@Override
	public void onPause(){
		super.onPause();
		Log.i("Activity", "Paused" );
		if (mService != null) {
		try {
			//Stop tags eidService from sending tags
			Message msg = Message.obtain(null, eidService.MSG_NO_TAGS_PLEASE);
			msg.replyTo = mMessenger;
			mService.send(msg);
		} catch (RemoteException e) {
			// In this case the service has crashed before we could even do anything with it
		}
		}
	}
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("textxxx", textStat.getText().toString());
		outState.putString("textinfo1", textInfo1.getText().toString());
		outState.putString("textinfo2", textInfo2.getText().toString());
		outState.putString("connectbuttontext", btnService.getText().toString());
		outState.putString("textlog", textLog.getText().toString());
		outState.putString("textbytes", textBytes.getText().toString());
	}
	private void restoreMe(Bundle state) {
		if (state!=null) {
			textStat.setText(state.getString("textxxx"));
			textInfo1.setText(state.getString("textinfo1"));
			textInfo2.setText(state.getString("textinfo2"));
			btnService.setText(state.getString("connectbuttontext"));
			textLog.setText(state.getString("textlog"));
			textBytes.setText(state.getString("textbytes"));
			svLog.post(new Runnable() {
			    public void run() {
			    	svLog.fullScroll(ScrollView.FOCUS_DOWN);
			    }
			});
		}
	}

	private void CheckIfServiceIsRunning() {
		//If the service is running when the activity starts, we want to automatically bind to it.
		Log.i("LambTracker", "At isRunning.");
		if (eidService.isRunning()) {
			doBindService();
			mLogoImage.setVisibility(View.GONE);		} else {
			btnService.setText("Connect");
			if (textLog.length() > 60) { //More text here than the default start-up amount
				mLogoImage.setVisibility(View.GONE);
			}
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//startActivity(new Intent(this, EditPreferences.class));
		super.onCreateOptionsMenu(menu);
		menu.add(0, 0, 0, "Settings").setIcon(R.drawable.settings).setAlphabeticShortcut('s');
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		//startActivity(new Intent(this, EditPreferences.class));
		super.onCreateOptionsMenu(menu);		
		menu.removeItem(1);
		return true;
	}
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch(item.getItemId()) {
		case 0: //Settings
			startActivity(new Intent(this, EditPreferences.class));
			return true;
//		case 9: //Record Note Here
//			AskUserAboutNote();
//			return true;
		}
		return super.onMenuItemSelected(featureId, item);
	}
	
	private OnClickListener ListenerBtnService = new OnClickListener() {
		public void onClick(View v){
			mLogoImage.setVisibility(View.GONE);

			if(btnService.getText() == "Connect"){
				LogMessage("Starting Service");
				startService(new Intent(MainActivity.this, eidService.class));
				doBindService();
//				if (KeepScreenOn) {
//					getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//				}
			} else {
				Log.i("Activity", "Disconnect clicked");
				doUnbindService();
				stopService(new Intent(MainActivity.this, eidService.class));
				LogMessage("Service Stopped");
//				if (KeepScreenOn) {
//					getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//				}
			}
		}
	};
	private OnClickListener ListenerToggleDisplayMsgType = new OnClickListener() {
		public void onClick(View v){
			if(btnService.getText() != "Connect"){
				if (mService != null) {
					try {
						//Request change of display message type
						Message msg = Message.obtain(null, eidService.MSG_TOGGLE_LOG_TYPE, 0, 0);
						msg.replyTo = mMessenger;
						mService.send(msg);
					} catch (RemoteException e) {}
				}
			}
		}
	};

	private void LogMessage(String m) {
		//Check if log is too long, shorten if necessary.
		if (textLog.getText().toString().length() > 4000) {
			String templog = textLog.getText().toString();
			int tempi = templog.length();
			tempi = templog.indexOf("\n", tempi-1000);
			textLog.setText(templog.substring(tempi+1));
		}
		
		textLog.append("\n" + m);
		svLog.post(new Runnable() { 
		    public void run() { 
		    	svLog.fullScroll(ScrollView.FOCUS_DOWN); 
		    } 
		}); 
	}

	void doBindService() {
		// Establish a connection with the service.  We use an explicit
		// class name because there is no reason to be able to let other
		// applications replace our component.
		bindService(new Intent(this, eidService.class), mConnection, Context.BIND_AUTO_CREATE);
		textStat.setText("Connecting...");
		btnService.setText("Disconnect");
		mIsBound = true;
		if (mService != null) {
			try {
				//Request status update
				Message msg = Message.obtain(null, eidService.MSG_UPDATE_STATUS, 0, 0);
				msg.replyTo = mMessenger;
				mService.send(msg);

				//Request full log from service.
				msg = Message.obtain(null, eidService.MSG_UPDATE_LOG_FULL, 0, 0);
				mService.send(msg);
			} catch (RemoteException e) {}
		}
	}
	void doUnbindService() {
		Log.i("Activity", "At DoUnbindservice");
		if (mService != null) {
		try {
			//Stop tags eidService from sending tags
			Message msg = Message.obtain(null, eidService.MSG_NO_TAGS_PLEASE);
			msg.replyTo = mMessenger;
			mService.send(msg);
			
		} catch (RemoteException e) {
			// In this case the service has crashed before we could even do anything with it
		}
		}
		if (mIsBound) {
			// If we have received the service, and hence registered with it, then now is the time to unregister.
			if (mService != null) {
				try {
					Message msg = Message.obtain(null, eidService.MSG_UNREGISTER_CLIENT);
					msg.replyTo = mMessenger;
					mService.send(msg);
//					Log.i("Activity", "At Unregister");
				} catch (RemoteException e) {
//					Log.i("Activity", "At Exception");
					// There is nothing special we need to do if the service has crashed.
				}
			}
			// Detach our existing connection.
			unbindService(mConnection);
//			Log.i("Activity", "At Detaching");
			mIsBound = false;
		}
		textStat.setText("Disconnected");
		textInfo1.setText("");
		textInfo2.setText("");
		textBytes.setText("");
		btnService.setText("Connect");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (KeepScreenOn) {
			getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		
		try {
			Log.i("Activity", "onDestroy");
			doUnbindService();
		} catch (Throwable t) {
			Log.e("MainActivity", "Failed to unbind from the service", t);
		}
	
	}
	// load the sheep list
	public void loadSheepList( View v )
	    {
		Intent listSheep = new Intent( this, LoadSheepList.class );
		startActivity(listSheep);
	    }

	// set up a sheep task
	public void doSheepTasks( View v )
	    {
		Intent workSheep = new Intent( this, DoSheepTask.class );
		workSheep.putExtra("com.weyr_associates.lambtracker.LASTEID", LastEID);
		startActivity(workSheep);
		}

	public void lookUpSheep( )
    {
	Intent lookSheep = new Intent( this, LookUpSheep.class );
	lookSheep.putExtra("com.weyr_associates.lambtracker.LASTEID", LastEID);
	startActivity(lookSheep);
	}

	// edit the database
	public void editDB( View v )
		{
		Intent edit_db = new Intent( this, EditDB.class );
		startActivity( edit_db );
		}
	    
 // quit the app
	public void quitApp( View v )
	{
		Log.i("Activity", "Quit clicked");
		doUnbindService();
		stopService(new Intent(MainActivity.this, eidService.class));
		LogMessage("Service Stopped");
		finish();
		this.moveTaskToBack( true );
	}
		    	   		
	}




	 