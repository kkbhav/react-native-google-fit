/**
 * Copyright (c) 2017-present, Stanislav Doskalenko - doskalenko.s@gmail.com
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 *
 **/

package com.reactnative.googlefit;

import android.os.AsyncTask;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataDeleteRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.fitness.data.HealthDataTypes;
import com.google.android.gms.fitness.data.HealthFields;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class HeartrateHistory {

    private ReactContext mReactContext;
    private GoogleFitManager googleFitManager;
    private DataSet Dataset;
    private DataType dataType;

    private static final String TAG = "Weights History";

    public HeartrateHistory(ReactContext reactContext, GoogleFitManager googleFitManager, DataType dataType){
        this.mReactContext = reactContext;
        this.googleFitManager = googleFitManager;
        this.dataType = dataType;
    }

    public HeartrateHistory(ReactContext reactContext, GoogleFitManager googleFitManager){
        this(reactContext, googleFitManager, DataType.TYPE_WEIGHT);
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public ReadableArray getHistory(long startTime, long endTime) throws Exception {
        DateFormat dateFormat = DateFormat.getDateInstance();
        // for height we need to take time, since GoogleFit foundation - https://stackoverflow.com/questions/28482176/read-the-height-in-googlefit-in-android

        DataReadRequest.Builder readRequestBuilder = new DataReadRequest.Builder()
                .read(this.dataType)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS);
        if (this.dataType == HealthDataTypes.TYPE_BLOOD_PRESSURE) {
            readRequestBuilder.bucketByTime(1, TimeUnit.DAYS);
        } else {
            readRequestBuilder.setLimit(5); // need only one height, since it's unchangable
        }

        DataReadRequest readRequest = readRequestBuilder.build();

        Task<DataReadResponse> task = Fitness.getHistoryClient(mReactContext, googleFitManager.getGoogleAccount()).readData(readRequest);
        DataReadResponse dataReadResult = Tasks.await(task, 1, TimeUnit.MINUTES);

        WritableArray map = Arguments.createArray();

        //Used for aggregated data
        if (dataReadResult.getBuckets().size() > 0) {
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    processDataSet(dataSet, map);
                }
            }
        }
        //Used for non-aggregated data
        else if (dataReadResult.getDataSets().size() > 0) {
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                processDataSet(dataSet, map);
            }
        }
        return map;
    }

    public boolean save(ReadableMap sample) {
        this.Dataset = createDataForRequest(
                this.dataType,    // for height, it would be DataType.TYPE_HEIGHT
                DataSource.TYPE_RAW,
                sample.getDouble("value"),                  // weight in kgs, height in metrs
                (long)sample.getDouble("date"),              // start time
                (long)sample.getDouble("date"),                // end time
                TimeUnit.MILLISECONDS                // Time Unit, for example, TimeUnit.MILLISECONDS
        );
        new InsertAndVerifyDataTask(this.Dataset).execute();

        return true;
    }

    public boolean delete(ReadableMap sample) {
        long endTime = (long) sample.getDouble("endTime");
        long startTime = (long) sample.getDouble("startTime");
        new DeleteDataTask(startTime, endTime, this.dataType).execute();
        return true;
    }

    //Async fit data delete
    private class DeleteDataTask extends AsyncTask<Void, Void, Void> {

        long startTime;
        long endTime;
        DataType dataType;

        DeleteDataTask(long startTime, long endTime, DataType dataType) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                DataDeleteRequest request = new DataDeleteRequest.Builder()
                        .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                        .addDataType(this.dataType)
                        .build();

                Task<Void> task = Fitness.getHistoryClient(mReactContext, googleFitManager.getGoogleAccount()).deleteData(request);
                Tasks.await(task, 1, TimeUnit.MINUTES);

                if (task.isSuccessful()) {
                    Log.w(TAG, "+Successfully deleted data.");
                } else {
                    Log.w(TAG, "+Failed to delete data.");
                }
            } catch (Exception e) {
                Log.w(TAG, "Error occurred: "+e.getMessage());
                e.printStackTrace();
            }

            return null;
        }
    }


    //Async fit data insert
    private class InsertAndVerifyDataTask extends AsyncTask<Void, Void, Void> {

        private DataSet Dataset;

        InsertAndVerifyDataTask(DataSet dataset) {
            this.Dataset = dataset;
        }

        protected Void doInBackground(Void... params) {
            try {
                // Create a new dataset and insertion request.
                DataSet dataSet = this.Dataset;

                // [START insert_dataset]
                // Then, invoke the History API to insert the data and await the result, which is
                // possible here because of the {@link AsyncTask}. Always include a timeout when calling
                // await() to prevent hanging that can occur from the service being shutdown because
                // of low memory or other conditions.
                //Log.i(TAG, "Inserting the dataset in the History API.");

                Task<Void> task = Fitness.getHistoryClient(mReactContext, googleFitManager.getGoogleAccount()).insertData(dataSet);
                Tasks.await(task, 1, TimeUnit.MINUTES);

                // Before querying the data, check to see if the insertion succeeded.
                if (!task.isSuccessful()) {
                    //Log.i(TAG, "There was a problem inserting the dataset.");
                    return null;
                }

                //Log.i(TAG, "Data insert was successful!");
            } catch (Exception e) {
                Log.w(TAG, "Error occurred: "+e.getMessage());
                e.printStackTrace();
            }

            return null;
        }
    }

    /**
     * This method creates a dataset object to be able to insert data in google fit
     * @param dataType DataType Fitness Data Type object
     * @param dataSourceType int Data Source Id. For example, DataSource.TYPE_RAW
     * @param value Object Values for the fitness data. They must be int or float
     * @param startTime long Time when the fitness activity started
     * @param endTime long Time when the fitness activity finished
     * @param timeUnit TimeUnit Time unit in which period is expressed
     * @return
     */
    private DataSet createDataForRequest(DataType dataType, int dataSourceType, Double value,
                                         long startTime, long endTime, TimeUnit timeUnit) {
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(GoogleFitPackage.PACKAGE_NAME)
                .setDataType(dataType)
                .setType(dataSourceType)
                .build();

        DataPoint.Builder builder = DataPoint.builder(dataSource).setTimeInterval(startTime, endTime, timeUnit);

        float f1 = Float.valueOf(value.toString());
        builder.setFloatValues(f1);

        DataSet dataSet = DataSet.builder(dataSource).add(builder.build()).build();

        return dataSet;
    }

    private void processDataSet(DataSet dataSet, WritableArray map) {

        //Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
        Format formatter = new SimpleDateFormat("EEE");
//        WritableMap stepMap = Arguments.createMap();

        for (DataPoint dp : dataSet.getDataPoints()) {
            WritableMap stepMap = Arguments.createMap();
            String day = formatter.format(new Date(dp.getStartTime(TimeUnit.MILLISECONDS)));
            int i = 0;

            for(Field field : dp.getDataType().getFields()) {
                i++;
                if (i > 1) continue;
                stepMap.putString("day", day);
                stepMap.putDouble("startDate", dp.getStartTime(TimeUnit.MILLISECONDS));
                stepMap.putDouble("endDate", dp.getEndTime(TimeUnit.MILLISECONDS));
                if (this.dataType == HealthDataTypes.TYPE_BLOOD_PRESSURE) {
                    stepMap.putDouble("value2", dp.getValue(HealthFields.FIELD_BLOOD_PRESSURE_DIASTOLIC).asFloat());
                    stepMap.putDouble("value", dp.getValue(HealthFields.FIELD_BLOOD_PRESSURE_SYSTOLIC).asFloat());
                } else {
                  stepMap.putDouble("value", dp.getValue(field).asFloat());
                }


                map.pushMap(stepMap);
            }
        }
    }

}
