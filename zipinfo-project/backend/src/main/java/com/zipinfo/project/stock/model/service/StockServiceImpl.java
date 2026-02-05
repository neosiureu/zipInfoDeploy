package com.zipinfo.project.stock.model.service;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.common.searchbar.model.mapper.SearchBarMapper;
import com.zipinfo.project.stock.model.dto.CoordsStatInfo;
import com.zipinfo.project.stock.model.dto.SearchRequest;
import com.zipinfo.project.stock.model.dto.Stock;
import com.zipinfo.project.stock.model.mapper.StockMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class StockServiceImpl implements StockService {
   
   @Autowired
   private StockMapper mapper;
   
   @Autowired
   private SearchBarMapper searchBarMapper;
   
   /** 좌표 내부에 있는 매물들을 반환하는 함수.
    *
    */
 @Override
public List<Stock> getStockListInRange(SearchRequest sr) {
    // 1. 매퍼로부터 리스트를 일단 받습니다.
    List<Stock> list = mapper.selectStockInRange(
            sr.getCoords().get("swLat"),
            sr.getCoords().get("swLng"),
            sr.getCoords().get("neLat"),
            sr.getCoords().get("neLng"),
            sr.getSearchKeyWord(),
            sr.getLocationCode(),
            sr.getStockForm(),
            sr.getStockType()
    );

    // 2. 로그를 찍어봅니다.
    if (list != null && !list.isEmpty()) {
        // 첫 번째 매물만 샘플로 조사
        Stock sample = list.get(0);
        log.info("[검증 로그] 매물명: {}, 좌표: ({}, {}), 지역코드(regionNo): {}", 
                 sample.getStockName(), sample.getLat(), sample.getLng(), sample.getRegionNo());
        
        // 만약 특정 매물(장수군 등)을 찾고 싶다면 루프를 돌려보세요
        /*
        for(Stock s : list) {
            if(s.getRegionNo() == 0) {
                log.warn("[경고] regionNo가 0인 매물 발견! 매물번호: {}", s.getStockNo());
            }
        }
        */
    } else {
        log.info("[검증 로그] 해당 범위에 검색된 매물이 없습니다.");
    }

    return list;
}

   /** 시군구 코드를 입력하면 시군구의 이름을 반환하는 함수
    * 11110 -> 종로구
    *
    */
   @Override
   public String getSigunguFullName(int code) {
      // TODO Auto-generated method stub
      return mapper.selectSigunguFullName(code);
   }
   
   /** 해당 조건들의 매물들의 평균 좌표, 최소 lat/lng, 최대 lat/lng를 반환하는 함수
    * @return key : latCenter - 매물들의 평균 lat
    * @return key : lngCenter - 매물들의 평균 lng
    * @return key : latMin - 매물들중에서 가장 작은 lat값
    * @return key : lngMin - 매물들중에서 가장 작은 lng값
    * @return key : latMax - 매물들중에서 가장 큰 lat값
    * @return key : lngMax - 매물들중에서 가장 큰 lng값
    */
   @Override
   public CoordsStatInfo getCoordsFromStock(
         SearchRequest sr){
      
      return mapper.getCoordsFromStock(
            sr.getSearchKeyWord(),
            sr.getLocationCode(),
            sr.getStockForm(),
            sr.getStockType());
   }
   
   @Override
   public List<Stock> selectAnyFour(){
      List<Stock> list = mapper.selectAnyFour();
      if(list != null && !list.isEmpty()) {
          log.info("[DEBUG] selectAnyFour 첫번째 매물 주소: {}", list.get(0).getStockAddress());
      }
      return list;
   }

   @Override
   public Stock selectStockDetail(int stockNo) {
      Stock stock = mapper.selectOneStock(stockNo);
      if(stock != null) {
          log.info("[DEBUG] selectStockDetail 상세주소 (stockNo: {}): {}", stockNo, stock.getStockAddress());
      }
      return stock;
   }

   @Override
   public List<Map<String, Object>> stockChart() {
      return mapper.stockChart();
   }
   
}
