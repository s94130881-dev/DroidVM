// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright DroidVM contributors
// Additional permissions apply; see ADDITIONAL-PERMISSIONS in the repository root.

package cn.classfun.droidvm;

import android.app.Application;
import android.content.pm.PackageManager;
import android.util.Log;

import com.google.android.material.color.DynamicColors;

import cn.classfun.droidvm.lib.daemon.DaemonConnection;
import cn.classfun.droidvm.lib.daemon.VMEventHandler;
import cn.classfun.droidvm.lib.store.base.DataStore;
import cn.classfun.droidvm.lib.store.disk.DiskStore;
import cn.classfun.droidvm.lib.store.network.NetworkStore;
import cn.classfun.droidvm.lib.store.vm.VMStore;
import cn.classfun.droidvm.lib.ui.ImeInsetsApplier;
import cn.classfun.droidvm.lib.utils.ThreadUtils;

import rikka.shizuku.Shizuku;

public final class DroidVMApp extends Application {

    private static final String TAG = "DroidVMApp";

    public static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;

    private VMEventHandler vmEventHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * Material Dynamic Colors
         */
        DynamicColors.applyToActivitiesIfAvailable(this);

        /*
         * Insets
         */
        registerActivityLifecycleCallbacks(
                new ImeInsetsApplier()
        );

        /*
         * VM event handler
         */
        vmEventHandler = new VMEventHandler(this);
        registerActivityLifecycleCallbacks(vmEventHandler);

        /*
         * Daemon
         */
        DaemonConnection.getInstance()
                .addListener(vmEventHandler);

        /*
         * Inicialização do Shizuku
         */
        initializeShizuku();

        /*
         * Inicialização dos Stores
         */
        ThreadUtils.runOnPool(() -> {

            initializeStore(new VMStore());
            initializeStore(new DiskStore());
            initializeStore(new NetworkStore());
        });
    }

    /**
     * Inicializa/verifica o Shizuku.
     */
    private void initializeShizuku() {
        try {

            if (Shizuku.isPreV11()) {
                Log.w(
                        TAG,
                        "Shizuku API version is not supported"
                );
                return;
            }

            int permission = Shizuku.checkSelfPermission();

            if (permission == PackageManager.PERMISSION_GRANTED) {

                Log.i(
                        TAG,
                        "Shizuku permission already granted"
                );

            } else {

                Log.i(
                        TAG,
                        "Shizuku permission not granted"
                );
            }

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "Failed to initialize Shizuku",
                    e
            );
        }
    }

    /**
     * Verifica se o DroidVM possui permissão Shizuku.
     */
    public boolean hasShizukuPermission() {
        try {
            return !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission()
                    == PackageManager.PERMISSION_GRANTED;

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "Failed to check Shizuku permission",
                    e
            );

            return false;
        }
    }

    /**
     * Verifica se o Shizuku está disponível.
     */
    public boolean isShizukuAvailable() {
        try {
            return !Shizuku.isPreV11()
                    && Shizuku.pingBinder();

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "Shizuku binder unavailable",
                    e
            );

            return false;
        }
    }

    private void initializeStore(DataStore<?> store) {
        try {

            if (!store.getStoreFile(this).exists()) {
                store.save(this);
            }

        } catch (Exception e) {

            Log.w(
                    TAG,
                    String.format(
                            "Failed to initialize store: %s",
                            store.getClass().getSimpleName()
                    ),
                    e
            );
        }
    }

    public VMEventHandler getVMEventHandler() {
        return vmEventHandler;
    }

    @Override
    public void onTerminate() {

        DaemonConnection.getInstance()
                .removeListener(vmEventHandler);

        unregisterActivityLifecycleCallbacks(
                vmEventHandler
        );

        DaemonConnection.getInstance()
                .shutdown();

        super.onTerminate();
    }
}
