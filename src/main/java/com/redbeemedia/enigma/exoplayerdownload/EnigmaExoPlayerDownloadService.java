package com.redbeemedia.enigma.exoplayerdownload;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.scheduler.PlatformScheduler;
import com.google.android.exoplayer2.ui.DownloadNotificationHelper;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;

import java.util.List;
import java.util.Objects;

public class EnigmaExoPlayerDownloadService extends DownloadService {
  /*package-protected*/ static final int STOP_REASON_PAUSED = 1;

  private static final String CHANNEL_ID = "download_channel";
  private static final int JOB_ID = 1;
  private static final int FOREGROUND_NOTIFICATION_ID = 1;

  private static int nextNotificationId = FOREGROUND_NOTIFICATION_ID + 1;

  private DownloadNotificationHelper notificationHelper;

  public EnigmaExoPlayerDownloadService() {
    super(
        FOREGROUND_NOTIFICATION_ID
    );
    nextNotificationId = FOREGROUND_NOTIFICATION_ID + 1;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    notificationHelper = new DownloadNotificationHelper(this, CHANNEL_ID);
  }

  @Override
  protected DownloadManager getDownloadManager() {
    return ExoPlayerDownloadContext.getDownloadManager();
  }

  @SuppressLint("MissingPermission")
  @Override
  protected PlatformScheduler getScheduler() {
    if(Util.SDK_INT >= 21 && canUsePlatformScheduler()) {
      return new PlatformScheduler(this, JOB_ID);
    } else {
      return null;
    }
  }

  @Override
  protected Notification getForegroundNotification(List<Download> downloads, int notMetRequirements) {
    return notificationHelper.buildProgressNotification(
            this.getApplicationContext(), R.drawable.ic_download, /* contentIntent= */ null, /* message= */ null, downloads);
  }

  private boolean canUsePlatformScheduler() {
    try {
      PackageInfo packageInfo = getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES);

      { //Check permissions
        String[] permissions = packageInfo.requestedPermissions;
        if(permissions == null) {
          return false;
        }
        boolean hasReceiveBootCompletedPermissionDeclared = false;
        boolean hasForegroundServicePermissionDeclared = false;

        for (String permissionName : permissions) {
          if (Objects.equals(permissionName, "android.permission.RECEIVE_BOOT_COMPLETED")) {
            hasReceiveBootCompletedPermissionDeclared = true;
          } else if (Objects.equals(permissionName, "android.permission.FOREGROUND_SERVICE")) {
            hasForegroundServicePermissionDeclared = true;
          }
        }

        if (!hasReceiveBootCompletedPermissionDeclared
                || !hasForegroundServicePermissionDeclared) {
          return false;
        }
      }

      { //Check services
        ServiceInfo[] services = packageInfo.services;
        if(services == null) {
          return false;
        }

        boolean hasPlatformSchedulerServiceDeclared = false;

        for(ServiceInfo serviceInfo : services) {
          if(Objects.equals(serviceInfo.name, PlatformScheduler.PlatformSchedulerService.class.getName())) {
            hasPlatformSchedulerServiceDeclared = true;
          }
        }

        if(!hasPlatformSchedulerServiceDeclared) {
          return false;
        }
      }

      return true;
    } catch (PackageManager.NameNotFoundException e) {
      Log.e("DownloadService","Failed to deduce if PlatformScheduler can be used", e);
      return false;
    }
  }

}
