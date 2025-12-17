package com.evolphin.zoom.aisearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoSearchResultDTO {
  private String assetId;
  // The start time of the relevant clip
  private double startTimeSeconds;
  // The end time of the relevant clip
  private double endTimeSeconds;
  // The highest semantic similarity score found in this clip duration
  private float relevanceScore;
}
