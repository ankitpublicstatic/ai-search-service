package com.evolphin.zoom.aisearch.dto;

import lombok.Data;

@Data
public class SearchRequestDTO {
  private String queryText;
  private StructuredFiltersDTO filters;
  private int limit = 20;
}
