package com.redbeemedia.enigma.exoplayerdownload;

import android.os.Parcelable;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/*package-protected*/ abstract class BaseJsonSerializable implements IJsonSerializable {

    protected static <T> T fromBytes(byte[] data, Class<T> returnType, IJsonDeserializer<T> deserializer) {
        if(data == null) {
            return null;
        }
        byte saveFormatVersion = data[0];
        String jsonData = new String(data, 1, data.length-1, StandardCharsets.UTF_8);
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            return deserializer.deserialize(saveFormatVersion, jsonObject);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] getBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            JSONObject jsonObject = new JSONObject();
            int saveFormatVersion = storeInJson(jsonObject);
            baos.write(saveFormatVersion);
            byte[] jsonBytes = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
            baos.write(jsonBytes, 0, jsonBytes.length);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static String encodeToString(IJsonSerializable serializable) {
        if(serializable == null) {
            return null;
        }
        return encodeToString(serializable.getBytes());
    }


    public static String encodeToString(Parcelable parcelable) {
        return encodeToString(JsonSerializableUtil.asJsonSerializable(parcelable));
    }

    public static byte[] decodeFromBase64String(String string) {
        if(string == null) {
            return null;
        }
        return Base64.decode(string, Base64.DEFAULT);
    }

    protected static String encodeToString(byte bytes[]) {
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    protected abstract int storeInJson(JSONObject jsonObject) throws JSONException;

    interface IJsonDeserializer<T> {
        T deserialize(int saveFormatVersion, JSONObject jsonObject) throws JSONException;
    }
}
