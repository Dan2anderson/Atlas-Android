package com.layer.atlas.messagetypes.threepartimage;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;

import com.layer.atlas.R;
import com.layer.atlas.messagetypes.AttachmentSender;
import com.layer.atlas.util.Log;
import com.layer.atlas.util.Util;
import com.layer.sdk.messaging.Identity;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.PushNotificationPayload;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CameraSender creates a ThreePartImage from the device's camera.
 * <p>
 * Note: If your AndroidManifest declares that it uses the CAMERA permission, then CameraSender will
 * require that the CAMERA permission is also granted.  If your AndroidManifest does not declare
 * that it uses the CAMERA permission, then CameraSender will not require the CAMERA permission to
 * be granted. See http://developer.android.com/reference/android/provider/MediaStore.html#ACTION_IMAGE_CAPTURE
 * for details.
 */
public class CameraSender extends AttachmentSender {
    private static final String PERMISSION_READ = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? Manifest.permission.READ_EXTERNAL_STORAGE : null;
    private static final String PERMISSION_WRITE = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? Manifest.permission.WRITE_EXTERNAL_STORAGE : null;

    public static final int ACTIVITY_REQUEST_CODE = 20;

    private static String fileProviderDestination;

    /**
     * If the developers app already has a FileProvider you will need to call this method at initialization to set the layer fileProviderDestination to match the apps destination for its own fileProvider.
     * So for example if your apps FileProvider's authority is like this in the manifest  android:authorities="com.salesrabbit.android.sales.universal.fileprovider"  you will need to pass in the string ".fileprovider"
     * @param str
     */
    public static void setFileProviderDestination(String str){
        fileProviderDestination = str;
    }

    public static final int PERMISSION_REQUEST_CODE = 110;


    private WeakReference<Activity> mActivity = new WeakReference<Activity>(null);

    private final AtomicReference<String> mPhotoFilePath = new AtomicReference<String>(null);

    public CameraSender(int titleResId, Integer iconResId, Activity activity) {
        this(activity.getString(titleResId), iconResId, activity);
    }

    public CameraSender(String title, Integer iconResId, Activity activity) {
        super(title, iconResId);
        mActivity = new WeakReference<Activity>(activity);
    }

    private void startCameraIntent(Activity activity) {
        String fileName = "cameraOutput" + System.currentTimeMillis() + ".jpg";
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), fileName);
        mPhotoFilePath.set(file.getAbsolutePath());

        String authority = activity.getApplicationContext().getPackageName() + ".com.layer.atlas.provider";

        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        final Uri outputUri;
        if (fileProviderDestination == null) {
            fileProviderDestination = ".provider";  //If the developer has not set their owne fileProvider destination then set it to use the default fileProvider for layer.
        }
        if (Build.VERSION.SDK_INT >=  Build.VERSION_CODES.N) {
            outputUri = FileProvider.getUriForFile(getContext(), getContext().getApplicationContext().getPackageName() + fileProviderDestination, file);
        } else {
            outputUri = Uri.fromFile(file);
        }
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);

        activity.startActivityForResult(cameraIntent, ACTIVITY_REQUEST_CODE);
    }

    @Override
    public boolean requestSend() {
        Activity activity = mActivity.get();
        if (activity == null) return false;

        if (Log.isLoggable(Log.VERBOSE)) Log.v("Checking permissions");

        if (!hasPermissions(activity, PERMISSION_READ, PERMISSION_WRITE)) {
            requestPermissions(activity, PERMISSION_REQUEST_CODE, PERMISSION_READ, PERMISSION_WRITE);
        } else {
            startCameraIntent(activity);
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE) return;

        if (grantResults.length != 2) {
            if (Log.isLoggable(Log.VERBOSE)) Log.v("One or more required  permissions denied");
            return;
        }

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            if (Log.isLoggable(Log.VERBOSE)) Log.v("Sending camera image");
            Activity activity = mActivity.get();
            if (activity == null) return;
            startCameraIntent(activity);
        }
    }

    @Override
    public boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode != ACTIVITY_REQUEST_CODE) return false;
        if (resultCode != Activity.RESULT_OK) {
            if (Log.isLoggable(Log.ERROR)) Log.e("Result: " + requestCode + ", data: " + data);
            return true;
        }
        if (Log.isLoggable(Log.VERBOSE)) Log.v("Received camera response");
        try {
            Identity me = getLayerClient().getAuthenticatedUser();
            String myName = me == null ? "" : Util.getDisplayName(me);
            Message message = ThreePartImageUtils.newThreePartImageMessage(activity, getLayerClient(), new File(mPhotoFilePath.get()));

            PushNotificationPayload payload = new PushNotificationPayload.Builder()
                    .text(getContext().getString(R.string.atlas_notification_image, myName))
                    .build();
            message.getOptions().defaultPushNotificationPayload(payload);
            send(message);
        } catch (IOException e) {
            if (Log.isLoggable(Log.ERROR)) Log.e(e.getMessage(), e);
        }
        return true;
    }

    /**
     * Saves photo file path during e.g. screen rotation
     */
    @Override
    public Parcelable onSaveInstanceState() {
        String path = mPhotoFilePath.get();
        if (path == null) return null;
        Bundle bundle = new Bundle();
        bundle.putString("photoFilePath", path);
        return bundle;
    }

    /**
     * Restores photo file path during e.g. screen rotation
     */
    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state == null) return;
        String path = ((Bundle) state).getString("photoFilePath");
        mPhotoFilePath.set(path);
    }
}
