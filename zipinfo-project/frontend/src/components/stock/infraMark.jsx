import { useEffect, useRef, useState } from "react"; // useRef 추가
import { axiosAPI } from "../../api/axiosApi";
import "../../css/stock/stockPage.css";
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

// 장소 검색 객체를 생성합니다
var ps = new kakao.maps.services.Places(map);

// 커스텀 오버레이의 컨텐츠 노드에 css class를 추가합니다
contentNode.className = "placeinfo_wrap";
// 커스텀 오버레이 컨텐츠를 설정합니다
placeOverlay.setContent(contentNode);

// 장소검색이 완료됐을 때 호출되는 콜백함수 입니다
function placesSearchCB(data, status, pagination) {
  if (status === kakao.maps.services.Status.OK) {
    // 정상적으로 검색이 완료됐으면 지도에 마커를 표출합니다
    displayPlaces(data);
  } else if (status === kakao.maps.services.Status.ZERO_RESULT) {
    // 검색결과가 없는경우 해야할 처리가 있다면 이곳에 작성해 주세요
  } else if (status === kakao.maps.services.Status.ERROR) {
    // 에러로 인해 검색결과가 나오지 않은 경우 해야할 처리가 있다면 이곳에 작성해 주세요
  }
}

// 마커를 생성하고 지도 위에 마커를 표시하는 함수입니다
export function addMarker(position, order) {
  var imageSrc =
      "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/places_category.png", // 마커 이미지 url, 스프라이트 이미지를 씁니다
    imageSize = new kakao.maps.Size(27, 28), // 마커 이미지의 크기
    imgOptions = {
      spriteSize: new kakao.maps.Size(72, 208), // 스프라이트 이미지의 크기
      spriteOrigin: new kakao.maps.Point(46, order * 36), // 스프라이트 이미지 중 사용할 영역의 좌상단 좌표
      offset: new kakao.maps.Point(11, 28), // 마커 좌표에 일치시킬 이미지 내에서의 좌표
    },
    markerImage = new kakao.maps.MarkerImage(imageSrc, imageSize, imgOptions),
    marker = new kakao.maps.Marker({
      position: position, // 마커의 위치
      image: markerImage,
    });

  marker.setMap(map); // 지도 위에 마커를 표출합니다
  markers.push(marker); // 배열에 생성된 마커를 추가합니다

  return marker;
}

// 지도 위에 표시되고 있는 마커를 모두 제거합니다
export function removeMarker() {
  for (var i = 0; i < markers.length; i++) {
    markers[i].setMap(null);
  }
  markers = [];
}

export const infraMarkSelect = () => (
  <div class="map_wrap">
    <div
      id="map"
      style="width:100%;height:100%;position:relative;overflow:hidden;"
    ></div>
    <ul id="category">
      <li id="BK9" data-order="0">
        <span class="category_bg bank"></span>
        은행
      </li>
      <li id="MT1" data-order="1">
        <span class="category_bg mart"></span>
        마트
      </li>
      <li id="PM9" data-order="2">
        <span class="category_bg pharmacy"></span>
        약국
      </li>
      <li id="OL7" data-order="3">
        <span class="category_bg oil"></span>
        주유소
      </li>
      <li id="CE7" data-order="4">
        <span class="category_bg cafe"></span>
        카페
      </li>
      <li id="CS2" data-order="5">
        <span class="category_bg store"></span>
        편의점
      </li>
    </ul>
  </div>
);
