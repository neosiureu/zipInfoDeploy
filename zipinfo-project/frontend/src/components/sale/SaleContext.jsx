import React, { createContext, useContext, useRef, useState } from "react";

const SaleContext = createContext();

export const SaleProvider = ({ children, searchParams, navigate }) => {
  /******************** 지도 관련 ref ********************/
  const mapRef = useRef(null); // 지도를 렌더링할 div DOM 참조
  const mapInstanceRef = useRef(null); // 생성된 Kakao map 객체 저장
  const itemMarkersRef = useRef([]); // 지도에 표시된 분양 마커 저장

  /******************** 패널 및 매물 상태 ********************/
  const [isAsideVisible, setIsAsideVisible] = useState(false); // 사이드 패널 열림 여부
  const [stockList, setStockList] = useState([]); // 현재 지도 내 분양 매물 리스트
  const [clickedStockItem, setClickedStockItem] = useState(null); // 선택된 매물

  /******************** 검색 조건 상태 ********************/
  const [searchKeyWord, setSearchKeyWord] = useState(""); // 키워드 검색
  const [searchLocationCode, setSearchLocationCode] = useState(-1); // 지역 코드
  const [searchSaleStatus, setSearchSaleStatus] = useState(-1); // 분양 상태
  const [searchSaleType, setSearchSaleType] = useState(-1); // 매물 유형

  /******************** 검색 조건 최신값 유지용 ref ********************/
  const searchKeyWordRef = useRef("");
  const locationCodeRef = useRef(-1);
  const saleStatusRef = useRef(-1);
  const saleTypeRef = useRef(-1);

  /******************** 외부에서 전달된 navigate, searchParams ********************/
  // → navigate는 리디렉션 용도, searchParams는 query string 파싱용
  return (
    <SaleContext.Provider
      value={{
        mapRef,
        mapInstanceRef,
        itemMarkersRef,

        isAsideVisible,
        setIsAsideVisible,
        stockList,
        setStockList,
        clickedStockItem,
        setClickedStockItem,

        searchKeyWord,
        setSearchKeyWord,
        searchKeyWordRef,
        searchLocationCode,
        setSearchLocationCode,
        locationCodeRef,
        searchSaleStatus,
        setSearchSaleStatus,
        saleStatusRef,
        searchSaleType,
        setSearchSaleType,
        saleTypeRef,

        searchParams,
        navigate,
      }}
    >
      {children}
    </SaleContext.Provider>
  );
};

// useContext로 쉽게 꺼내 쓸 수 있도록 커스텀 훅 제공
export const useSaleContext = () => useContext(SaleContext);
