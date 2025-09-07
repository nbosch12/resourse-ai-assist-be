package com.bosch.user.resourceaiassist.services;


import java.util.List;

public interface EmbeddingClient {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
}