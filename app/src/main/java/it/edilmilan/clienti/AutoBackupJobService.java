package it.edilmilan.clienti;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public class AutoBackupJobService extends JobService {
    private static final int JOB_ID = 1207;
    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || scheduler.getPendingJob(JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, AutoBackupJobService.class))
                .setPeriodic(ONE_DAY_MS)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            AutomaticBackupManager.perform(getApplicationContext());
            jobFinished(params, false);
        }, "edil-auto-backup").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
