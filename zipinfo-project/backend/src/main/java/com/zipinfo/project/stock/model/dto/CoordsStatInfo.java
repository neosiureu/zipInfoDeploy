package com.zipinfo.project.stock.model.dto;

import lombok.Data;

@Data
public class CoordsStatInfo {
    private Double latCenter;
    private Double lngCenter;
    private Double latMin;
    private Double lngMin;
    private Double latMax;
    private Double lngMax;

    // getters/setters
}