package com.narish61.amefix;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 把模块私有目录里的自定义字体以只读 fd 暴露给宿主进程。
 * 通过 Android 标准的 ContentProvider IPC 传递，绕开 UID 隔离 / SELinux，
 * 不依赖 root（KernelSU / Magisk 都能用）。
 */
public class FontProvider extends ContentProvider {

    public static final String AUTHORITY = "com.narish61.amefix.fontprovider";
    public static final Uri FONT_URI = Uri.parse("content://" + AUTHORITY + "/custom_font.ttf");
    public static final String FONT_FILE = "custom_font.ttf";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("read-only provider");
        }
        File file = new File(getContext().getFilesDir(), FONT_FILE);
        if (!file.isFile() || file.length() == 0) {
            throw new FileNotFoundException("custom font not set");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
