/**
 * Copyright (c) 2017-present, Stanislav Doskalenko - doskalenko.s@gmail.com
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * Based on Asim Malik android source code, copyright (c) 2015
 *
 **/
package com.reactnative.googlefit;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.ErrorDialogFragment;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;

import java.util.ArrayList;


public class GoogleFitManager implements ActivityEventListener
{

    private ReactContext mReactContext;
    private GoogleSignInAccount mApiAccount;
    private Scope[] scopes;
    private static final int REQUEST_OAUTH = 1001;
    private static final String AUTH_PENDING = "auth_state_pending";
    private static boolean mAuthInProgress = false;
    private Activity mActivity;

    private DistanceHistory distanceHistory;
    private StepHistory stepHistory;
    private BodyHistory bodyHistory;
    private HeartrateHistory heartrateHistory;
    private CalorieHistory calorieHistory;
    private NutritionHistory nutritionHistory;
    private StepCounter mStepCounter;
    private StepSensor stepSensor;
    private RecordingApi recordingApi;
    private ActivityHistory activityHistory;

    private static final String TAG = "RNGoogleFit";

    public GoogleFitManager(ReactContext reactContext, Activity activity) {

        //Log.i(TAG, "Initializing GoogleFitManager" + mAuthInProgress);
        this.mReactContext = reactContext;
        this.mActivity = activity;
        this.scopes = new Scope[]{new Scope(Scopes.EMAIL), new Scope(Scopes.FITNESS_ACTIVITY_READ)};

        mReactContext.addActivityEventListener(this);

        this.mStepCounter = new StepCounter(mReactContext, this, activity);
        this.stepHistory = new StepHistory(mReactContext, this);
        this.bodyHistory = new BodyHistory(mReactContext, this);
        this.heartrateHistory = new HeartrateHistory(mReactContext, this);
        this.distanceHistory = new DistanceHistory(mReactContext, this);
        this.calorieHistory = new CalorieHistory(mReactContext, this);
        this.nutritionHistory = new NutritionHistory(mReactContext, this);
        this.recordingApi = new RecordingApi(mReactContext, this);
        this.activityHistory = new ActivityHistory(mReactContext, this);
        //        this.stepSensor = new StepSensor(mReactContext, activity);
    }

