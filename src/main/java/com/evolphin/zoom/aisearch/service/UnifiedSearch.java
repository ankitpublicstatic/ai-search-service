package com.evolphin.zoom.aisearch.service;


import static io.qdrant.client.ConditionFactory.matchKeyword;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import com.evolphin.zoom.aisearch.dto.SearchRequestDTO;
import com.evolphin.zoom.aisearch.dto.StructuredFiltersDTO;
import com.evolphin.zoom.aisearch.dto.VideoSearchResultDTO;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt; // Required for 'Value'
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
// @Service
@RequiredArgsConstructor
public class UnifiedSearch {

  private final QdrantClient qdrantClient;
  private final EmbeddingSupplier embeddingSupplier;

  @Value("${qdrant.collection:zoom_media_assets}")
  private String collectionName;

  /**
   * Core Logic: Combines semantic vector query with structured SQL-like filters.
   */
  public List<VideoSearchResultDTO> performHybridSearch(SearchRequestDTO request)
      throws ExecutionException, InterruptedException {
    log.info("Starting hybrid search for query: '{}' with filters: {}", request.getQueryText(),
        request.getFilters());

    // 1. Convert text query to vector (Semantic foundation)
    List<Float> queryVector = embeddingSupplier.embedQueryText(request.getQueryText());

    // 2. Build Structured Filters (The "Structured" part)
    Points.Filter filter = buildQdrantFilters(request.getFilters());

    // 3. Execute Hybrid Query on Qdrant
    // We ask for more results than requested because raw results are individual frames,
    // and we will group them into clips later.
    int oversamplingLimit = request.getLimit() * 5;
    Points.SearchPoints searchRequest2 = Points.SearchPoints.newBuilder()
        .setCollectionName(collectionName).addAllVector(queryVector).setFilter(filter)
        // .setFilter(filter) Applies pre-filtering before HNSW search
        .setLimit(oversamplingLimit)
        // Ensure we get the payload back to know the timestamps and asset IDs
        .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build()).build();


    // 1. Build the Search Request
    SearchPoints searchRequest = SearchPoints.newBuilder().setCollectionName(collectionName)
        // Ensure queryVector is strictly List<Float>
        .addAllVector(queryVector).setFilter(filter).setLimit(oversamplingLimit)
        // Correct way to enable payload in the generic selector
        .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build()).build();

    ListenableFuture<List<Points.ScoredPoint>> asyncResult =
        qdrantClient.searchAsync(searchRequest);
    // Blocking here for simplicity in Spring Web MVC context.
    // In WebFlux, this would remain reactive.
    List<Points.ScoredPoint> rawFrameResults = asyncResult.get();

    log.info("Qdrant returned {} raw frames. Beginning time-coalescence...",
        rawFrameResults.size());

    // 4. Post-Processing: Coalesce frames into video clips
    return coalesceFramesIntoClips(rawFrameResults, request.getLimit());
  }

  /**
   * Helper to build Qdrant gRPC Filter objects from DTO.
   */
  private Points.Filter buildQdrantFilters(StructuredFiltersDTO filtersDTO) {
    Points.Filter.Builder filterBuilder = Points.Filter.newBuilder();

    if (filtersDTO == null) {
      return filterBuilder.build();
    }

    // Using 'must' ensures these conditions define the mandatory search space.
    if (filtersDTO.getCategory() != null && !filtersDTO.getCategory().isBlank()) {
      filterBuilder.addMust(matchKeyword("category", filtersDTO.getCategory()));
    }
    if (filtersDTO.getProject() != null && !filtersDTO.getProject().isBlank()) {
      filterBuilder.addMust(matchKeyword("project", filtersDTO.getProject()));
    }
    if (filtersDTO.getDrmStatus() != null && !filtersDTO.getDrmStatus().isBlank()) {
      filterBuilder.addMust(matchKeyword("drm_status", filtersDTO.getDrmStatus()));
    }

    return filterBuilder.build();
  }

  /**
   * THE CHALLENGE: Raw semantic search returns many frames from the same video scene. We must group
   * contiguous frames into a single "Clip" result for the UI.
   */
  private List<VideoSearchResultDTO> coalesceFramesIntoClips(List<Points.ScoredPoint> rawFrames,
      int finalLimit) {
    if (rawFrames.isEmpty()) {
      return Collections.emptyList();
    }

    // 4a. Map raw points to intermediate workable objects
    List<RawFrameData> frameDataList = rawFrames.stream().map(point -> {

      // 2. Map the Results

      Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
      return new RawFrameData(payload.get("asset_id").getStringValue(),
          payload.get("timestamp").getDoubleValue(), point.getScore());
    }).collect(Collectors.toCollection(ArrayList::new));

    // 4b. Sort primarily by Asset ID, secondarily by time. Critical for grouping logic.
    frameDataList.sort(
        Comparator.comparing(RawFrameData::assetId).thenComparingDouble(RawFrameData::timestamp));

    // 4c. Grouping logic (Temporal Coalescence)
    List<VideoSearchResultDTO> mergedClips = new ArrayList<>();
    VideoSearchResultDTO currentClip = null;
    // Define tolerance: frames within 1.5s of each other are considered part of the same "scene"
    double TIME_TOLERANCE_SECONDS = 1.5;

    for (RawFrameData frame : frameDataList) {
      if (currentClip == null) {
        // Start first clip
        currentClip =
            new VideoSearchResultDTO(frame.assetId, frame.timestamp, frame.timestamp, frame.score);
      } else {
        boolean isSameAsset = currentClip.getAssetId().equals(frame.assetId);
        // Check if this frame is close enough in time to extend the current clip
        boolean isContiguous =
            frame.timestamp - currentClip.getEndTimeSeconds() <= TIME_TOLERANCE_SECONDS;

        if (isSameAsset && isContiguous) {
          // Extend current clip range
          currentClip.setEndTimeSeconds(frame.timestamp);
          // Keep track of the highest score found in this segment
          currentClip.setRelevanceScore(Math.max(currentClip.getRelevanceScore(), frame.score));
        } else {
          // Close current clip and start new one
          mergedClips.add(currentClip);
          currentClip = new VideoSearchResultDTO(frame.assetId, frame.timestamp, frame.timestamp,
              frame.score);
        }
      }
    }
    // Add the final pending clip
    if (currentClip != null) {
      mergedClips.add(currentClip);
    }

    // 4d. Re-sort final clips by their best score descending and limit
    return mergedClips.stream()
        .sorted(Comparator.comparingDouble(VideoSearchResultDTO::getRelevanceScore).reversed())
        .limit(finalLimit).collect(Collectors.toList());
  }

  // Internal helper record for intermediate processing
  private record RawFrameData(String assetId, double timestamp, float score) {

  }
}
