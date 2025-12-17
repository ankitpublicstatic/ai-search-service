package com.evolphin.zoom.aisearch.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class MockEmbeddingSupplier implements EmbeddingSupplier {
  private final Random random = new Random();

  @Override
  public List<Float> embedQueryText(String text) {
    // Simulate network latency of calling Python AI service
    try {
      Thread.sleep(100);
    } catch (InterruptedException ignored) {
    }

    // Return a fake 512-dimension vector normalized roughly to unit length
    List<Float> vector = new ArrayList<>(512);
    for (int i = 0; i < 512; i++) {
      vector.add(random.nextFloat() * 2 - 1);
    }
    return vector;
  }
}
