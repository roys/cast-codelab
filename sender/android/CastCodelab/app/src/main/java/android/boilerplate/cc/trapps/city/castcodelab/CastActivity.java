package android.boilerplate.cc.trapps.city.castcodelab;

import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

// Step 1
public class CastActivity extends ActionBarActivity {
    private static final String TAG = CastActivity.class.getSimpleName();
    private static final String NAMESPACE = "urn:x-cast:city.trapps.cc.invaders";
    private static final String APPID = "37D1FF71";

    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private CastDevice mSelectedDevice;
    private MediaRouter.Callback mMediaRouterCallback;
    private GoogleApiClient mApiClient;
    private ConnectionCallbacks mConnectionCallbacks;
    private Cast.Listener mCastClientListener;
    private HelloWorldChannel mHelloWorldChannel;
    private ConnectionFailedListener mConnectionFailedListener;
    private String mSessionId;
    private boolean mWaitingForReconnect, mApplicationStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cast);

        // Step 2
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());

        // Step 3
        mMediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(APPID)).build();

        // Some step (was missing)
        mMediaRouterCallback = new MyMediaRouterCallback();

        Button sendMessageButton = (Button) findViewById(R.id.button);
        sendMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand("JOIN");
            }
        });
    }

    // Step 7
    @Override
    protected void onStart() {
        super.onStart();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    // Step 8
    @Override
    protected void onStop() {
        mMediaRouter.removeCallback(mMediaRouterCallback);
        super.onStop();
    }

    // Step 6
    @Override
    protected void onPause() {
        if (isFinishing()) {
            mMediaRouter.removeCallback(mMediaRouterCallback);
        }
        super.onPause();
    }

    // Step 5
    @Override
    protected void onResume() {
        super.onResume();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_cast, menu);

        // Step 4 (import the appcompat alternative)
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    // Step 13
    private void reconnectChannels(Bundle hint) {
        if ((hint != null) && hint.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
            teardown();
        } else {
            try {
                Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mHelloWorldChannel.getNamespace(), mHelloWorldChannel);
            } catch (IOException e) {
                Log.e(TAG, "Exception while creating media channel ", e);
            }
        }
    }

    // Step 16
    private void sendMessage(String message) {
        Log.d(TAG, "sendMessage: " + message);
        if (mApiClient != null && mHelloWorldChannel != null) {
            try {
                Cast.CastApi.sendMessage(mApiClient, mHelloWorldChannel.getNamespace(), message).setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status result) {
                        if (!result.isSuccess()) {
                            Log.e(TAG, "Sending message failed");
                        } else {
                            Log.d(TAG, "Sent message to receiver: " + result.toString());
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Exception while sending message", e);
            }
        } else {
            Log.w(TAG, "No client or channel to send message to.");
        }
    }

    // Step 17
    private void sendCommand(String command) {
        JSONObject json = new JSONObject();
        JSONObject extra = new JSONObject();

        try {
            extra.put("playerId", Build.MODEL); // Player name
            extra.put("enableSounds", true);
            json.put("command", command); // JOIN / SHOOT / LEFT / RIGHT / SOUNDON / SOUNDOFF
            json.put("extra", extra);
            sendMessage(json.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Got JSONException while trying to send message. User will not be notified.", e);
        }
    }

    // Step 14
    private void teardown() {
        Log.d(TAG, "teardown");
        if (mApiClient != null) {
            if (mApplicationStarted) {
                if (mApiClient.isConnected() || mApiClient.isConnecting()) {
                    try {
                        Cast.CastApi.stopApplication(mApiClient, mSessionId);
                        if (mHelloWorldChannel != null) {
                            Cast.CastApi.removeMessageReceivedCallbacks(mApiClient, mHelloWorldChannel.getNamespace());
                            mHelloWorldChannel = null;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Exception while removing channel", e);
                    }
                    mApiClient.disconnect();
                }
                mApplicationStarted = false;
            }
            mApiClient = null;
        }
        mSelectedDevice = null;
        mWaitingForReconnect = false;
        mSessionId = null;
    }

    // Step 5
    private class MyMediaRouterCallback extends MediaRouter.Callback {
        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            mSelectedDevice = CastDevice.getFromBundle(info.getExtras());

            // Step 12
            mCastClientListener = new Cast.Listener() {
                @Override
                public void onApplicationStatusChanged() {
                    if (mApiClient != null) {
                        Log.d(TAG, "onApplicationStatusChanged: " + Cast.CastApi.getApplicationStatus(mApiClient));
                    }
                }

                @Override
                public void onVolumeChanged() {
                }

                @Override
                public void onApplicationDisconnected(int errorCode) {
                    teardown();
                }
            };

            // Step 9
            mConnectionCallbacks = new ConnectionCallbacks();
            mConnectionFailedListener = new ConnectionFailedListener();
            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(mSelectedDevice, mCastClientListener);
            mApiClient = new GoogleApiClient.Builder(getApplicationContext()).addApi(Cast.API, apiOptionsBuilder.build()).addConnectionCallbacks(mConnectionCallbacks).addOnConnectionFailedListener(mConnectionFailedListener).build();

            // Step 10
            if (!mApiClient.isConnected()) {
                mApiClient.connect();
            }

            // Step 15
            Cast.CastOptions.builder(mSelectedDevice, mCastClientListener).setVerboseLoggingEnabled(true).build();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
            teardown();
            mSelectedDevice = null;
        }
    }

    // Step 11 (import gms version)
    private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {
        @Override
        public void onConnected(Bundle connectionHint) {
            if (mWaitingForReconnect) {
                mWaitingForReconnect = false;
                reconnectChannels(connectionHint);
            } else {
                try {
                    Cast.CastApi.launchApplication(mApiClient, APPID, false).setResultCallback(new ResultCallback<Cast.ApplicationConnectionResult>() {
                        @Override
                        public void onResult(Cast.ApplicationConnectionResult result) {
                            Status status = result.getStatus();
                            if (status.isSuccess()) {
                                Log.d(TAG, "metaData: [" + result.getApplicationMetadata() + "], applicationStatus: [" + result.getApplicationStatus() + "], wasLaunched: [" + result.getWasLaunched() + "]");
                                mSessionId = result.getSessionId();
                                mHelloWorldChannel = new HelloWorldChannel();
                            } else {
                                teardown();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch application", e);
                }
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            mWaitingForReconnect = true;
        }
    }

    private class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {
        @Override
        public void onConnectionFailed(ConnectionResult result) {
            teardown();
        }
    }

    private class HelloWorldChannel implements Cast.MessageReceivedCallback {
        public String getNamespace() {
            return NAMESPACE;
        }

        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
            Log.d(TAG, "onMessageReceived: " + message);
        }
    }
}