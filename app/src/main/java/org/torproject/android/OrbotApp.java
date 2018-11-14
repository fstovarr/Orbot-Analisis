package org.torproject.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.support.v4.app.NotificationCompat;

import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.Display;
import com.github.javiersantos.appupdater.enums.UpdateFrom;

import org.torproject.android.service.OrbotConstants;
import org.torproject.android.service.util.Prefs;
import org.torproject.android.settings.Languages;
import org.torproject.android.settings.LocaleHelper;

import java.util.Locale;

import im.delight.android.languages.Language;

public class OrbotApp extends Application implements OrbotConstants {

    private Locale locale;

    /**
     * public static void forceChangeLanguage(Activity activity) {
     * Intent intent = activity.getIntent();
     * if (intent == null) // when launched as LAUNCHER
     * intent = new Intent(activity, OrbotMainActivity.class);
     * intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
     * activity.finish();
     * activity.overridePendingTransition(0, 0);
     * activity.startActivity(intent);
     * activity.overridePendingTransition(0, 0);
     * }
     **/

    public static Languages getLanguages(Activity activity) {
        return Languages.get(activity);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        /**
         Languages.setup(OrbotMainActivity.class, R.string.menu_settings);
         Languages.setLanguage(this, Prefs.getDefaultLocale(), true);
         **/
        Language.setFromPreference(this, "pref_default_locale");

        //check for updates via github, since it is unlikely to be blocked; notify the user of places where upgrades can be found
        new AppUpdater(this)
                .setUpdateFrom(UpdateFrom.JSON)
                .setUpdateJSON("https://raw.githubusercontent.com/n8fr8/orbot/master/update.json")
                .setDisplay(Display.NOTIFICATION).start();
    }

    @Override
    protected void attachBaseContext(Context base) {
        Prefs.setContext(base);
        super.attachBaseContext(LocaleHelper.onAttach(base, "en"));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Language.setFromPreference(this, "pref_default_locale");

        //Log.i(TAG, "onConfigurationChanged " + newConfig.locale.getLanguage());
        //    Languages.setLanguage(this, Prefs.getDefaultLocale(), true);
    }

    @SuppressLint("NewApi")
    protected void showToolbarNotification(String shortMsg, String notifyMsg, int notifyId, int icon) {

        NotificationCompat.Builder notifyBuilder;

        //Reusable code.
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(getPackageName());
        PendingIntent pendIntent = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notifyBuilder = new NotificationCompat.Builder(this)
                .setContentTitle(getString(org.torproject.android.service.R.string.app_name));


        notifyBuilder.setContentIntent(pendIntent);

        notifyBuilder.setContentText(shortMsg);
        notifyBuilder.setSmallIcon(icon);
        notifyBuilder.setTicker(notifyMsg);

        notifyBuilder.setOngoing(false);

        notifyBuilder.setStyle(new NotificationCompat.BigTextStyle()
                .bigText(notifyMsg).setBigContentTitle(getString(org.torproject.android.service.R.string.app_name)));

        Notification notification = notifyBuilder.build();

        notificationManager.notify(notifyId, notification);
    }
}
