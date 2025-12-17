package com.evolphin.zoom.aisearch.service;

import java.util.List;

public interface EmbeddingSupplier {
  /**
   * Takes raw text and returns the 512-dim CLIP embedding. Usually calls an external Python
   * microservice via gRPC/REST.
   */
  List<Float> embedQueryText(String text);
}
