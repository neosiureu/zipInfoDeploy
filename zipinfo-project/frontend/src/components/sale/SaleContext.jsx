import React, { createContext, useState, useRef, useContext } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

const SaleContext = createContext();

export const SaleProvider = ({ children, searchParams, navigate }) => {
  // Kakao api 세팅
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const itemMarkersRef = useRef([]);

  // 사이드 패널 관련
  const [isAsideVisible, setIsAsideVisible] = useState(false);

  // 매물 상태 변수
  const [stockList, setStockList] = useState(null); // 전체 매물 리스트
  const [clickedStockItem, setClickedStockItem] = useState(null); // 상세 보기 매물

  // 검색 조건 관련
  const [searchKeyWord, setSearchKeyWord] = useState("");
  const searchKeyWordRef = useRef(searchKeyWord);

  const [searchLocationCode, setSearchLocationCode] = useState(-1);
  const locationCodeRef = useRef(searchLocationCode);

  const [searchSaleStatus, setSearchSaleStatus] = useState(-1);
  const searchSaleStatusRef = useRef(searchSaleStatus);

  const [searchSaleStockForm, setSearchSaleStockForm] = useState(-1);
  const searchSaleStockFormRef = useRef(searchSaleStockForm);

  // 마커 겹침 처리용 변수 (일반 JS 변수)
  const gridSize = 50;
  const cellMap = {};

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
        searchSaleStatusRef,
        searchSaleStockForm,
        setSearchSaleStockForm,
        searchSaleStockFormRef,
        navigate,
        searchParams,
        // gridSize, cellMap은 상태 아님
      }}
    >
      {children}
    </SaleContext.Provider>
  );
};

// 사용 시: const { stockList } = useSaleContext();
export const useSaleContext = () => useContext(SaleContext);
