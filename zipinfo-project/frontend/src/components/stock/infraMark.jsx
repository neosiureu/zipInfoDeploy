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

  const [mapReady, setMapReady] = useState(false); // 실거래가 페이지상의 KakaoMap이
  useEffect(() => {
    const waitForMap = () => {
      const map = mapInstanceRef.current;
      if (map) {
        setMapReady(true);

        // ps 객체 활용 가능
      } else {
        // SDK 또는 mapInstance가 아직 준비되지 않은 경우 재시도
        setTimeout(waitForMap, 100); // 계속 기다림
        console.log("setTimeout(waitForMap) 실행중..");
      }
    };
    waitForMap();
  }, []);

  const [clickedCategory, setClickedCategory] = useState(-1); // 현재 클릭한 분류. 기본값 아무것도 선택하지 않았음.
  const placeRef = useRef(null); // 카카오 지도 api에서 제공하는 place객체를 여기에 저장

  useEffect(() => {
    // 장소 검색 객체를 생성합니다
    /*
    if (mapInstanceRef) {
      let ps = new window.kakao.maps.services.Places();
    }*/
    console.log("mapReady:", mapReady);

    if (mapReady) {
      const tryInit = () => {
        if (!mapInstanceRef.current) {
          console.log("mapInstanceRef 아직 로딩이 안됨!!");
          return;
        }
        const map = mapInstanceRef.current;
        placeRef.current = new window.kakao.maps.services.Places({
          map,
        });
        if (placeRef.current) {
          console.log("placeRef 저장됨!", placeRef.current);
        }

        // ps 객체 활용 가능
      };
      tryInit(); //mapReady = false일때는 tryInit()을 절대 실행시키지 말것.
    }
  }, [mapReady]);

  const [infraList, setInfraList] = useState();
  const [infraMarkerList, setInfraMarkerList] = useState([]); //주변 편의시설 Marker개체를 저장하는 STATE함수
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

  //주변시설 유형 클릭시 실행하는 함수.
  function onClickCategory(val) {
    setClickedCategory(val); //클릭한 분류 설정.
    console.log("clickedCategory:", clickedCategory);
    if (val === "") return; // "선택 없음"을 클릭했을떄는 ps.categorySearch() 실행안함
    placeRef.current.categorySearch(val, placesSearchCB, {
      //카카오 api에 주변정보 요청
      bounds: mapInstanceRef.current.getBounds(),
    });
  }

  // onClickCategory() 내부에서 장소검색(ps.categorySearch())이 완료됐을 때 호출되는 콜백함수 입니다
  /*@param : data	장소 정보 배열 (검색된 장소들)
    @param : status	검색 상태 (OK, ZERO_RESULT, ERROR 중 하나)
    @param : pagination	다음 페이지 정보 (더보기 기능 등 지원 가능)*/
  function placesSearchCB(data, status, pagination) {
    const displayPlaces = (data) => {
      // 몇번째 카테고리가 선택되어 있는지 얻어옵니다
      // 이 순서는 스프라이트 이미지에서의 위치를 계산하는데 사용됩니다
      data.forEach((item) => {
        // item : 선택한 category 인프라 정보

        const itemCoord = new kakao.maps.LatLng(item.y, item.x); // x, y가 거꾸로 된거아니야????

        /*********************** */

        const content = ` 
        <div class="custom-overlay" >
          <div class="area">${item.category_group_name}㎡</div>
          ${item.place_name}
        </div>
      `; // 커스텀 마커 저장

        //클릭 이벤트 리스너 바인딩을 위한 코드
        const customOverlay = document.createElement("div");
        customOverlay.innerHTML = content;
        /************************ */
        const itemMarker = new window.kakao.maps.CustomOverlay({
          position: itemCoord,
          content: customOverlay,
          yAnchor: 1,
        }); // 카카오 지도에 표시할 커스텀 마커 객체 생성
        setInfraMarkerList((infraMarkerList) => [
          ...infraMarkerList,
          itemMarker,
        ]);
        itemMarker.setMap(mapInstanceRef.current);
      });
    };

    if (status === kakao.maps.services.Status.OK) {
      // 정상적으로 검색이 완료됐으면 처리해야할 코드를 이곳에 작성해 주세요
      displayPlaces(data);
    } else if (status === kakao.maps.services.Status.ZERO_RESULT) {
      // 검색결과가 없는경우 해야할 처리가 있다면 이곳에 작성해 주세요
      displayPlaces(data);
    } else if (status === kakao.maps.services.Status.ERROR) {
      // 에러로 인해 검색결과가 나오지 않은 경우 해야할 처리가 있다면 이곳에 작성해 주세요
    }
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
