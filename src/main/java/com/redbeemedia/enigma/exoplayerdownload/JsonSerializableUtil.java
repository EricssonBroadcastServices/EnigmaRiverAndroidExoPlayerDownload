package com.redbeemedia.enigma.exoplayerdownload;

import android.os.Parcel;
import android.os.Parcelable;

/*package-preotected*/ class JsonSerializableUtil {
    public static IJsonSerializable asJsonSerializable(Parcelable parcelable) {
        if(parcelable == null) {
            return null;
        }
        return new JsonSerializableAdapter(parcelable);
    }

    public static <T extends Parcelable> T readParcelableFromBase64String(String base64Data, Parcelable.Creator<T> creator) {
        return readParcelable(BaseJsonSerializable.decodeFromBase64String(base64Data), creator);
    }

    private static <T extends Parcelable> T readParcelable(byte[] data, Parcelable.Creator<T> creator) {
        if(data == null) {
            return null;
        }
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(data, 0, data.length);
        parcel.setDataPosition(0);
        return creator.createFromParcel(parcel);
    }

    private static class JsonSerializableAdapter implements IJsonSerializable {
        private final Parcelable parcelable;

        private JsonSerializableAdapter(Parcelable parcelable) {
            this.parcelable = parcelable;
        }

        @Override
        public byte[] getBytes() {
            Parcel parcel = Parcel.obtain();
            parcelable.writeToParcel(parcel, 0);
            byte[] bytes = parcel.marshall();
            parcel.recycle();
            return bytes;
        }
    }
}
