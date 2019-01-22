package expo.modules.backgroundfetch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.SystemClock;

import java.util.Map;

import expo.core.interfaces.LifecycleEventListener;
import expo.interfaces.taskManager.TaskConsumer;
import expo.interfaces.taskManager.TaskConsumerInterface;
import expo.interfaces.taskManager.TaskExecutionCallback;
import expo.interfaces.taskManager.TaskInterface;
import expo.interfaces.taskManager.TaskManagerUtilsInterface;

public class BackgroundFetchTaskConsumer extends TaskConsumer implements TaskConsumerInterface, LifecycleEventListener {
  private static final String TAG = BackgroundFetchTaskConsumer.class.getSimpleName();
  private static final int DEFAULT_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes

  private TaskInterface mTask;
  private PendingIntent mPendingIntent;

  public BackgroundFetchTaskConsumer(Context context, TaskManagerUtilsInterface taskManagerUtils) {
    super(context, taskManagerUtils);
  }

  //region TaskConsumerInterface

  @Override
  public String taskType() {
    return "backgroundFetch";
  }

  @Override
  public void didRegister(TaskInterface task) {
    mTask = task;
  }

  @Override
  public void didUnregister() {
    // Stop an alarm.
    stopAlarm();

    // Cancel pending intent.
    getTaskManagerUtils().cancelTaskIntent(getContext(), mTask.getAppId(), mTask.getName());

    mTask = null;
    mPendingIntent = null;
  }

  @Override
  public void didReceiveBroadcast(Intent intent) {
    Context context = getContext();
    TaskManagerUtilsInterface taskManagerUtils = getTaskManagerUtils();

    if (context != null) {
      taskManagerUtils.scheduleJob(context, mTask, new PersistableBundle());
    }
  }

  @Override
  public boolean didExecuteJob(final JobService jobService, final JobParameters params) {
    mTask.execute(null, null, new TaskExecutionCallback() {
      @Override
      public void onFinished(Map<String, Object> response) {
        jobService.jobFinished(params, false);
      }
    });

    // Returning `true` indicates that the job is still running, but in async mode.
    // In that case we're obligated to call `jobService.jobFinished` as soon as the async block finishes.
    return true;
  }

  //endregion
  //region private methods

  private AlarmManager getAlarmManager() {
    Context context = getContext();
    return context != null ? (AlarmManager) context.getSystemService(Context.ALARM_SERVICE) : null;
  }

  private int getIntervalMs() {
    Map<String, Object> options = mTask != null ? mTask.getOptions() : null;

    if (options != null && options.containsKey("interval")) {
      // Given option is in seconds.
      // It doesn't make much sense to offer millisecond accuracy since the interval is inexact.
      return ((Number) options.get("interval")).intValue() * 1000;
    }
    return DEFAULT_INTERVAL_MS;
  }

  private void startAlarm() {
    Context context = getContext();
    AlarmManager alarmManager = getAlarmManager();

    if (alarmManager == null) {
      return;
    }

    int interval = getIntervalMs();
    TaskManagerUtilsInterface taskManagerUtils = getTaskManagerUtils();

    mPendingIntent = taskManagerUtils.createTaskIntent(context, mTask);

    // Cancel existing alarm for this pending intent.
    alarmManager.cancel(mPendingIntent);

    alarmManager.setInexactRepeating(
        AlarmManager.ELAPSED_REALTIME_WAKEUP,
        SystemClock.elapsedRealtime() + interval,
        interval,
        mPendingIntent
    );
  }

  private void stopAlarm() {
    AlarmManager alarmManager = getAlarmManager();

    if (alarmManager != null && mPendingIntent != null) {
      alarmManager.cancel(mPendingIntent);
    }
  }

  //endregion
  //region LifecycleEventListener

  @Override
  public void onHostResume() {
    stopAlarm();
  }

  @Override
  public void onHostPause() {
    startAlarm();
  }

  @Override
  public void onHostDestroy() {
    // nothing - the alarm should continue to work when the activity is terminated.
  }

  //endregion
}
