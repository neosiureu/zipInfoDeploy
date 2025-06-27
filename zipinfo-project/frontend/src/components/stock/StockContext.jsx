//StockPage 전용 contextProvider를 여기 따로 구현할 것임.

import React, { createContext, useState, useRef, useContext } from "react";
import {
  useNavigate,
  useLocation,
  useSearchParams,
  useParams,
} from "react-router-dom";
const StockContext = createContext();

export const StockProvider = ({ children }) => {
  /**********************Kakao api 세팅****************** */
  const mapRef = useRef(null); // 지도를 담을 div의 ref
  const mapInstanceRef = useRef(null); //생성한 map instance를 저장 -- const map = new window.kakao.maps.Map(container, options);
  const itemMarkersRef = useRef([]); // 지도내 표시된 마커 배열 저장
  /***************side-panel 관련 상태변수들******************************** */
  const [isAsideVisible, setIsAsideVisible] = useState(false); // 지정한 side-panel 숨김여부 저장 state
  /***************매물(item) 로딩 관련 상태변수들******************************** */
  const [stockList, setStockList] = useState(null); // spring 서버에서 받아오는 매물 List

  const [clickedStockItem, setClickedStockItem] = useState(null); // 자세히 보기창에 띄울 매물
  /*****************검색창 관련 상태변수들************************** */
  /* searchKeyWord : 
   검색창SearchBar내부 매물 이름을 검색하기 위한 키워드를 저장하는 상태변수 - 기본값 ""(빈 문자열) */
  const [searchKeyWord, setSearchKeyWord] = useState(""); //
  const searchKeyWordRef = useRef(searchKeyWord); //  **중요**꼭 상태 변수를 따로 useRef로 마련해주고(일종의 state변수의 pointer역할), 그걸 addEventListener에 넣어야 변수의 값이 아니라 변수 그 자체를 참조한다.
  /* searchLoactionCode : 
  검색창SearchBar내부 REGION_NO 검색조건을 저장하는 상태변수 - 기본값 -1(전체 매물 표시), 
  00 - 두자릿수(ex. 11(서울특별시), 26(부산광역시))일경우는 해당 도의 전체 매물 조회.
  00000 - 다섯자리수(ex.11110(서울특별시 종로구), 11260(서울특별시 중랑구))일 경우에는 해당 구 한정 매물 조회.*/
  const [searchLocationCode, setSearchLocationCode] = useState(-1);
  const locationCodeRef = useRef(searchLocationCode); //  **중요**
  /*searchStockType : 
  검색창 내 매물 판매 유형(매매:0, 전세:1, 월세:2)을 저장하는 상태변수 - 기본값 -1(전체 매물 선택)*/
  const [searchStockType, setSearchStockType] = useState(-1);
  const searchStockTypeRef = useRef(searchStockType); //  **중요**
  /*searchStockForm : 
  검색창 내 부동산 유형(아파트:1, 빌라:2, 오피스텔:3)을 저장하는 상태변수 - 기본값 -1(전체 매물 선택)*/
  const [searchStockForm, setSearchStockForm] = useState(-1);
  const searchStockFormRef = useRef(searchStockForm); //  **중요**

  // 상세 디테일 페이지를 URL로 연결할 변수
  const navigate = useNavigate();
  /*******************마커 겹침 처리기능 관련 변수***************** */
  // ⚙️ 격자 셀의 크기를 설정 (화면 픽셀 기준)
  // 마커가 겹친다고 판단할 최소 거리보다 약간 큰 값이 좋습니다.
  const gridSize = 50;

  // 📦 각 셀에 어떤 마커들이 들어있는지를 저장하는 해시맵
  // 키: "셀X,셀Y", 값: 그 셀에 속한 마커들의 정보 배열
  const cellMap = {};
  /****************QueryString 기능 구현을 위한 변수******************************************** */
  const [searchParams] = useSearchParams();

  return (
    <StockContext.Provider
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
        searchStockType,
        setSearchStockType,
        searchStockTypeRef,
        searchStockForm,
        setSearchStockForm,
        searchStockFormRef,
        navigate,
        //gridsize -> state 변수가 아님!!
        //cellMap -> state 변수가 아님!!
        searchParams,
      }}
    >
      {children}
    </StockContext.Provider>
  );
};

export const useStockContext = () => useContext(StockContext);
