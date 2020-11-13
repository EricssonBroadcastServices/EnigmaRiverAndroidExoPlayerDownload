package com.redbeemedia.enigma.download;

import java.util.HashMap;
import java.util.Map;

public class MockMetadataManager implements IMetadataManager {
    private final Map<String, byte[]> inMemory = new HashMap<>();

    @Override
    public void store(String contentId, byte[] data) {
        inMemory.put(contentId, data);
    }

    @Override
    public void clear(String contentId) {
        inMemory.remove(contentId);
    }

    @Override
    public byte[] load(String contentId) {
        return inMemory.get(contentId);
    }
}
