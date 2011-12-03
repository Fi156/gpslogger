package com.mendhak.gpslogger.senders.dropbox;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session;
import com.dropbox.client2.session.TokenPair;
import com.mendhak.gpslogger.GpsMainActivity;
import com.mendhak.gpslogger.R;
import com.mendhak.gpslogger.common.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class DropBoxHelper implements IDropBoxHelper
{

    final static private String ACCESS_KEY_NAME = "DROPBOX_ACCESS_KEY";
    final static private String ACCESS_SECRET_NAME = "DROPBOX_ACCESS_SECRET";
    final static private Session.AccessType ACCESS_TYPE = Session.AccessType.APP_FOLDER;
    Context ctx;
    DropboxAPI<AndroidAuthSession> dropboxApi;
    GpsMainActivity mainActivity;


    public DropBoxHelper(Context context)
    {
        ctx = context;
        AndroidAuthSession session = buildSession();
        dropboxApi = new DropboxAPI<AndroidAuthSession>(session);
    }

    public boolean IsLinked()
    {
        return dropboxApi.getSession().isLinked();
    }

    public void FinishAuthorization()
    {
        AndroidAuthSession session = dropboxApi.getSession();
        if(session.authenticationSuccessful())
        {
            // Mandatory call to complete the auth
            session.finishAuthentication();

            // Store it locally in our app for later use
            TokenPair tokens = session.getAccessTokenPair();
            storeKeys(tokens.key, tokens.secret);
        }
    }


    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     */
    private void storeKeys(String key, String secret)
    {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(ACCESS_KEY_NAME, key);
        edit.putString(ACCESS_SECRET_NAME, secret);
        edit.commit();
    }

    private void clearKeys()
    {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor edit = prefs.edit();
        edit.remove(ACCESS_KEY_NAME);
        edit.remove(ACCESS_SECRET_NAME);
        edit.commit();
    }


    private AndroidAuthSession buildSession()
    {
        int dropboxAppKey = ctx.getResources().getIdentifier("dropbox_appkey", "string", ctx.getPackageName());
        int dropboxAppSecret = ctx.getResources().getIdentifier("dropbox_appsecret", "string", ctx.getPackageName());
        AppKeyPair appKeyPair = new AppKeyPair(ctx.getString(dropboxAppKey), ctx.getString(dropboxAppSecret));
        AndroidAuthSession session;

        String[] stored = getKeys();
        if(stored != null)
        {
            AccessTokenPair accessToken = new AccessTokenPair(stored[0], stored[1]);
            session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE, accessToken);
        }
        else
        {
            session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE);
        }

        return session;
    }

    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     *
     * @return Array of [access_key, access_secret], or null if none stored
     */
    private String[] getKeys()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String key = prefs.getString(ACCESS_KEY_NAME, null);
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if(key != null && secret != null)
        {
            String[] ret = new String[2];
            ret[0] = key;
            ret[1] = secret;
            return ret;
        }
        else
        {
            return null;
        }
    }

    public void StartAuthentication(DropBoxAuthorizationActivity dropBoxAuthorizationActivity)
    {
        // Start the remote authentication
        dropboxApi.getSession().startAuthentication(dropBoxAuthorizationActivity);
    }

    public void UnLink()
    {
        // Remove credentials from the session
        dropboxApi.getSession().unlink();

        // Clear our stored keys
        clearKeys();
    }

    public void UploadFile(String fileName, GpsMainActivity activity)
    {
        mainActivity = activity;
        Thread t = new Thread(new DropBoxUploadHandler(fileName, dropboxApi, this));
        t.start();
    }

    public void OnComplete()
    {
        mainActivity.handler.post(mainActivity.updateDropBoxUpload);
    }


    public class DropBoxUploadHandler implements Runnable
    {
        DropboxAPI<AndroidAuthSession> api;
        String fileName;
        IDropBoxHelper helper;

        public DropBoxUploadHandler(String file, DropboxAPI<AndroidAuthSession> dbApi, IDropBoxHelper dbHelper)
        {
            fileName = file;
            api = dbApi;
            helper = dbHelper;
        }

        public void run()
        {
            try
            {
                File gpsDir = new File(Environment.getExternalStorageDirectory(), "GPSLogger");
                File gpxFile = new File(gpsDir, fileName);

                FileInputStream fis = new FileInputStream(gpxFile);
                DropboxAPI.Entry upEntry = api.putFileOverwrite(gpxFile.getName(), fis, gpxFile.length(), null);
                Utilities.LogInfo("DropBox uploaded file rev is: " + upEntry.rev);
                helper.OnComplete();
            }
            catch(Exception e)
            {
                Utilities.LogError("DropBoxHelper.UploadFile", e);
            }
        }
    }

}

interface IDropBoxHelper
{
    public void OnComplete();

}

