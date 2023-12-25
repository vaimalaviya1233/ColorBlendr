package com.drdisagree.colorblendr.service;

import static com.drdisagree.colorblendr.common.Const.FABRICATED_OVERLAY_SOURCE_PACKAGE;
import static com.drdisagree.colorblendr.common.Const.SYSTEMUI_PACKAGE;

import android.app.IActivityManager;
import android.app.IProcessObserver;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManagerTransaction;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.drdisagree.colorblendr.extension.MethodInterface;
import com.drdisagree.colorblendr.utils.fabricated.FabricatedOverlayEntry;
import com.drdisagree.colorblendr.utils.fabricated.FabricatedOverlayResource;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.Utils;
import com.topjohnwu.superuser.ipc.RootService;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import rikka.shizuku.SystemServiceHelper;

@SuppressWarnings({"all"})
public class RootServiceProvider extends RootService {

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        return new RootServiceProviderImpl();
    }

    static class RootServiceProviderImpl extends IRootServiceProvider.Stub {

        private static final String TAG = RootServiceProviderImpl.class.getSimpleName();
        private static Context context = Utils.getContext();
        private static final UserHandle currentUser;
        private static final int currentUserId;
        private static IOverlayManager mOMS;
        private static Class<?> oiClass;
        private static Class<?> foClass;
        private static Class<?> fobClass;
        private static Class<?> omtbClass;
        private static int SystemUI_UID = -1;
        private static IActivityManager mActivityManager;
        private static MethodInterface onSystemUIRestartedListener;

        private IProcessObserver.Stub processListener = new IProcessObserver.Stub() {
            @Override
            public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) throws RemoteException {
                // Do nothing
            }

            @Override
            public void onForegroundServicesChanged(int pid, int uid, int serviceTypes) throws RemoteException {
                // Do nothing
            }

            @Override
            public void onProcessDied(int pid, int uid) {
                if (uid == getSystemUI_UID() && onSystemUIRestartedListener != null) {
                    onSystemUIRestartedListener.run();
                }
            }
        };

        static {
            currentUser = getCurrentUser();
            currentUserId = getCurrentUserId();

            if (mOMS == null) {
                mOMS = IOverlayManager.Stub.asInterface(SystemServiceHelper.getSystemService("overlay"));
            }

            if (mActivityManager == null) {
                mActivityManager = IActivityManager.Stub.asInterface(SystemServiceHelper.getSystemService("activity"));
            }

            try {
                SystemUI_UID = context.getPackageManager().getPackageUid(SYSTEMUI_PACKAGE, 0);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "static: ", e);
            }

            try {
                if (oiClass == null) {
                    oiClass = Class.forName("android.content.om.OverlayIdentifier");
                }
                if (foClass == null) {
                    foClass = Class.forName("android.content.om.FabricatedOverlay");
                }
                if (fobClass == null) {
                    fobClass = Class.forName("android.content.om.FabricatedOverlay$Builder");
                }
                if (omtbClass == null) {
                    omtbClass = Class.forName("android.content.om.OverlayManagerTransaction$Builder");
                }
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "static: ", e);
            }
        }

        private static UserHandle getCurrentUser() {
            return Process.myUserHandle();
        }

        private static Integer getCurrentUserId() {
            try {
                return (Integer) UserHandle.class.getMethod("getIdentifier").invoke(currentUser);
            } catch (NoSuchMethodException | IllegalAccessException |
                     InvocationTargetException exception) {
                return 0;
            }
        }

        private static IOverlayManager getOMS() {
            if (mOMS == null) {
                mOMS = IOverlayManager.Stub.asInterface(SystemServiceHelper.getSystemService("overlay"));
            }
            return mOMS;
        }

        private static IActivityManager getAM() {
            if (mActivityManager == null) {
                mActivityManager = IActivityManager.Stub.asInterface(SystemServiceHelper.getSystemService("activity"));
            }
            return mActivityManager;
        }

        private static int getSystemUI_UID() {
            if (SystemUI_UID == -1) {
                try {
                    SystemUI_UID = context.getPackageManager().getPackageUid(SYSTEMUI_PACKAGE, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    // It's impossible to get here, but just in case
                    Log.e(TAG, "getSystemUI_UID: ", e);
                }
            }
            return SystemUI_UID;
        }

        @Override
        public boolean isRooted() throws RemoteException {
            return Shell.cmd("whoami").exec().getOut().get(0).equals("root");
        }

        /**
         * Listener to notify when SystemUI restarts.
         */
        @Override
        public void setupSystemUIRestartListener() throws RemoteException {
            getAM().registerProcessObserver(processListener);
        }

        @Override
        public void runOnSystemUIRestarted(MethodInterface method) throws RemoteException {
            onSystemUIRestartedListener = method;
        }

        /**
         * Return true if an overlay package is installed.
         */
        @Override
        public boolean isOverlayInstalled(String packageName) throws RemoteException {
            return getOMS().getOverlayInfo(packageName, currentUserId) != null;
        }

        /**
         * Return true if an overlay package is enabled.
         */
        @Override
        public boolean isOverlayEnabled(String packageName) throws RemoteException {
            OverlayInfo overlay = getOMS().getOverlayInfo(packageName, currentUserId);

            try {
                Field isEnabled = overlay.getClass().getDeclaredField("isEnabled");
                isEnabled.setAccessible(true);
                return isEnabled.getBoolean(overlay);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
                return false;
            }
        }

        /**
         * Request that an overlay package be enabled when possible to do so.
         */
        @Override
        public void enableOverlay(List<String> packages) {
            for (String p : packages) {
                switchOverlay(p, true);
            }
        }

        /**
         * Request that an overlay package be enabled when possible to do so.
         */
        @Override
        public void enableOverlayWithIdentifier(List<String> packages) throws RemoteException {
            for (String p : packages) {
                OverlayIdentifier identifier = generateOverlayIdentifier(p);
                switchOverlayWithIdentifier(identifier, true);
            }
        }

        /**
         * Request that an overlay package is enabled and any other overlay packages with the same
         * target package are disabled.
         */
        @Override
        public boolean enableOverlayExclusive(String packageName) throws RemoteException {
            return getOMS().setEnabledExclusive(packageName, true, currentUserId);
        }

        /**
         * Request that an overlay package is enabled and any other overlay packages with the same
         * target package and category are disabled.
         */
        @Override
        public boolean enableOverlayExclusiveInCategory(String packageName) throws RemoteException {
            return getOMS().setEnabledExclusiveInCategory(packageName, currentUserId);
        }

        /**
         * Request that an overlay package be disabled when possible to do so.
         */
        @Override
        public void disableOverlay(List<String> packages) throws RemoteException {
            for (String p : packages) {
                switchOverlay(p, false);
            }
        }

        /**
         * Request that an overlay package be disabled when possible to do so.
         */
        @Override
        public void disableOverlayWithIdentifier(List<String> packages) throws RemoteException {
            for (String p : packages) {
                OverlayIdentifier identifier = generateOverlayIdentifier(p);
                switchOverlayWithIdentifier(identifier, false);
            }
        }

        /**
         * Registers the fabricated overlay with the overlay manager so it can be enabled and
         * disabled for any user.
         * <p>
         * The fabricated overlay is initialized in a disabled state. If an overlay is re-registered
         * the existing overlay will be replaced by the newly registered overlay and the enabled
         * state of the overlay will be left unchanged if the target package and target overlayable
         * have not changed.
         *
         * @param overlay the overlay to register with the overlay manager
         */
        @Override
        public void registerFabricatedOverlay(FabricatedOverlayResource overlay) throws RemoteException {
            try {
                Object fobInstance = fobClass.getConstructor(
                        String.class,
                        String.class,
                        String.class
                ).newInstance(
                        overlay.sourcePackage,
                        overlay.overlayName,
                        overlay.targetPackage
                );

                Method setResourceValueMethod = fobClass.getMethod(
                        "setResourceValue",
                        String.class,
                        int.class,
                        int.class
                );

                for (Map.Entry<String, FabricatedOverlayEntry> entry : overlay.getEntries().entrySet()) {
                    FabricatedOverlayEntry overlayEntry = entry.getValue();
                    setResourceValueMethod.invoke(
                            fobInstance,
                            overlayEntry.getResourceName(),
                            overlayEntry.getResourceType(),
                            overlayEntry.getResourceValue()
                    );
                }

                Object foInstance = fobClass.getMethod(
                        "build"
                ).invoke(fobInstance);

                Object omtbInstance = omtbClass.newInstance();

                omtbClass.getMethod(
                        "registerFabricatedOverlay",
                        foClass
                ).invoke(
                        omtbInstance,
                        foInstance
                );

                Object omtInstance = omtbClass.getMethod(
                        "build"
                ).invoke(omtbInstance);

                commit(omtInstance);
            } catch (Exception e) {
                Log.e(TAG, "registerFabricatedOverlay: ", e);
            }
        }

        /**
         * Disables and removes the overlay from the overlay manager for all users.
         *
         * @param packageName the package name of the overlay to disable and remove
         */
        @Override
        public void unregisterFabricatedOverlay(String packageName) throws RemoteException {
            try {
                OverlayIdentifier overlay = generateOverlayIdentifier(packageName);
                if (overlay == null) {
                    return;
                }

                Object omtbInstance = omtbClass.newInstance();

                omtbClass.getMethod(
                        "unregisterFabricatedOverlay",
                        oiClass
                ).invoke(
                        omtbInstance,
                        overlay
                );

                Object omtInstance = omtbClass.getMethod(
                        "build"
                ).invoke(omtbInstance);

                commit(omtInstance);
            } catch (Exception e) {
                Log.e(TAG, "unregisterFabricatedOverlay: ", e);
            }
        }

        /**
         * Change the priority of the given overlay to the highest priority relative to
         * the other overlays with the same target and user.
         */
        @Override
        public boolean setHighestPriority(String packageName) throws RemoteException {
            return (boolean) getOMS().setHighestPriority(packageName, currentUserId);
        }

        /**
         * Change the priority of the given overlay to the lowest priority relative to
         * the other overlays with the same target and user.
         */
        @Override
        public boolean setLowestPriority(String packageName) throws RemoteException {
            return (boolean) getOMS().setLowestPriority(packageName, currentUserId);
        }

        @Override
        public OverlayIdentifier generateOverlayIdentifier(String packageName) throws RemoteException {
            return generateOverlayIdentifier(packageName, FABRICATED_OVERLAY_SOURCE_PACKAGE);
        }

        private static OverlayIdentifier generateOverlayIdentifier(String packageName, String sourcePackage) {
            try {
                return (OverlayIdentifier) oiClass.getConstructor(
                        String.class,
                        String.class
                ).newInstance(
                        sourcePackage,
                        packageName
                );
            } catch (Exception e) {
                Log.e(TAG, "generateOverlayIdentifier: ", e);
                return null;
            }
        }

        private void switchOverlay(String packageName, boolean enable) {
            try {
                getOMS().setEnabled(packageName, enable, currentUserId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void switchOverlayWithIdentifier(OverlayIdentifier identifier, boolean enable) {
            try {
                Object omtbInstance = omtbClass.newInstance();

                omtbClass.getMethod(
                        "setEnabled",
                        oiClass,
                        boolean.class,
                        int.class
                ).invoke(
                        omtbInstance,
                        identifier,
                        enable,
                        currentUserId
                );

                Object omtInstance = omtbClass.getMethod(
                        "build"
                ).invoke(omtbInstance);

                commit(omtInstance);
            } catch (Exception e) {
                Log.e(TAG, "switchOverlayWithIdentifier: ", e);
            }
        }

        /**
         * Uninstall any overlay updates for the given package name.
         */
        @Override
        public void uninstallOverlayUpdates(String packageName) {
            runCommand(Collections.singletonList("pm uninstall " + packageName));
        }

        /**
         * Restart systemui immediately.
         */
        @Override
        public void restartSystemUI() throws RemoteException {
            runCommand(Collections.singletonList("killall com.android.systemui"));
        }

        /**
         * Run list of commands as root.
         */
        @Override
        public String[] runCommand(List<String> command) {
            return Shell.cmd(command.toArray(new String[0])).exec().getOut().toArray(new String[0]);
        }

        /**
         * Run method interface as root.
         */
        @Override
        public void runWithRoot(MethodInterface method) {
            method.run();
        }

        private void commit(Object transaction) throws Exception {
            getOMS().commit((OverlayManagerTransaction) transaction);
        }
    }
}
