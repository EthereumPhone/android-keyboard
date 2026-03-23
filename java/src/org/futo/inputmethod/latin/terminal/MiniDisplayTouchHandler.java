package org.futo.inputmethod.latin.terminal;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;

/**
 * Handles touch events on the ethOS mini (back) display.
 * Adapted from TerminalSDK for use within the keyboard IME.
 */
public class MiniDisplayTouchHandler {
    private static final String TAG = "MiniDisplayTouchHandler";
    private static final String SERVICE_NAME = "minidisplay.touch";
    private static final String SERVICE_INTERFACE_TOKEN = "minidisplay.touch";
    private static final String LISTENER_INTERFACE_TOKEN =
            "com.freeme.backscreen.IMiniDisplayTouchListener";

    private final Context mContext;
    private OnTouchListener touchListener;
    private Object mService;
    private ListenerBinder mListenerBinder;
    private boolean isConnected = false;

    private static MiniDisplayTouchHandler activeInstance = null;

    public interface OnTouchListener {
        void onTouch(float x, float y, int action);
    }

    private class ListenerBinder extends Binder {
        private static final int TRANSACTION_ON_TOUCH = 1;

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == TRANSACTION_ON_TOUCH) {
                data.enforceInterface(LISTENER_INTERFACE_TOKEN);
                float x = data.readFloat();
                float y = data.readFloat();
                int action = data.readInt();
                long eventTime = data.readLong();

                mContext.getMainExecutor().execute(() -> {
                    if (touchListener != null && isConnected) {
                        touchListener.onTouch(x, y, action);
                    }
                });

                reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    private static class ServiceProxy {
        private static final int TRANSACTION_REGISTER = 1;
        private static final int TRANSACTION_UNREGISTER = 2;
        private static final int TRANSACTION_IS_REGISTERED = 3;
        private static final int TRANSACTION_GET_ACTIVE = 4;

        private final IBinder mRemote;

        ServiceProxy(IBinder remote) {
            mRemote = remote;
        }

        boolean registerTouchListener(IBinder listener, String packageName)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SERVICE_INTERFACE_TOKEN);
                data.writeStrongBinder(listener);
                data.writeString(packageName);
                mRemote.transact(TRANSACTION_REGISTER, data, reply, 0);
                reply.readException();
                return reply.readInt() != 0;
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        void unregisterTouchListener(IBinder listener) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SERVICE_INTERFACE_TOKEN);
                data.writeStrongBinder(listener);
                mRemote.transact(TRANSACTION_UNREGISTER, data, reply, 0);
                reply.readException();
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        boolean isListenerRegistered(String packageName) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SERVICE_INTERFACE_TOKEN);
                data.writeString(packageName);
                mRemote.transact(TRANSACTION_IS_REGISTERED, data, reply, 0);
                reply.readException();
                return reply.readInt() != 0;
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        String getActiveListenerPackage() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SERVICE_INTERFACE_TOKEN);
                mRemote.transact(TRANSACTION_GET_ACTIVE, data, reply, 0);
                reply.readException();
                return reply.readString();
            } finally {
                reply.recycle();
                data.recycle();
            }
        }
    }

    /**
     * Query the active listener package without registering a handler.
     * Returns null if the service is unavailable or no listener is active.
     */
    public static String queryActiveListenerPackage() {
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            IBinder serviceBinder = (IBinder) getServiceMethod.invoke(null, SERVICE_NAME);
            if (serviceBinder == null) return null;

            ServiceProxy proxy = new ServiceProxy(serviceBinder);
            return proxy.getActiveListenerPackage();
        } catch (Exception e) {
            Log.e(TAG, "Failed to query active listener package", e);
            return null;
        }
    }

    public MiniDisplayTouchHandler(Context context, OnTouchListener listener) {
        if (activeInstance != null) {
            activeInstance.destroy();
        }

        this.mContext = context.getApplicationContext();
        this.touchListener = listener;
        this.mListenerBinder = new ListenerBinder();

        initializeService();
        activeInstance = this;
    }

    private void initializeService() {
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            IBinder serviceBinder = (IBinder) getServiceMethod.invoke(null, SERVICE_NAME);

            if (serviceBinder == null) {
                Log.e(TAG, "Mini display touch service not found");
                return;
            }

            mService = new ServiceProxy(serviceBinder);
            registerListener();
            isConnected = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize service connection", e);
            throw new RuntimeException("Failed to connect to mini display touch service", e);
        }
    }

    private void registerListener() throws Exception {
        if (mService == null || mListenerBinder == null) {
            throw new IllegalStateException("Service or listener not initialized");
        }
        ServiceProxy proxy = (ServiceProxy) mService;
        boolean result = proxy.registerTouchListener(mListenerBinder, mContext.getPackageName());
        if (!result) {
            throw new RuntimeException("Failed to register touch listener");
        }
    }

    private void unregisterListener() {
        if (mService == null || mListenerBinder == null || !isConnected) return;
        try {
            ServiceProxy proxy = (ServiceProxy) mService;
            proxy.unregisterTouchListener(mListenerBinder);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister listener", e);
        }
    }

    public void destroy() {
        isConnected = false;
        unregisterListener();
        mService = null;
        mListenerBinder = null;
        touchListener = null;
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    public boolean isActive() {
        if (!isConnected || mService == null) return false;
        try {
            ServiceProxy proxy = (ServiceProxy) mService;
            return proxy.isListenerRegistered(mContext.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }
}
