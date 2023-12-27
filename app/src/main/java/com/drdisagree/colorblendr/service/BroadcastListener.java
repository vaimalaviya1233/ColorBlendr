package com.drdisagree.colorblendr.service;

import static com.drdisagree.colorblendr.common.Const.MONET_LAST_UPDATED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.drdisagree.colorblendr.ColorBlendr;
import com.drdisagree.colorblendr.common.Const;
import com.drdisagree.colorblendr.config.RPrefs;
import com.drdisagree.colorblendr.extension.MethodInterface;
import com.drdisagree.colorblendr.provider.RootServiceProvider;
import com.drdisagree.colorblendr.utils.AppUtil;
import com.drdisagree.colorblendr.utils.ColorUtil;

public class BroadcastListener extends BroadcastReceiver {

    private static final String TAG = BroadcastListener.class.getSimpleName();

    @SuppressWarnings("deprecation")
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received intent: " + intent.getAction());

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())
        ) {
            if (AppUtil.permissionsGranted(context)) {
                if (!Const.isBackgroundServiceRunning) {
                    context.startService(new Intent(ColorBlendr.getAppContext(), BackgroundService.class));
                }
            }

            if (!RootServiceProvider.isRootServiceBound()) {
                if (Const.getWorkingMethod() == Const.WORK_METHOD.ROOT) {
                    RootServiceProvider rootServiceProvider = new RootServiceProvider(context);
                    rootServiceProvider.runOnSuccess(new MethodInterface() {
                        @Override
                        public void run() {
                            if (Math.abs(RPrefs.getLong(MONET_LAST_UPDATED, 0) - System.currentTimeMillis()) >= 5000) {
                                RPrefs.putLong(MONET_LAST_UPDATED, System.currentTimeMillis());
                                new Handler(Looper.getMainLooper()).postDelayed(() -> ColorUtil.applyFabricatedColors(context), 3000);
                            }
                        }
                    });
                    rootServiceProvider.startRootService();
                }
            }
        }

        if (Intent.ACTION_WALLPAPER_CHANGED.equals(intent.getAction()) ||
                Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())
        ) {
            if (Math.abs(RPrefs.getLong(MONET_LAST_UPDATED, 0) - System.currentTimeMillis()) >= 5000) {
                RPrefs.putLong(MONET_LAST_UPDATED, System.currentTimeMillis());
                new Handler(Looper.getMainLooper()).postDelayed(() -> ColorUtil.applyFabricatedColors(context), 3000);
            }
        }
    }
}
