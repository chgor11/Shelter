package net.typeblog.shelter.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Size;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.util.CrossProfileDocumentsProvider;
import net.typeblog.shelter.util.Utility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// A service to forward file information across the profile boundary
public class FileShuttleService extends Service {
    public static final long TIMEOUT = 10000;
    // Periodic task to stop the service when idle.
    // This service does not need to persist.
    private Runnable mSuicideTask = this::suicide;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private IFileShuttleService.Stub mStub = new IFileShuttleService.Stub() {
        @Override
        public void ping() {
            // Dummy method
            resetSuicideTask();
        }

        @Override
        public List<FileShuttleMeta> loadFiles(String path) {
            resetSuicideTask();
            ArrayList<FileShuttleMeta> ret = new ArrayList<>();
            File f = new File(resolvePath(path));
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    ret.add(loadFileMeta(child.getPath()));
                }
            }
            return ret;
        }

        @Override
        public FileShuttleMeta loadFileMeta(String path) {
            resetSuicideTask();
            File f = new File(resolvePath(path));

            final String documentId = f.getAbsolutePath();

            final String displayName;
            if (f.equals(Environment.getExternalStorageDirectory())) {
                // Show "Shelter" as the name of the root directory
                displayName = getString(R.string.app_name);
            } else {
                displayName = f.getName();
            }

            final long size = f.length();
            final long lastModified = f.lastModified();

            final String mimeType;
            final int flags;

            if (f.isDirectory()) {
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
                flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                        | DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
            } else {
                final String detected = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        Utility.getFileExtension(f.getAbsolutePath()));
                int computedFlags = DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
                if (detected != null
                        && (detected.startsWith("image/") || detected.startsWith("video/"))) {
                    computedFlags |= DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL;
                }
                mimeType = detected != null ? detected : "application/unknown";
                flags = computedFlags;
            }

            return new FileShuttleMeta(documentId, displayName, size, lastModified, mimeType, flags);
        }

        @Override
        public ParcelFileDescriptor openFile(String path, String mode) {
            resetSuicideTask();
            File f = new File(resolvePath(path));
            int numericMode = ParcelFileDescriptor.parseMode(mode);

            try {
                if ((numericMode & ParcelFileDescriptor.MODE_WRITE_ONLY) != 0) {
                    // When the file is opened in writable mode, and the file is of a media
                    // type, we need to notify the media scanner of the update.
                    // Even though this is done as part of file creation as well, that scan
                    // might have failed because it happened before the writer was able to
                    // finish writing.
                    return ParcelFileDescriptor.open(f, numericMode, mHandler, (e) -> {
                        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                                Utility.getFileExtension(f.getAbsolutePath()));
                        notifyMediaScannerIfNecessary(f, mime);
                    });
                } else {
                    return ParcelFileDescriptor.open(f, numericMode);
                }
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public ParcelFileDescriptor openThumbnail(String path, Point sizeHint) {
            resetSuicideTask();
            String fullPath = resolvePath(path);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    Utility.getFileExtension(fullPath));
            if (mime == null) {
                return null;
            }
            if (mime.startsWith("image/")) {
                return loadImageThumbnail(fullPath, sizeHint);
            } else if (mime.startsWith("video/")) {
                return loadVideoThumbnail(fullPath);
            } else {
                return null;
            }
        }

        @Override
        public String createFile(String path, String mimeType, String displayName) {
            resetSuicideTask();
            File f;
            String fullPath = path + "/" + displayName;
            boolean isDirectory =
                    DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
            boolean shouldAppendExtension =
                    mimeType != null && !isDirectory && !mimeType.equals("application/octet-stream");

            // Append extension for files if a MIME type is specified
            if (shouldAppendExtension) {
                String extensionPart = "." + MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (!fullPath.endsWith(extensionPart)) {
                    fullPath += extensionPart;
                }
            }

            // Now we can create the file / directory
            f = new File(resolvePath(fullPath));
            try {
                if ((isDirectory && !f.mkdir()) || (!isDirectory && !f.createNewFile())) {
                    return null;
                }
            } catch (IOException e) {
                return null;
            }

            notifyMediaScannerIfNecessary(f, mimeType);

            return f.getAbsolutePath();
        }

        @Override
        public String deleteFile(String path) {
            resetSuicideTask();
            File f = new File(resolvePath(path));
            f.delete();
            return f.getParentFile().getAbsolutePath();
        }

        @Override
        public boolean isChildOf(String parent, String child) {
            File parentFile = new File(resolvePath(parent));
            File childFile = new File(resolvePath(child));
            String parentPath = parentFile.getAbsolutePath();
            if (parentPath.charAt(parentPath.length() - 1) != '/') {
                parentPath += "/"; // Make sure it ends with '/'
            }
            return parentFile.exists() && parentFile.isDirectory()
                    && childFile.exists()
                    && childFile.getAbsolutePath().startsWith(parentPath);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        resetSuicideTask();
        return mStub;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private String resolvePath(String path) {
        if (path.startsWith(CrossProfileDocumentsProvider.DUMMY_ROOT)) {
            return path.replaceFirst(CrossProfileDocumentsProvider.DUMMY_ROOT,
                    Environment.getExternalStorageDirectory().getAbsolutePath());
        } else {
            return path;
        }
    }

    private void resetSuicideTask() {
        mHandler.removeCallbacks(mSuicideTask);
        mHandler.postDelayed(mSuicideTask, TIMEOUT);
    }

    private void suicide() {
        mHandler.removeCallbacks(mSuicideTask);
        ((ShelterApplication) getApplication()).unbindFileShuttleService();
        stopSelf();
    }

    // Modern replacement for the deprecated MediaStore.Images.Thumbnails API.
    // ContentResolver.loadThumbnail() supports both file:// and content:// URIs
    // and uses ImageDecoder internally to produce a properly scaled thumbnail.
    private ParcelFileDescriptor loadImageThumbnail(String fullPath, Point sizeHint) {
        try {
            Bitmap bmp = getContentResolver().loadThumbnail(
                    Uri.fromFile(new File(fullPath)),
                    new Size(sizeHint.x, sizeHint.y),
                    null);
            return bitmapToFd(bmp);
        } catch (IOException | SecurityException e) {
            // If the modern thumbnail pipeline fails for any reason, fall back
            // to decoding the file directly (still uses BitmapFactory).
            return loadBitmapThumbnail(fullPath, sizeHint);
        }
    }

    private ParcelFileDescriptor loadVideoThumbnail(String fullPath) {
        try {
            Bitmap bmp = ThumbnailUtils.createVideoThumbnail(
                    new File(fullPath),
                    new Size(512, 384),  // equivalent of the old MINI_KIND
                    null);
            return bitmapToFd(bmp);
        } catch (IOException e) {
            // Thumbnail generation failed; return null so DocumentsUI
            // falls back to its default icon for this document.
            return null;
        }
    }

    // Fallback method for thumbnail loading: just load from disk, but load a scaled down version
    private ParcelFileDescriptor loadBitmapThumbnail(String path, Point sizeHint) {
        Bitmap bmp = Utility.decodeSampledBitmap(path, sizeHint.x, sizeHint.y);

        if (bmp == null) {
            return null;
        }

        return bitmapToFd(bmp);
    }

    private ParcelFileDescriptor bitmapToFd(Bitmap bmp) {
        if (bmp == null) {
            return null;
        }
        ParcelFileDescriptor[] pair;
        try {
            // Use a pipe as a virtual in-memory ParcelFileDescriptor
            pair = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            return null;
        }

        // Send the bitmap into the pipe in another thread, so that we can return the
        // reading fd to the Documents UI before we finish sending the Bitmap.
        new Thread(() -> {
            try (FileOutputStream os = new FileOutputStream(pair[1].getFileDescriptor())) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.flush();
            } catch (IOException e) {
                // ...
            }
            bmp.recycle();
        }).start();

        return pair[0];
    }

    // Modern replacement for Intent.ACTION_MEDIA_SCANNER_SCAN_FILE (deprecated in API 29).
    // MediaScannerConnection.scanFile() is the supported, direct replacement.
    private void notifyMediaScannerIfNecessary(File f, String mimeType) {
        if (mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"))) {
            MediaScannerConnection.scanFile(
                    this,
                    new String[]{f.getAbsolutePath()},
                    new String[]{mimeType},
                    null);
        }
    }
}
