import { useEffect, useRef, useState } from "react"; // useRef 추가
import { axiosAPI } from "../../api/axiosApi";
import "../../css/stock/stockPage.css";
import "../../css/stock/infraMark.css";
import SearchBar from "../common/SearchBar";
import warning from "../../assets/circle_warning.svg"; // 미검색 결과 아이콘
import saleThumbnail from "../../assets/sale-page-thumbnail.svg"; // 썸네일 이미지 추가
import {
  useNavigate,
  useLocation,
  useSearchParams,
  useParams,
} from "react-router-dom";
import { useStockContext } from "./StockContext";

const InfraMark = () => {
  const {
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
  } = useStockContext();

  const [clickedCategory, setClickedCategory] = useState(-1); // 현재 클릭한 분류. 기본값 아무것도 선택하지 않았음.
  /*
  useEffect(() => {
    // 장소 검색 객체를 생성합니다
    let ps = new window.kakao.maps.services.Places();
  }, []);
*/

  useEffect(() => {
    //todo : Map의 Level이 5 이하일떄만 주변 편의시설을 표시할수 있도록.
  }, []);

  const handleZoomChanged = () => {
    const map = mapInstanceRef.current;
    if (!map) return;

    const handler = () => {
      const level = map.getLevel();
      console.log("지도 레벨 변경됨:", level);
      setZoomLevel(level); // 상태 저장하고 UI 반영 등
    };

    window.kakao.maps.event.addListener(map, "zoom_changed", handler);
  };
  function onClickCategory(val) {
    setClickedCategory(val); //클릭한 분류 설정.
    console.log("clickedCategory:", clickedCategory);
  }

  return (
    <aside id="category">
      <div
        id="None"
        className={`infra-item ${clickedCategory === "" ? "on" : ""}`}
        data-category=""
        onClick={() => onClickCategory("")}
      >
        <span className="category_bg none"></span>
        선택 없음
      </div>
      <div
        id="BK9"
        className={`infra-item ${clickedCategory === "BK9" ? "on" : ""}`}
        data-category="BK9"
        onClick={() => onClickCategory("BK9")}
      >
        <span className="category_bg bank"></span>
        은행
      </div>
      <div
        id="MT1"
        className={`infra-item ${clickedCategory === "MT1" ? "on" : ""}`}
        data-category="MT1"
        onClick={() => onClickCategory("MT1")}
      >
        <span className="category_bg mart"></span>
        마트
      </div>
      <div
        id="PM9"
        className={`infra-item ${clickedCategory === "PM9" ? "on" : ""}`}
        data-category="PM9"
        onClick={() => onClickCategory("PM9")}
      >
        <span className="category_bg pharmacy"></span>
        약국
      </div>
      <div
        id="OL7"
        className={`infra-item ${clickedCategory === "OL7" ? "on" : ""}`}
        data-category="OL7"
        onClick={() => onClickCategory("OL7")}
      >
        <span className="category_bg oil"></span>
        주유소
      </div>
      <div
        id="CE7"
        className={`infra-item ${clickedCategory === "CE7" ? "on" : ""}`}
        data-category="CE7"
        onClick={() => onClickCategory("CE7")}
      >
        <span className="category_bg cafe"></span>
        카페
      </div>
      <div
        id="CS2"
        className={`infra-item ${clickedCategory === "CS2" ? "on" : ""}`}
        data-category="CS2"
        onClick={() => onClickCategory("CS2")}
      >
        <span className="category_bg store"></span>
        편의점
      </div>
    </aside>
  );
};

export default InfraMark;
