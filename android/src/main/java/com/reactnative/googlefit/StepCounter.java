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
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

public class StepCounter implements OnDataPointListener
{

    private ReactContext mReactContext;
    private GoogleFitManager googleFitManager;
    private Activity activity;

    private static final String TAG = "StepCounter";


    public StepCounter(ReactContext reactContext, GoogleFitManager googleFitManager, Activity activity) {
        this.mReactContext = reactContext;
        this.googleFitManager = googleFitManager;
        this.activity = activity;
    }

    public void findFitnessDataSources() {

        DataSourcesRequest dataSourceRequest = new DataSourcesRequest.Builder()
                .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA, DataType.TYPE_STEP_COUNT_CUMULATIVE)
                .setDataSourceTypes(DataSource.TYPE_DERIVED)
                .build();

        Fitness.getSensorsClient(mReactContext, googleFitManager.getGoogleAccount())
                .findDataSources(dataSourceRequest)
                .addOnSuccessListener(googleFitManager.getCurrentActivity(), new OnSuccessListener<List<DataSource>>()
                {
                    @Override
                    public void onSuccess(List<DataSource> dataSources) {
                        for (DataSource dataSource : dataSources) {
                            DataType type = dataSource.getDataType();

                            if (DataType.TYPE_STEP_COUNT_DELTA.equals(type)
                                    || DataType.TYPE_STEP_COUNT_CUMULATIVE.equals(type)) {
                                Log.i(TAG, "Register Fitness Listener: " + type);
                                registerFitnessDataListener(dataSource, type);//DataType.TYPE_STEP_COUNT_DELTA);
                            }
                        }
                    }
                });
    }

    private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {

        SensorRequest request = new SensorRequest.Builder()
                .setDataSource(dataSource)
                .setDataType(dataType)
                .setSamplingRate(3, TimeUnit.SECONDS)
                .build();

        Fitness.getSensorsClient(mReactContext, googleFitManager.getGoogleAccount()).add(request, this)
                .addOnCompleteListener(new OnCompleteListener<Void>()
                {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.i(TAG, "SensorApi successfully added");
                        }
                    }
                });
    }


    @Override
    public void onDataPoint(DataPoint dataPoint) {
        DataType type = dataPoint.getDataType();
        Log.i(TAG, "Detected DataPoint type: " + type);

        for (final Field field : type.getFields()) {
            final Value value = dataPoint.getValue(field);
            Log.i(TAG, "Detected DataPoint field: " + field.getName());
            Log.i(TAG, "Detected DataPoint value: " + value);


       /*     activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mReactContext.getApplicationContext(), "Field: " + field.getName() + " Value: " + value, Toast.LENGTH_SHORT).show();
                }
            });*/

            if (type.equals(DataType.TYPE_STEP_COUNT_DELTA)) {
                WritableMap map = Arguments.createMap();
                map.putDouble("steps", value.asInt());
                sendEvent(this.mReactContext, "StepDeltaChangedEvent", map);
            }

            if (type.equals(DataType.TYPE_STEP_COUNT_CUMULATIVE)) {
                WritableMap map = Arguments.createMap();
                map.putDouble("steps", value.asInt());
                sendEvent(this.mReactContext, "StepChangedEvent", map);
            }

        }
    }


    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
