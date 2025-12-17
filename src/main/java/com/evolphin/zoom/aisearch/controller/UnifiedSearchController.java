package com.evolphin.zoom.aisearch.controller;

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.evolphin.zoom.aisearch.dto.SearchRequestDTO;
import com.evolphin.zoom.aisearch.dto.VideoSearchResultDTO;
import com.evolphin.zoom.aisearch.service.UnifiedSearchService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class UnifiedSearchController {

  private final UnifiedSearchService searchService;

  /**
   * Endpoint for Unified Semantic + Structured Search. Example curl: curl -X POST
   * http://localhost:8080/api/v1/search/hybrid \ -H "Content-Type: application/json" \ -d '{
   * "queryText": "fast red car", "filters": { "category": "automotive", "drmStatus": "licensed" }
   * }'
   */
  @PostMapping("/hybrid")
  public ResponseEntity<List<VideoSearchResultDTO>> hybridSearch(
      @RequestBody SearchRequestDTO request) {
    try {
      List<VideoSearchResultDTO> results = searchService.performHybridSearch(request);
      return ResponseEntity.ok(results);
    } catch (ExecutionException | InterruptedException e) {
      // In a real app, use a global exception handler to map to proper HTTP error codes
      Thread.currentThread().interrupt();
      return ResponseEntity.internalServerError().build();
    }
  }
}
