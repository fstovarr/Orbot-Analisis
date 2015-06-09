/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - https://guardianproject.info */
/* See LICENSE for licensing information */

package org.torproject.android;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.Locale;

import org.torproject.android.service.TorService;
import org.torproject.android.service.TorServiceConstants;
import org.torproject.android.service.TorServiceUtils;
import org.torproject.android.settings.SettingsPreferences;
import org.torproject.android.ui.ImageProgressView;
import org.torproject.android.ui.PromoAppsActivity;
import org.torproject.android.ui.Rotate3dAnimation;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.animation.AccelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;


public class OrbotMainActivity extends Activity implements OrbotConstants, OnLongClickListener, OnTouchListener, OnSharedPreferenceChangeListener
{
    /* Useful UI bits */
    private TextView lblStatus = null; //the main text display widget
    private ImageProgressView imgStatus = null; //the main touchable image for activating Orbot

    private MenuItem mItemOnOff = null;
    private TextView downloadText = null;
    private TextView uploadText = null;
    private TextView mTxtOrbotLog = null;
    
    private Button mBtnBrowser = null;
    private ToggleButton mBtnVPN = null;
    private ToggleButton mBtnBridges = null;
    
    
	private DrawerLayout mDrawer;
	private ActionBarDrawerToggle mDrawerToggle;
	private Toolbar mToolbar;
		
    /* Some tracking bits */
    private String torStatus = TorServiceConstants.STATUS_OFF; //latest status reported from the tor service
    
    private SharedPreferences mPrefs = null;

    private boolean autoStartFromIntent = false;
    
    private final static int REQUEST_VPN = 8888;
    private final static int REQUEST_SETTINGS = 0x9874;
    
