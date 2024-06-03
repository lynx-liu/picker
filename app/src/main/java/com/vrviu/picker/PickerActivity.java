package com.vrviu.picker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class PickerActivity extends Activity implements Handler.Callback {
    private static final int MSG_REFRESH = 1;
    private static final int MSG_TIMEOUT = 0;
    private static final int TIMEOUT = 120000;
    private Handler handler = null;
    private BroadcastReceiver mediaScannerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Uri data = intent.getData();
            if (data != null) {
                Message obtain = Message.obtain();
                obtain.what = MSG_REFRESH;
                obtain.obj = data;
                handler.sendMessage(obtain);
            }
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
            }
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            }
            if (checkSelfPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE}, 2);
            }
        }

        Intent intent = getIntent();
        String action = intent.getAction();
        if (action==null || action.equals(Intent.ACTION_MAIN)) {
            finish();
        } else {
            handler = new Handler(this);
            handler.sendEmptyMessageDelayed(MSG_TIMEOUT, TIMEOUT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intentFilter.addDataScheme("file");
        registerReceiver(mediaScannerReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mediaScannerReceiver);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (handler != null) {
            handler.removeMessages(MSG_TIMEOUT);
            handler = null;
        }
        super.onDestroy();
    }

    private Uri queryUri(Uri uri) {
        Uri existingUri = null;
        String[] projection = { MediaStore.MediaColumns._ID };
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        String[] selectionArgs = { uri.getLastPathSegment() };
        Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null);

        if (cursor != null) {
            if(cursor.moveToFirst()) {
                @SuppressLint("Range") long existingId = cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
                existingUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, existingId);
            }
            cursor.close();
        }
        return existingUri;
    }

    boolean setResult(Uri uri) {
        Uri resultUri = queryUri(uri);
        if(resultUri==null) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, uri.getLastPathSegment());
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/*");
            resultUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        }

        if(resultUri==null) {
            setResult(RESULT_CANCELED);
            return false;
        }

        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {//Android13需要把内容复制过去
                int read;
                byte[] buffer = new byte[4096];
                InputStream openInputStream = getContentResolver().openInputStream(uri);
                OutputStream openOutputStream = getContentResolver().openOutputStream(resultUri);
                while ((read = openInputStream.read(buffer)) > 0) {
                    openOutputStream.write(buffer, 0, read);
                }
                openOutputStream.close();
                openInputStream.close();
            }

            Intent intent = new Intent();
            intent.setDataAndType(resultUri, "image/*");
            setResult(RESULT_OK, intent);
            return true;
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(),e.toString(),Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
        }
        return false;
    }

    void setResult(String str) {
        try {
            File file = new File(str);
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/*");
            Uri insert = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            if (insert != null) {
                OutputStream openOutputStream = getContentResolver().openOutputStream(insert);
                FileInputStream fileInputStream = new FileInputStream(file);

                int read;
                byte[] buffer = new byte[4096];
                while ((read = fileInputStream.read(buffer))>0) {
                    openOutputStream.write(buffer, 0, read);
                }
                fileInputStream.close();
                openOutputStream.close();

                Intent intent = new Intent();
                intent.setDataAndType(insert, "image/*");
                setResult(RESULT_OK, intent);
            } else {
                setResult(RESULT_CANCELED);
            }
        } catch (Exception e) {
            Log.d("llx", e.toString());
            setResult(RESULT_CANCELED);
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        switch (message.what) {
            case MSG_TIMEOUT:
                finish();
                break;

            case MSG_REFRESH:
                setResult((Uri)message.obj);
                finish();
                break;
        }
        return true;
    }
}