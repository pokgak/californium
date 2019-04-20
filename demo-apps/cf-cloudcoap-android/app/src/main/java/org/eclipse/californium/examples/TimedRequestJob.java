/*******************************************************************************
 * Copyright (c) 2019 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - Initial creation
 ******************************************************************************/
package org.eclipse.californium.examples;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.eclipse.californium.examples.coaputil.CoapArguments;
import org.eclipse.californium.examples.coaputil.CoapTaskResult;
import org.eclipse.californium.examples.coaputil.SetupCalifornium;

@TargetApi(Build.VERSION_CODES.M)
public class TimedRequestJob extends JobService {

    private static final String LOG_TAG = "job";

    private CoapGetTask task;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(LOG_TAG, "start job: " + params.getJobId());
        Context applicationContext = getApplicationContext();
        JobManager.createNext(applicationContext, params.getJobId());
        int loops = 20;
        CoapContext.getInstance().attach(applicationContext);
        String connectivityType = getConnectivityType();
        while (connectivityType == null && loops > 0) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
            --loops;
            connectivityType = getConnectivityType();
        }
        if (connectivityType == null) {
            jobFinished(params, true);
            return false;
        }
        CoapArguments arguments = CoapContext.getInstance().setup(SetupCalifornium.InitMode.INIT).build();
        Log.i(LOG_TAG, "request: " + arguments.uriString);
        task = new CoapGetTask(applicationContext, params);
        CoapContext.getInstance().executeRequest(task, arguments);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        JobManager.ensureNext(getApplicationContext(), params.getJobId());
        if (task != null) {
            if (getConnectivityType() == null) {
                task.cancel("job stopped, connectivity lost", true);
            } else {
                task.cancel("job stopped", true);
            }
            task = null;
        }
        return true;
    }

    private String getConnectivityType() {
        return CoapContext.getInstance().getConnectivityType();
    }

    class CoapGetTask extends CoapRequestTask {
        private final JobParameters params;

        private CoapGetTask(Context applicationContext, JobParameters params) {
            super(applicationContext);
            this.params = params;
        }

        @Override
        protected void onPostExecute(CoapTaskResult result) {
            String connectivity = getConnectivityType();
            super.onPostExecute(result);
            CoapContext.getInstance().detach();
            if (params != null) {
                jobFinished(params, !isSuccess() && connectivity == null);
            }
        }
    }
}
