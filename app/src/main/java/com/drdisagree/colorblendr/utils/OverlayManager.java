package com.drdisagree.colorblendr.utils;

import android.os.RemoteException;

import com.drdisagree.colorblendr.ColorBlendr;
import com.drdisagree.colorblendr.service.IRootServiceProvider;
import com.drdisagree.colorblendr.utils.fabricated.FabricatedOverlayResource;

import java.util.ArrayList;
import java.util.Collections;

public class OverlayManager {

    private static final String TAG = OverlayManager.class.getSimpleName();
    private static final IRootServiceProvider mRootService = ColorBlendr.getRootService();

    public static void enableOverlay(String packageName) {
        try {
            mRootService.enableOverlay(Collections.singletonList(packageName));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static void disableOverlay(String packageName) {
        try {
            mRootService.disableOverlay(Collections.singletonList(packageName));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static boolean isOverlayInstalled(String packageName) {
        try {
            return mRootService.isOverlayInstalled(packageName);
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isOverlayEnabled(String packageName) {
        try {
            return mRootService.isOverlayEnabled(packageName);
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void uninstallOverlayUpdates(String packageName) {
        try {
            mRootService.uninstallOverlayUpdates(packageName);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static void registerFabricatedOverlay(FabricatedOverlayResource fabricatedOverlay) {
        try {
            mRootService.registerFabricatedOverlay(fabricatedOverlay);
            mRootService.enableOverlayWithIdentifier(Collections.singletonList(fabricatedOverlay.overlayName));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static void unregisterFabricatedOverlay(String packageName) {
        try {
            mRootService.unregisterFabricatedOverlay(packageName);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static void applyFabricatedColors(ArrayList<ArrayList<Integer>> palette) {
        FabricatedOverlayResource accentOverlay = new FabricatedOverlayResource("accent", "android");
        FabricatedOverlayResource neutralOverlay = new FabricatedOverlayResource("neutral", "android");
        String[][] colorNames = ColorUtil.getColorNames();

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 13; j++) {
                if (i < 3) {
                    accentOverlay.setColor(colorNames[i][j], palette.get(i).get(j));
                } else {
                    neutralOverlay.setColor(colorNames[i][j], palette.get(i).get(j));
                }
            }
        }

        OverlayManager.registerFabricatedOverlay(accentOverlay);
        OverlayManager.registerFabricatedOverlay(neutralOverlay);
    }
}