    public Activity getCurrentActivity() {
        if (this.mActivity != null && !this.mActivity.isFinishing()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                if (!this.mActivity.isDestroyed()) {
                    return this.mActivity;
                }
            } else {
                return this.mActivity;
            }
        }
        return null;
    }

    public GoogleSignInAccount getGoogleAccount() {
        if (this.mApiAccount == null) {
            this.mApiAccount = GoogleSignIn.getAccountForScopes(mReactContext, new Scope(Scopes.FITNESS_ACTIVITY_READ));
        }
        return this.mApiAccount;
    }

    public RecordingApi getRecordingApi() {
        return recordingApi;
    }

    public StepCounter getStepCounter() {
        return mStepCounter;
    }

    public StepHistory getStepHistory() {
        return stepHistory;
    }

    public BodyHistory getBodyHistory() {
        return bodyHistory;
    }

    public HeartrateHistory getHeartrateHistory() {
        return heartrateHistory;
    }

    public DistanceHistory getDistanceHistory() {
        return distanceHistory;
    }

    public void resetAuthInProgress() {
        if (!isAuthorized()) {
            mAuthInProgress = false;
        }
    }

    public CalorieHistory getCalorieHistory() {
        return calorieHistory;
    }

    public NutritionHistory getNutritionHistory() {
        return nutritionHistory;
    }

    public void authorize(ArrayList<String> userScopes) {
        if (userScopes.size() > 0) {
            this.scopes = new Scope[userScopes.size()];
            for (int i = 0; i < userScopes.size(); i++) {
                this.scopes[i] = new Scope(userScopes.get(i));
            }
        } else {
            this.scopes = new Scope[]{new Scope(Scopes.EMAIL), new Scope(Scopes.FITNESS_ACTIVITY_READ)};
        }

        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this.mReactContext), this.scopes)) {
            if (mAuthInProgress) {
                Log.i(TAG, "Authorization - Already attempting to resolve an error.");
            } else if (getCurrentActivity() == null) {
                Log.i(TAG, "Authorization - Current activity is null.");
                WritableMap map = Arguments.createMap();
                map.putString("message", "Cannot authorize from background");
                sendEvent(mReactContext, "GoogleFitAuthorizeFailure", map);
            } else {
                mAuthInProgress = true;
                GoogleSignIn.requestPermissions(
                        mActivity,
                        REQUEST_OAUTH,
                        GoogleSignIn.getLastSignedInAccount(this.mReactContext),
                        this.scopes
                );
            }
        } else {
            this.mApiAccount = GoogleSignIn.getAccountForScopes(mReactContext, new Scope(Scopes.FITNESS_ACTIVITY_READ));
            sendEvent(mReactContext, "GoogleFitAuthorizeSuccess", getUserProperties(GoogleSignIn.getAccountForScopes(mReactContext, new Scope(Scopes.FITNESS_ACTIVITY_READ))));
        }
    }

    public void disconnect(Context context) {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
              .requestEmail()
              .build();
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(context, options);
        GoogleSignInAccount gsa = GoogleSignIn.getAccountForScopes(mReactContext, new Scope(Scopes.FITNESS_ACTIVITY_READ));
        if (gsa.getIdToken() != null && !gsa.isExpired()) {
            Fitness.getConfigClient(mReactContext, gsa).disableFit();

            googleSignInClient.signOut();
        }
    }

    public boolean isAuthorized() {
        if (this.mApiAccount != null && this.mApiAccount.getAccount() != null && !this.mApiAccount.isExpired()) {
            return true;
        } else if (GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this.mReactContext), this.scopes)) {
            return true;
        } else {
            return false;
        }
    }

    protected void stop() {
        if (this.mApiAccount != null && this.mApiAccount.getIdToken() != null && !this.mApiAccount.isExpired()) {
            Fitness.getSensorsClient(mActivity, this.mApiAccount).remove(mStepCounter);
        }
    }


    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }


    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            mAuthInProgress = false;
            if (resultCode == Activity.RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                this.mApiAccount = GoogleSignIn.getAccountForScopes(mReactContext, new Scope(Scopes.FITNESS_ACTIVITY_READ));
                sendEvent(mReactContext, "GoogleFitAuthorizeSuccess", getUserProperties(GoogleSignIn.getAccountForScopes(mReactContext, new Scope(Scopes.FITNESS_ACTIVITY_READ))));
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.e(TAG, "Authorization - Cancel");
                WritableMap map = Arguments.createMap();
                map.putString("message", "" + "Authorization cancelled");
                sendEvent(mReactContext, "GoogleFitAuthorizeFailure", map);
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    public ActivityHistory getActivityHistory() {
        return activityHistory;
    }

    public void setActivityHistory(ActivityHistory activityHistory) {
        this.activityHistory = activityHistory;
    }

    public static class GoogleFitCustomErrorDialig extends ErrorDialogFragment
    {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(AUTH_PENDING);
            return GoogleApiAvailability.getInstance().getErrorDialog(
                    this.getActivity(), errorCode, REQUEST_OAUTH);
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            mAuthInProgress = false;
        }
    }

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        if (getCurrentActivity() != null) {
            // Create a fragment for the error dialog
            GoogleFitCustomErrorDialig dialogFragment = new GoogleFitCustomErrorDialig();
            // Pass the error that should be displayed
            Bundle args = new Bundle();
            args.putInt(AUTH_PENDING, errorCode);
            dialogFragment.setArguments(args);
            dialogFragment.show(mActivity.getFragmentManager(), "errordialog");
        }
    }

    private WritableMap getUserProperties(@NonNull GoogleSignInAccount acct) {
        Uri photoUrl = acct.getPhotoUrl();

        WritableMap user = Arguments.createMap();
        user.putString("id", acct.getId());
        user.putString("name", acct.getDisplayName());
        user.putString("givenName", acct.getGivenName());
        user.putString("familyName", acct.getFamilyName());
        user.putString("email", acct.getEmail());
        user.putString("photo", photoUrl != null ? photoUrl.toString() : null);

        WritableMap params = Arguments.createMap();
        params.putMap("user", user);
        params.putString("idToken", acct.getIdToken());

        WritableArray scopes = Arguments.createArray();
        for (Scope scope : acct.getGrantedScopes()) {
            String scopeString = scope.toString();
            if (scopeString.startsWith("http")) {
                scopes.pushString(scopeString);
            }
        }
        params.putArray("scopes", scopes);
        return params;
    }
}
