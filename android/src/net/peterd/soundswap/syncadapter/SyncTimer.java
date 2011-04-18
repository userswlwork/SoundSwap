package net.peterd.soundswap.syncadapter;

import java.util.concurrent.TimeUnit;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SyncTimer extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
      scheduleRepeatingSyncBroadcast(context);
    } else {
      sync(context);
    }
  }

  private void sync(Context context) {
    context.startService(new Intent(context, SyncService.class));
  }

  private void scheduleRepeatingSyncBroadcast(Context context) {
    AlarmManager alarmManager =
        (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME,
        System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15 * 60),
        AlarmManager.INTERVAL_HOUR,
        PendingIntent.getBroadcast(context,
            0,
            new Intent(context, SyncTimer.class),
            PendingIntent.FLAG_UPDATE_CURRENT));
  }
}
