package android.boilerplate.cc.trapps.city.castcodelab;

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
import android.widget.Toast;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

// Step 1
public class CastActivity extends ActionBarActivity {
    private static final String TAG = CastActivity.class.getSimpleName();

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
        mMediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast("37D1FF71")).build();

        // Some step (was missing)
        mMediaRouterCallback = new MyMediaRouterCallback();
    }

    // Step 5
    @Override
    protected void onResume() {
        super.onResume();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    // Step 6
    @Override
    protected void onPause() {
        if (isFinishing()) {
            mMediaRouter.removeCallback(mMediaRouterCallback);
        }
        super.onPause();
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
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }

    // Step 5
    private class MyMediaRouterCallback extends MediaRouter.Callback {
        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            mSelectedDevice = CastDevice.getFromBundle(info.getExtras());
            String routeId = info.getId();

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
                    if (mApiClient != null) {
                        Log.d(TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(mApiClient));
                    }
                }

                @Override
                public void onApplicationDisconnected(int errorCode) {
                    teardown();
                }
            };

            // Step 15
            Cast.CastOptions apiOptions = Cast.CastOptions.builder(mSelectedDevice, mCastClientListener).setVerboseLoggingEnabled(true).build();

            // Step 9
            mConnectionCallbacks = new ConnectionCallbacks();
            mConnectionFailedListener = new ConnectionFailedListener();
            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(mSelectedDevice, mCastClientListener);
            mApiClient = new GoogleApiClient.Builder(getApplicationContext()).addApi(Cast.API, apiOptionsBuilder.build()).addConnectionCallbacks(mConnectionCallbacks).addOnConnectionFailedListener(mConnectionFailedListener).build();

            // Step 10
            if (!mApiClient.isConnected()) {
                mApiClient.connect();
            }
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
                    Cast.CastApi.launchApplication(mApiClient, "37D1FF71", false).setResultCallback(new ResultCallback<Cast.ApplicationConnectionResult>() {
                        @Override
                        public void onResult(Cast.ApplicationConnectionResult result) {
                            Status status = result.getStatus();
                            if (status.isSuccess()) {
                                ApplicationMetadata applicationMetadata = result.getApplicationMetadata();
                                mSessionId = result.getSessionId();
                                String applicationStatus = result.getApplicationStatus();
                                boolean wasLaunched = result.getWasLaunched();
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

    class HelloWorldChannel implements Cast.MessageReceivedCallback {
        public String getNamespace() {
            return "urn:x-cast:com.example.custom";
        }

        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
            Log.d(TAG, "onMessageReceived: " + message);
        }
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
}