    /** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = TorServiceUtils.getSharedPrefs(getApplicationContext());        
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        
    	doLayout();

    	/* receive the internal status broadcasts, which are separate from the public
    	 * status broadcasts to prevent other apps from sending fake/wrong status
    	 * info to this app */
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(mLocalBroadcastReceiver,
                new IntentFilter(TorServiceConstants.ACTION_STATUS));
        lbm.registerReceiver(mLocalBroadcastReceiver,
                new IntentFilter(TorServiceConstants.LOCAL_ACTION_BANDWIDTH));
        lbm.registerReceiver(mLocalBroadcastReceiver,
                new IntentFilter(TorServiceConstants.LOCAL_ACTION_LOG));
	}
	
	

	private void sendIntentToService(String action) {
		Intent torService = new Intent(this, TorService.class);    
		torService.setAction(action);
		startService(torService);
	}

    private void stopTor() {
        Intent torService = new Intent(this, TorService.class);
        stopService(torService);
    }

    /**
     * The state and log info from {@link TorService} are sent to the UI here in
     * the form of a local broadcast. Regular broadcasts can be sent by any app,
     * so local ones are used here so other apps cannot interfere with Orbot's
     * operation.
     */
    private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            if (action.equals(TorServiceConstants.LOCAL_ACTION_LOG)) {
                String log = intent.getStringExtra(TorServiceConstants.LOCAL_EXTRA_LOG);
                updateStatus(log);

            } else if (action.equals(TorServiceConstants.LOCAL_ACTION_BANDWIDTH)) {
                long upload = intent.getLongExtra("up", 0);
                long download = intent.getLongExtra("down", 0);
                long written = intent.getLongExtra("written", 0);
                long read = intent.getLongExtra("read", 0);

                Message msg = mStatusUpdateHandler
                        .obtainMessage(TorServiceConstants.MESSAGE_TRAFFIC_COUNT);
                msg.getData().putLong("download", download);
                msg.getData().putLong("upload", upload);
                msg.getData().putLong("readTotal", read);
                msg.getData().putLong("writeTotal", written);
                mStatusUpdateHandler.sendMessage(msg);

            } else if (action.equals(TorServiceConstants.ACTION_STATUS)) {
                torStatus = intent.getStringExtra(TorServiceConstants.EXTRA_STATUS);
                updateStatus("");
            }
        }
    };
 
    private void doLayout ()
    {
        setContentView(R.layout.layout_main);
        
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbar.inflateMenu(R.menu.orbot_main);
        mToolbar.setTitle(R.string.app_name);

        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
          mDrawerToggle = new ActionBarDrawerToggle(
              this,  mDrawer, mToolbar,
              android.R.string.ok, android.R.string.cancel
          );
          
          mDrawer.setDrawerListener(mDrawerToggle);
          mDrawerToggle.setDrawerIndicatorEnabled(true);
          mDrawerToggle.syncState();
          mDrawerToggle.setToolbarNavigationClickListener(new OnClickListener ()
          {

              @Override
              public void onClick(View v) {
              }


          });

        setupMenu();
        
        mTxtOrbotLog = (TextView)findViewById(R.id.orbotLog);
        
        lblStatus = (TextView)findViewById(R.id.lblStatus);
        lblStatus.setOnLongClickListener(this);
        imgStatus = (ImageProgressView)findViewById(R.id.imgStatus);
        imgStatus.setOnLongClickListener(this);
        imgStatus.setOnTouchListener(this);
        
        downloadText = (TextView)findViewById(R.id.trafficDown);
        uploadText = (TextView)findViewById(R.id.trafficUp);
        
        
        downloadText.setText(formatCount(0) + " / " + formatTotal(0));
        uploadText.setText(formatCount(0) + " / " + formatTotal(0));
    
        // Gesture detection
		mGestureDetector = new GestureDetector(this, new MyGestureDetector());
		
		mBtnBrowser = (Button)findViewById(R.id.btnBrowser);
		mBtnBrowser.setOnClickListener(new View.OnClickListener ()
		{

			@Override
			public void onClick(View v) {
				doTorCheck();
				
			}

		});
		
		mBtnBrowser.setEnabled(false);
		
		mBtnVPN = (ToggleButton)findViewById(R.id.btnVPN);
		
		boolean useVPN = Prefs.useVpn();
		mBtnVPN.setChecked(useVPN);
		
		if (useVPN)
		{
			startVpnService ();
		}
		
		mBtnVPN.setOnClickListener(new View.OnClickListener ()
		{

			@Override
			public void onClick(View v) {

				if (mBtnVPN.isChecked())
					promptStartVpnService();
				else
					stopVpnService();
				
			}

		});
		
		
		mBtnBridges = (ToggleButton)findViewById(R.id.btnBridges);
		mBtnBridges.setChecked(Prefs.bridgesEnabled());
		mBtnBridges.setOnClickListener(new View.OnClickListener ()
		{

			@Override
			public void onClick(View v) {
				if (Build.CPU_ABI.contains("arm"))
				{       
					promptSetupBridges (); //if ARM processor, show all bridge options
				
				}
				else
				{
					Toast.makeText(OrbotMainActivity.this, R.string.note_only_standard_tor_bridges_work_on_intel_x86_atom_devices, Toast.LENGTH_LONG).show();
					showGetBridgePrompt(""); //if other chip ar, only stock bridges are supported
				}
			}

			
		});
		
		

    }
    
    GestureDetector mGestureDetector;
    

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return mGestureDetector.onTouchEvent(event);
	}
   	
    
   /*
    * Create the UI Options Menu (non-Javadoc)
    * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
    */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.orbot_main, menu);
       
        mItemOnOff = menu.getItem(0);
        
        return true;
    }
    
    /**
    private void appConflictChecker ()
    {
    	SharedPreferences sprefs = TorServiceUtils.getSharedPrefs(getApplicationContext());
    	
    	boolean showAppConflict = true;//sprefs.getBoolean("pref_show_conflict",true);
    	
    	String[] badApps = {"com.sec.msc.nts.android.proxy:com.sec.msc.nts.android.proxy","com.sec.pcw:Samsung Link"};
    	
    	for (String badApp : badApps)
    	{
    		String[] badAppParts = badApp.split(":");
    		
    		if (appInstalledOrNot(badAppParts[0]))
    		{
    			String msg = getString(R.string.please_disable_this_app_in_android_settings_apps_if_you_are_having_problems_with_orbot_) + badAppParts[1];
    			
    			if (showAppConflict)
    				showAlert(getString(R.string.app_conflict),msg,true);
	    	
	    	//	appendLogTextAndScroll(msg);
    		}
    	}
    	
    	sprefs.edit().putBoolean("pref_show_conflict", false).commit();
	
    }*/
    
    private void showAbout ()
        {
                
            LayoutInflater li = LayoutInflater.from(this);
            View view = li.inflate(R.layout.layout_about, null); 
            
            String version = "";
            
            try {
                version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName + " (Tor " + TorServiceConstants.BINARY_TOR_VERSION + ")";
            } catch (NameNotFoundException e) {
                version = "Version Not Found";
            }
            
            TextView versionName = (TextView)view.findViewById(R.id.versionName);
            versionName.setText(version);    
            
                    new AlertDialog.Builder(this)
            .setTitle(getString(R.string.button_about))
            .setView(view)
            .show();
        }
    
    private void setupMenu ()
    {

        mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener ()
        {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                
                if (item.getItemId() == R.id.menu_settings)
                {
                    Intent intent = new Intent(OrbotMainActivity.this, SettingsPreferences.class);
                    startActivityForResult(intent, REQUEST_SETTINGS);
                }
                else if (item.getItemId() == R.id.menu_promo_apps)
                {
                    startActivity(new Intent(OrbotMainActivity.this, PromoAppsActivity.class));

                }
                else if (item.getItemId() == R.id.menu_exit)
                {
                        //exit app
                        doExit();
                        
                        
                }
                else if (item.getItemId() == R.id.menu_about)
                {
                        showAbout();
                        
                        
                }
                else if (item.getItemId() == R.id.menu_scan)
                {
                	IntentIntegrator integrator = new IntentIntegrator(OrbotMainActivity.this);
                	integrator.initiateScan();
                }
                else if (item.getItemId() == R.id.menu_share_bridge)
                {
                	
            		String bridges = Prefs.getBridgesList();
                	
            		if (bridges != null && bridges.length() > 0)
            		{
	            		try {
							bridges = "bridge://" + URLEncoder.encode(bridges,"UTF-8");
		            		
		                	IntentIntegrator integrator = new IntentIntegrator(OrbotMainActivity.this);
		                	integrator.shareText(bridges);
		                	
						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
            		}

                }
                
                return true;
            
            }
        
        });
            
        
        }

    /**
     * This is our attempt to REALLY exit Orbot, and stop the background service
     * However, Android doesn't like people "quitting" apps, and/or our code may
     * not be quite right b/c no matter what we do, it seems like the TorService
     * still exists
     **/
    private void doExit() {
        stopTor();

        // Kill all the wizard activities
        setResult(RESULT_CLOSE_ALL);
        finish();
    }

	protected void onPause() {
		try
		{
			super.onPause();
	
			if (aDialog != null)
				aDialog.dismiss();
		}
		catch (IllegalStateException ise)
		{
			//can happen on exit/shutdown
		}
	}

	private void doTorCheck ()
	{
		
		openBrowser(URL_TOR_CHECK,false);
		

	}
	
	private void enableHiddenServicePort (int hsPort) throws RemoteException, InterruptedException
	{
		
		Editor pEdit = mPrefs.edit();
		
		String hsPortString = mPrefs.getString("pref_hs_ports", "");
		String onionHostname = mPrefs.getString("pref_hs_hostname","");
		
		if (hsPortString.indexOf(hsPort+"")==-1)
		{
			if (hsPortString.length() > 0 && hsPortString.indexOf(hsPort+"")==-1)
				hsPortString += ',' + hsPort;
			else
				hsPortString = hsPort + "";
			
			pEdit.putString("pref_hs_ports", hsPortString);
			pEdit.putBoolean("pref_hs_enable", true);
			
			pEdit.commit();
		}

		if (onionHostname == null || onionHostname.length() == 0)
		{
			requestTorRereadConfig();

			new Thread () {

			    public void run ()
				{
					String onionHostname = mPrefs.getString("pref_hs_hostname","");

					while (onionHostname.length() == 0)				
					{
						//we need to stop and start Tor
						try {					
							Thread.sleep(3000); //wait three seconds					
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						 
						onionHostname = mPrefs.getString("pref_hs_hostname","");						
					}
					
					Intent nResult = new Intent();
					nResult.putExtra("hs_host", onionHostname);
					setResult(RESULT_OK, nResult);
					finish();
				}
			}.start();
		
		}
		else
		{
			Intent nResult = new Intent();
			nResult.putExtra("hs_host", onionHostname);
			setResult(RESULT_OK, nResult);
			finish();
		}
	
	}


	private synchronized void handleIntents ()
	{
		if (getIntent() == null)
			return;
		
	    // Get intent, action and MIME type
	    Intent intent = getIntent();
	    String action = intent.getAction();
	    Log.e(TAG, "handleIntents " + action);

	    String type = intent.getType();
		
		if (action == null)
			return;
		
		if (action.equals("org.torproject.android.REQUEST_HS_PORT"))
		{
        	final int hiddenServicePortRequest = getIntent().getIntExtra("hs_port", -1);

			DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			    
			    public void onClick(DialogInterface dialog, int which) {
			        switch (which){
			        case DialogInterface.BUTTON_POSITIVE:
			            
						try {
							enableHiddenServicePort (hiddenServicePortRequest);
							
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						
			            break;

			        case DialogInterface.BUTTON_NEGATIVE:
			            //No button clicked
			        	finish();
			            break;
			        }
			    }
			};


			String requestMsg = getString(R.string.hidden_service_request, hiddenServicePortRequest);
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(requestMsg).setPositiveButton("Allow", dialogClickListener)
			    .setNegativeButton("Deny", dialogClickListener).show();
			
			return; //don't null the setIntent() as we need it later
		}
		else if (action.equals("org.torproject.android.START_TOR"))
		{
			autoStartFromIntent = true;
		    try {
		        Log.i(TAG, "action equals org.torproject.android.START_TOR");
		        startTor();

		        Intent resultIntent = new Intent(intent);
		        resultIntent.putExtra("socks_proxy", "socks://127.0.0.1:" + TorServiceConstants.PORT_SOCKS_DEFAULT);
                resultIntent.putExtra("socks_proxy_host", "127.0.0.1");
                resultIntent.putExtra("socks_proxy_port", TorServiceConstants.PORT_SOCKS_DEFAULT);
                resultIntent.putExtra("http_proxy", "http://127.0.0.1" + TorServiceConstants.PORT_HTTP);
                resultIntent.putExtra("http_proxy_host", "127.0.0.1");
                resultIntent.putExtra("http_proxy_port", TorServiceConstants.PORT_HTTP);
		        setResult(RESULT_OK, resultIntent);
		        finish();
		    } catch (RemoteException e) {
		        // TODO Auto-generated catch block
		        e.printStackTrace();
		    }

		}
		else if (action.equals(Intent.ACTION_VIEW))
		{
			String urlString = intent.getDataString();
			
			if (urlString != null)
			{
				
				if (urlString.toLowerCase().startsWith("bridge://"))

				{
					String newBridgeValue = urlString.substring(9); //remove the bridge protocol piece
					newBridgeValue = URLDecoder.decode(newBridgeValue); //decode the value here
					
					setNewBridges(newBridgeValue);
				}
			}
		}
		
		updateStatus("");
		
		setIntent(null);
		
		
	}
		
	private void setNewBridges (String newBridgeValue)
	{

		showAlert(getString(R.string.bridges_updated),getString(R.string.restart_orbot_to_use_this_bridge_) + newBridgeValue,false);	
		
		Prefs.setBridgesList(newBridgeValue); //set the string to a preference
		Prefs.putBridgesEnabled(true);
		
		setResult(RESULT_OK);
		
		mBtnBridges.setChecked(true);
		
		enableBridges(true);
	}	

	/*
	 * Launch the system activity for Uri viewing with the provided url
	 */
	private void openBrowser(final String browserLaunchUrl,boolean forceExternal)
	{
		boolean isOrwebInstalled = appInstalledOrNot("info.guardianproject.browser");
		
		if (mBtnVPN.isChecked()||forceExternal)
		{
			//use the system browser since VPN is on
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(browserLaunchUrl));
			intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		}
		else if (Prefs.useTransparentProxying())
		{
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(browserLaunchUrl));
			intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		}
		else if (isOrwebInstalled)
		{
			startIntent("info.guardianproject.browser",Intent.ACTION_VIEW,Uri.parse(browserLaunchUrl));						
		}
		else
		{
			AlertDialog aDialog = new AlertDialog.Builder(OrbotMainActivity.this)
              .setIcon(R.drawable.onion32)
		      .setTitle(R.string.install_apps_)
		      .setMessage(R.string.it_doesn_t_seem_like_you_have_orweb_installed_want_help_with_that_or_should_we_just_open_the_browser_)
		      .setPositiveButton(R.string.install_orweb, new Dialog.OnClickListener ()
		      {

				@Override
				public void onClick(DialogInterface dialog, int which) {

					//prompt to install Orweb
					Intent intent = new Intent(OrbotMainActivity.this,PromoAppsActivity.class);
					startActivity(intent);
					
				}
		    	  
		      })
		      .setNegativeButton(R.string.standard_browser, new Dialog.OnClickListener ()
		      {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(browserLaunchUrl));
					intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
					
				}
		    	  
		      })
		      .show();
			  
		}
		
	}
	
	
	
    

    private void startIntent (String pkg, String action, Uri data)
    {
        Intent i;
        PackageManager manager = getPackageManager();
        try {
            i = manager.getLaunchIntentForPackage(pkg);
            if (i == null)
                throw new PackageManager.NameNotFoundException();            
            i.setAction(action);
            i.setData(data);
            startActivity(i);
        } catch (PackageManager.NameNotFoundException e) {

        }
    }
    
    private boolean appInstalledOrNot(String uri)
    {
        PackageManager pm = getPackageManager();
        try
        {
               PackageInfo pi = pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);               
               return pi.applicationInfo.enabled;
        }
        catch (PackageManager.NameNotFoundException e)
        {
              return false;
        }
   }    
    
    @Override
    protected void onActivityResult(int request, int response, Intent data) {
        super.onActivityResult(request, response, data);

        if (request == REQUEST_SETTINGS && response == RESULT_OK)
        {
            OrbotApp.forceChangeLanguage(this);
            if (data != null && data.getBooleanExtra("transproxywipe", false))
            {
                    
                    boolean result = flushTransProxy();
                    
                    if (result)
                    {

                        Toast.makeText(this, R.string.transparent_proxy_rules_flushed_, Toast.LENGTH_SHORT).show();
                         
                    }
                    else
                    {

                        Toast.makeText(this, R.string.you_do_not_have_root_access_enabled, Toast.LENGTH_SHORT).show();
                         
                    }
                
            }
            else if (torStatus == TorServiceConstants.STATUS_ON)
            {
                updateTransProxy();
                Toast.makeText(this, R.string.you_may_need_to_stop_and_start_orbot_for_settings_change_to_be_enabled_, Toast.LENGTH_SHORT).show();

            }
        }
        else if (request == REQUEST_VPN && response == RESULT_OK)
        {
            sendIntentToService(TorServiceConstants.CMD_VPN);
        }
        
        IntentResult scanResult = IntentIntegrator.parseActivityResult(request, response, data);
        if (scanResult != null) {
             // handle scan result
        	
        	String results = scanResult.getContents();
        	
        	if (results != null && results.length() > 0)
        	{
	        	try {
					results = URLDecoder.decode(results, "UTF-8");
					
					int urlIdx = results.indexOf("://");
					
					if (urlIdx!=-1)
						results = results.substring(urlIdx+3);
					
					setNewBridges(results);
					
				} catch (UnsupportedEncodingException e) {
					Log.e(TAG,"unsupported",e);
				}
        	}
        	
          }
        
    }
    
    public void promptSetupBridges ()
    {
    	LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.layout_diag, null); 
        
        TextView versionName = (TextView)view.findViewById(R.id.diaglog);
        versionName.setText(R.string.if_your_mobile_network_actively_blocks_tor_you_can_use_a_tor_bridge_to_access_the_network_another_way_to_get_bridges_is_to_send_an_email_to_bridges_torproject_org_please_note_that_you_must_send_the_email_using_an_address_from_one_of_the_following_email_providers_riseup_gmail_or_yahoo_);    
        
        if (mBtnBridges.isChecked())
        {
	        new AlertDialog.Builder(this)
	        .setTitle(R.string.bridge_mode)
	        .setView(view)
	        .setItems(R.array.bridge_options, new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
               // The 'which' argument contains the index position
               // of the selected item
            	   
            	   switch (which)
            	   {
            	   case 0: //obfs 4;
            		   showGetBridgePrompt("obfs4");
            		   
            		   break;
            	   case 1: //obfs3
            		   showGetBridgePrompt("obfs3");
            		   
            		   break;
            	   case 2: //scramblesuit
            		   showGetBridgePrompt("scramblesuit");
            		   
            		   break;
            	   case 3: //azure
            		   Prefs.setBridgesList("2");
            		   enableBridges(true);
            		   
            		   break;
            	   case 4: //amazon
                       Prefs.setBridgesList("1");
            		   enableBridges(true);
            		   
            		   break;
            	   case 5: //google
                       Prefs.setBridgesList("0");
            		   enableBridges(true);
            		   
            		   break;
            		  
            	   }
            	   
               }
           }).setNegativeButton(android.R.string.cancel, new Dialog.OnClickListener()
	        {
	        	@Override
				public void onClick(DialogInterface dialog, int which) {
					
	            	//mBtnBridges.setChecked(false);
					
				}
	        })
	        .show();
	        
	       
        }
        else
        {
        	enableBridges(false);
        }
        
    }
    
    private void showGetBridgePrompt (final String type)
    {
    	LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.layout_diag, null); 
        
        TextView versionName = (TextView)view.findViewById(R.id.diaglog);
        versionName.setText(R.string.you_must_get_a_bridge_address_by_email_web_or_from_a_friend_once_you_have_this_address_please_paste_it_into_the_bridges_preference_in_orbot_s_setting_and_restart_);    
        
        new AlertDialog.Builder(this)
        .setTitle(R.string.bridge_mode)
        .setView(view)
        .setNegativeButton(android.R.string.cancel, new Dialog.OnClickListener()
        {
        	@Override
			public void onClick(DialogInterface dialog, int which) {
				//do nothing
			}
        })
        .setNeutralButton(R.string.get_bridges_email, new Dialog.OnClickListener ()
        {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				

				sendGetBridgeEmail(type);

			}

       	 
        })
        .setPositiveButton(R.string.get_bridges_web, new Dialog.OnClickListener ()
        {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				
				openBrowser(URL_TOR_BRIDGES + type,true);

			}

       	 
        }).show();
    }
    
    private void sendGetBridgeEmail (String type)
    {
    	Intent intent = new Intent(Intent.ACTION_SEND);
    	intent.setType("message/rfc822");
		intent.putExtra(Intent.EXTRA_EMAIL  , new String[]{"bridges@torproject.org"});
		
		if (type != null)
		{
	    	intent.putExtra(Intent.EXTRA_SUBJECT, "get transport " + type);
	    	intent.putExtra(Intent.EXTRA_TEXT, "get transport " + type);
	    	
		}
		else
		{
			intent.putExtra(Intent.EXTRA_SUBJECT, "get bridges");
			intent.putExtra(Intent.EXTRA_TEXT, "get bridges");
			
		}
		
    	startActivity(Intent.createChooser(intent, getString(R.string.send_email)));
    }
    
    private void enableBridges (boolean enable)
    {
		Prefs.putBridgesEnabled(enable);

		if (torStatus == TorServiceConstants.STATUS_ON)
		{
			String bridgeList = Prefs.getBridgesList();
			if (bridgeList != null && bridgeList.length() > 0)
			{
				requestTorRereadConfig ();
			}
		}
    }
    
    private void requestTorRereadConfig()    {
        sendIntentToService (TorServiceConstants.CMD_START);
    }
    
    public void promptStartVpnService ()
    {
    	 LayoutInflater li = LayoutInflater.from(this);
         View view = li.inflate(R.layout.layout_diag, null); 
         
         TextView versionName = (TextView)view.findViewById(R.id.diaglog);
         versionName.setText(R.string.you_can_enable_all_apps_on_your_device_to_run_through_the_tor_network_using_the_vpn_feature_of_android_);    
         
         new AlertDialog.Builder(this)
         .setTitle(R.string.apps_mode)
         .setView(view)
         .setPositiveButton(R.string.activate, new Dialog.OnClickListener ()
         {

			@Override
			public void onClick(DialogInterface dialog, int which) {
		        Prefs.putUseVpn(true);
				startVpnService();
			}

        	 
         })
         .setNegativeButton(android.R.string.cancel, new Dialog.OnClickListener ()
         {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				
				mBtnVPN.setChecked(false);
			}
        	 
         })
         .show();
    }
    
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void startVpnService ()
    {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent,REQUEST_VPN);
        } 
        else
        {
            sendIntentToService(TorServiceConstants.CMD_VPN);
        }
    }
    
    public void stopVpnService ()
    {    	
        sendIntentToService(TorServiceConstants.CMD_VPN_CLEAR);
    }

    private boolean flushTransProxy ()
    {
        sendIntentToService(TorServiceConstants.CMD_FLUSH);
        return true;
    }
    
    private boolean updateTransProxy ()
    {
        sendIntentToService(TorServiceConstants.CMD_UPDATE_TRANS_PROXY);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mPrefs != null)
        {
			mBtnVPN.setChecked(Prefs.useVpn());			
			mBtnBridges.setChecked(Prefs.bridgesEnabled());
        }

        mStatusUpdateHandler.postDelayed(new Runnable ()
        {
            public void run ()
            {
        
                handleIntents();

            }
        }
        , 500);
        
        
    }

    AlertDialog aDialog = null;
    
    //general alert dialog for mostly Tor warning messages
    //sometimes this can go haywire or crazy with too many error
    //messages from Tor, and the user cannot stop or exit Orbot
    //so need to ensure repeated error messages are not spamming this method
    private void showAlert(String title, String msg, boolean button)
    {
            try
            {
                    if (aDialog != null && aDialog.isShowing())
                            aDialog.dismiss();
            }
            catch (Exception e){} //swallow any errors
            
             if (button)
             {
                            aDialog = new AlertDialog.Builder(OrbotMainActivity.this)
                     .setIcon(R.drawable.onion32)
             .setTitle(title)
             .setMessage(msg)
             .setPositiveButton(android.R.string.ok, null)
             .show();
             }
             else
             {
                     aDialog = new AlertDialog.Builder(OrbotMainActivity.this)
                     .setIcon(R.drawable.onion32)
             .setTitle(title)
             .setMessage(msg)
             .show();
             }
    
             aDialog.setCanceledOnTouchOutside(true);
    }
    
    private void updateStatus (String torServiceMsg)
    {
        
            //now update the layout_main UI based on the status
            if (imgStatus != null)
            {
                    
                    if (torStatus == TorServiceConstants.STATUS_ON)
                    {
                            
                            imgStatus.setImageResource(R.drawable.toron);
                    		
                            mBtnBrowser.setEnabled(true);
                            
                            if (mItemOnOff != null)
                                    mItemOnOff.setTitle(R.string.menu_stop);
                            
                            if (lblStatus != null && torServiceMsg != null)
                            	if (torServiceMsg.indexOf('%')!=-1)
                            		lblStatus.setText(torServiceMsg);
                            	else
                            		lblStatus.setText("");
                            
                            boolean showFirstTime = mPrefs.getBoolean("connect_first_time",true);
                            
                            if (showFirstTime)
                            {
                            
                                    Editor pEdit = mPrefs.edit();
                                    
                                    pEdit.putBoolean("connect_first_time",false);
                                    
                                    pEdit.commit();
                                    
                                    showAlert(getString(R.string.status_activated),getString(R.string.connect_first_time),true);
                                    
                            }
                            
                            if (autoStartFromIntent)
                            {
                                autoStartFromIntent = false;
                                finish();
                                Log.e(TAG, "autoStartFromIntent finish");
                            }
                    }
                    else if (torStatus == TorServiceConstants.STATUS_STARTING)
                    {
                        
                        imgStatus.setImageResource(R.drawable.torstarting);
                
                        if (mItemOnOff != null)
                                mItemOnOff.setTitle(R.string.menu_stop);
                	
                        if (lblStatus != null && torServiceMsg != null)
                        	if (torServiceMsg.indexOf('%')!=-1)
                        		lblStatus.setText(torServiceMsg);
                        	
                       
                    	           
                    }
                    else if (torStatus == TorServiceConstants.STATUS_OFF)
                    {
                        imgStatus.setImageResource(R.drawable.toroff);
                        lblStatus.setText(getString(R.string.press_to_start));
                        mBtnBrowser.setEnabled(false);
                        
                        if (mItemOnOff != null)
                                mItemOnOff.setTitle(R.string.menu_start);

                    }
                    
                    if (torServiceMsg != null && torServiceMsg.length() > 0)
                    {
                    	mTxtOrbotLog.append(torServiceMsg + '\n');
                    }
            }
                
           
    }
        
         
  
  // guess what? this start's Tor! actually no it just requests via the local ITorService to the remote TorService instance
  // to start Tor
    private void startTor () throws RemoteException
    {
		sendIntentToService (TorServiceConstants.CMD_START);
		torStatus = TorServiceConstants.STATUS_STARTING;
				
		mTxtOrbotLog.setText("");

        //here we update the UI which is a bit sloppy and mixed up code wise
        //might be best to just call updateStatus() instead of directly manipulating UI in this method - yep makes sense
        imgStatus.setImageResource(R.drawable.torstarting);
        lblStatus.setText(getString(R.string.status_starting_up));
        
        //we send a message here to the progressDialog i believe, but we can clarify that shortly
        Message msg = mStatusUpdateHandler.obtainMessage(TorServiceConstants.ENABLE_TOR_MSG);
        msg.getData().putString(HANDLER_TOR_MSG, getString(R.string.status_starting_up));
        mStatusUpdateHandler.sendMessage(msg);
    }

    public boolean onLongClick(View view) {
        try {
            if (torStatus == TorServiceConstants.STATUS_OFF) {
                startTor();
            } else {
                stopTor();
            }
            return true;
        } catch (RemoteException e) {
            Log.d(TAG, "error onclick", e);
        }
        return false;
    }

