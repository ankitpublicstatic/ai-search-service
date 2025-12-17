package com.evolphin.zoom.aisearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StructuredFiltersDTO {
  private String category;
  private String project;
  private String drmStatus;
}
