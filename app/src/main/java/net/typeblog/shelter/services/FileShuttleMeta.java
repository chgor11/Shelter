package net.typeblog.shelter.services;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * Typed, Parcelable replacement for the legacy Map&lt;String, Serializable&gt; used
 * to transfer file metadata across the AIDL boundary.
 *
 * <p>Rationale: AIDL does not support generics such as Map&lt;String, Serializable&gt;
 * or List&lt;Map&lt;String, Serializable&gt;&gt;. Declaring them forced the AIDL
 * compiler to generate Parcel.readHashMap(ClassLoader) and
 * Parcel.readArrayList(ClassLoader) calls, both of which were deprecated in
 * API 34, and forced the client to use unchecked conversions.
 *
 * <p>A typed Parcelable removes both warnings and gives the client and server
 * a single, compile-time-checked contract.
 */
public final class FileShuttleMeta implements Parcelable {

    public final String documentId;
    public final String displayName;
    public final long size;
    public final long lastModified;
    public final String mimeType;
    public final int flags;

    public FileShuttleMeta(
            String documentId,
            String displayName,
            long size,
            long lastModified,
            String mimeType,
            int flags) {
        this.documentId = documentId;
        this.displayName = displayName;
        this.size = size;
        this.lastModified = lastModified;
        this.mimeType = mimeType;
        this.flags = flags;
    }

    private FileShuttleMeta(@NonNull Parcel in) {
        documentId = in.readString();
        displayName = in.readString();
        size = in.readLong();
        lastModified = in.readLong();
        mimeType = in.readString();
        flags = in.readInt();
    }

    public static final Creator<FileShuttleMeta> CREATOR = new Creator<FileShuttleMeta>() {
        @Override
        public FileShuttleMeta createFromParcel(Parcel in) {
            return new FileShuttleMeta(in);
        }

        @Override
        public FileShuttleMeta[] newArray(int size) {
            return new FileShuttleMeta[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int parcelFlags) {
        dest.writeString(documentId);
        dest.writeString(displayName);
        dest.writeLong(size);
        dest.writeLong(lastModified);
        dest.writeString(mimeType);
        dest.writeInt(flags);
    }
}
