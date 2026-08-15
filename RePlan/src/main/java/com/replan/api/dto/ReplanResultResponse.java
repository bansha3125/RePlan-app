package com.replan.api.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class ReplanResultResponse {
    private boolean success;
    private String message;
    private List<String> warnings;
}