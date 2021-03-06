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

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.RecordingClient;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;

import androidx.annotation.Nullable;


public class RecordingApi
{

    private ReactContext reactContext;
    private GoogleFitManager googleFitManager;

    private static final String TAG = "RecordingApi";

    @Nullable
    public static DataType[] getDataType(String dataTypeName) {
        switch (dataTypeName) {
            case "step":
                return new DataType[]{DataType.TYPE_STEP_COUNT_CUMULATIVE, DataType.TYPE_STEP_COUNT_DELTA};
            case "distance":
                return new DataType[]{DataType.TYPE_DISTANCE_DELTA};
            case "activity":
                return new DataType[]{DataType.TYPE_ACTIVITY_SEGMENT};
            default:
                Log.v(TAG, "Unknown data type " + dataTypeName);
                return null;
        }
    }

    public static String getEventName(String dataTypeName) {
        return dataTypeName.toUpperCase() + "_RECORDING";
    }

    public RecordingApi(ReactContext reactContext, GoogleFitManager googleFitManager) {

        this.reactContext = reactContext;
        this.googleFitManager = googleFitManager;

    }

    public void subscribe(ReadableArray dataTypes) {
        ArrayList<String> dataTypesList = new ArrayList<String>();

        for (Object type : dataTypes.toArrayList()) {
            dataTypesList.add(type.toString());
        }


        for (String dataTypeName : dataTypesList) {
            DataType[] dataType = getDataType(dataTypeName);

            // Just skip unknown data types
            if (dataType == null) {
                continue;
            }

            final String eventName = getEventName(dataTypeName);

            RecordingClient client = Fitness.getRecordingClient(reactContext, googleFitManager.getGoogleAccount());

            Task<Void> task = client.subscribe(dataType[0]);
            if (dataType.length > 1) {
                for (int i = 1; i < dataType.length; i++) {
                    DataType d = dataType[i];
                    client.subscribe(d);
                }
            }
            task.addOnCompleteListener(googleFitManager.getCurrentActivity(), new OnCompleteListener<Void>()
            {
                @Override
                public void onComplete(@androidx.annotation.NonNull Task<Void> task) {
                    WritableMap map = Arguments.createMap();

                    map.putString("type", eventName);

                    if (task.isSuccessful()) {
                        map.putBoolean("recording", true);
                        Log.i(TAG, "RecordingAPI - Connected");
                        sendEvent(reactContext, eventName, map);
                    } else {
                        map.putBoolean("recording", false);
                        Log.i(TAG, "RecordingAPI - Error connecting");
                        sendEvent(reactContext, eventName, map);
                    }
                }
            });
        }
    }


    private void sendEvent(ReactContext reactContext,
                           String eventName, @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