// this is what takes messages or values from the callback threads or other non-mainUI threads
//and passes them back into the main UI thread for display to the user
    private Handler mStatusUpdateHandler = new Handler() {
        
        private String lastServiceMsg = null;
        
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TorServiceConstants.STATUS_MSG:
                case TorServiceConstants.LOG_MSG:

                        String torServiceMsg = (String)msg.getData().getString(HANDLER_TOR_MSG);
                        
                        if (lastServiceMsg == null || !lastServiceMsg.equals(torServiceMsg))
                        {
                            updateStatus(torServiceMsg);
                        
                            lastServiceMsg = torServiceMsg;
                        }
                        
                    break;
                case TorServiceConstants.ENABLE_TOR_MSG:
                        
                        
                        updateStatus((String)msg.getData().getString(HANDLER_TOR_MSG));
                        
                        break;
                case TorServiceConstants.DISABLE_TOR_MSG:
                    
                    updateStatus((String)msg.getData().getString(HANDLER_TOR_MSG));
                    
                    break;
                    

                case TorServiceConstants.MESSAGE_TRAFFIC_COUNT :
                    
                    Bundle data = msg.getData();
                    DataCount datacount =  new DataCount(data.getLong("upload"),data.getLong("download"));     
                    
                    long totalRead = data.getLong("readTotal");
                    long totalWrite = data.getLong("writeTotal");
                
                    downloadText.setText(formatCount(datacount.Download) + " / " + formatTotal(totalRead));
                    uploadText.setText(formatCount(datacount.Upload) + " / " + formatTotal(totalWrite));
            
                    if (torStatus != TorServiceConstants.STATUS_ON)
                    {
                        updateStatus("");
                    }
                        
                default:
                    super.handleMessage(msg);
            }
        }
        
        
        
    };

       @Override
    protected void onDestroy() {
        super.onDestroy();
          LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalBroadcastReceiver);

    }

    public class DataCount {
           // data uploaded
           public long Upload;
           // data downloaded
           public long Download;
           
           DataCount(long Upload, long Download){
               this.Upload = Upload;
               this.Download = Download;
           }
       }
       
    private String formatCount(long count) {
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.getDefault());
        // Converts the supplied argument into a string.
        // Under 2Mb, returns "xxx.xKb"
        // Over 2Mb, returns "xxx.xxMb"
        if (count < 1e6)
            return numberFormat.format(Math.round(((float) ((int) (count * 10 / 1024)) / 10)))
                    + getString(R.string.kbps);
        else
            return numberFormat.format(Math
                    .round(((float) ((int) (count * 100 / 1024 / 1024)) / 100)))
                    + getString(R.string.mbps);
    }

    private String formatTotal(long count) {
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.getDefault());
        // Converts the supplied argument into a string.
        // Under 2Mb, returns "xxx.xKb"
        // Over 2Mb, returns "xxx.xxMb"
        if (count < 1e6)
            return numberFormat.format(Math.round(((float) ((int) (count * 10 / 1024)) / 10)))
                    + getString(R.string.kb);
        else
            return numberFormat.format(Math
                    .round(((float) ((int) (count * 100 / 1024 / 1024)) / 100)))
                    + getString(R.string.mb);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
    
        
    }
    
      private static final float ROTATE_FROM = 0.0f;
        private static final float ROTATE_TO = 360.0f*4f;// 3.141592654f * 32.0f;

    public void spinOrbot (float direction)
    {
            sendIntentToService (TorServiceConstants.CMD_NEWNYM);
        
        
            Toast.makeText(this, R.string.newnym, Toast.LENGTH_SHORT).show();
            
        //    Rotate3dAnimation rotation = new Rotate3dAnimation(ROTATE_FROM, ROTATE_TO*direction, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
             Rotate3dAnimation rotation = new Rotate3dAnimation(ROTATE_FROM, ROTATE_TO*direction, imgStatus.getWidth()/2f,imgStatus.getWidth()/2f,20f,false);
             rotation.setFillAfter(true);
              rotation.setInterpolator(new AccelerateInterpolator());
              rotation.setDuration((long) 2*1000);
              rotation.setRepeatCount(0);
              imgStatus.startAnimation(rotation);
              
        
    }
    
    class MyGestureDetector extends SimpleOnGestureListener {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                try {                    
                    if (torStatus == TorServiceConstants.STATUS_ON)
                    {
                        float direction = 1f;
                        if (velocityX < 0)
                            direction = -1f;
                        spinOrbot (direction);
                    }
                } catch (Exception e) {
                    // nothing
                }
                return false;
            }
    }
}
