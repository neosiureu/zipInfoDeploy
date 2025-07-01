package com.zipinfo.project.sale.model.dto;

import lombok.Data;

@Data
public class SaleCoordsStatInfo {
    private Double latCenter;
    private Double lngCenter;
    private Double latMin;
    private Double lngMin;
    private Double latMax;
    private Double lngMax;
}
