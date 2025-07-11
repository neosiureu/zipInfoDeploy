import { useContext, useEffect, useRef, useState } from "react"; // useRef 추가
import { axiosAPI } from "../../api/axiosApi";
import "../../css/stock/stockPage.css";
import SearchBar from "../common/SearchBar";
import agent from "../../assets/agent-icon.svg"; // 중개사 아이콘
import warning from "../../assets/circle_warning.svg"; // 미검색 결과 아이콘
import { useStockContext } from "./StockContext";
import InfraMark from "./infraMark";
import { Bookmark } from "lucide-react";
import { MemberContext } from "../member/MemberContext";
import { useParams } from "react-router-dom";

const StockPageCopy = () => {
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
    isInfraCategoryVisible, // InfraMark.jsx에서 StockContext로 옮김
    setIsInfraCategoryVisible, // InfraMark.jsx에서 StockContext로 옮김
    isInfraCategoryVisibleRef, // InfraMark.jsx에서 StockContext로 옮김
  } = useStockContext();

  const { stockNo } = useParams(); // 매물번호를 주소에서 받아옴(/stock/:stockNo)

  // 매물번호 주소기능 구현중
  /*******************마커 겹침 처리기능 관련 변수***************** */
  // ⚙️ 격자 셀의 크기를 설정 (화면 픽셀 기준)
  // 마커가 겹친다고 판단할 최소 거리보다 약간 큰 값이 좋습니다.
  const gridSize = 50;

  // 📦 각 셀에 어떤 마커들이 들어있는지를 저장하는 해시맵
  // 키: "셀X,셀Y", 값: 그 셀에 속한 마커들의 정보 배열
  const cellMap = {};

  /*******************마커 겹침 처리기능 관련 함수***************** */
  // 📌 현재 마커의 화면 좌표가 속한 셀의 고유 키를 생성하는 함수
  function getCellKey(point) {
    const x = Math.floor(point.x / gridSize); // 셀 X좌표
    const y = Math.floor(point.y / gridSize); // 셀 Y좌표
    return `${x},${y}`; // 예: "3,5"
  }

  // 🔍 현재 셀 + 주변 8개 셀까지 포함한 총 9개 셀의 키를 반환
  // 이렇게 해야 셀 경계에 걸친 마커들도 겹침 여부를 정확히 판단할 수 있습니다.
  function getAdjacentCellKeys(point) {
    const cx = Math.floor(point.x / gridSize);
    const cy = Math.floor(point.y / gridSize);
    const keys = [];

    // 상하좌우 + 대각선 방향까지 포함
    for (let dx = -1; dx <= 1; dx++) {
      for (let dy = -1; dy <= 1; dy++) {
        keys.push(`${cx + dx},${cy + dy}`);
      }
    }

    return keys; // 총 9개의 셀 키
  }

  const { member } = useContext(MemberContext);

  const [likeStockList, setLikeStockList] = useState([]);

  const [likeStock, setLikeStock] = useState(new Set()); // 좋아요 매물 가져오기

  const getLikeStock = async () => {
    try {
      if (member !== null) {
        const response = await axiosAPI.get("/myPage/getLikeStock");
        setLikeStockList(response.data);
        setLikeStock(new Set(response.data.map((item) => item.stockNo)));
      }
    } catch (err) {
      console.error("매물 불러오기 실패:", err);
    }
  };

  const handleStockLike = async (stockNo) => {
    setLikeStock((prev) => {
      const updated = new Set(prev);
      if (updated.has(stockNo)) {
        updated.delete(stockNo); // 찜 해제
      } else {
        updated.add(stockNo); // 찜 추가
      }
      return updated;
    });
    try {
      if (likeStock.has(stockNo)) {
        const resp = await axiosAPI.post("/myPage/unlikeStock", { stockNo });
      } else {
        const resp = await axiosAPI.post("/myPage/likeStock", { stockNo });
      }
    } catch (err) {
      console.error("찜 상태 변경 실패:", err);
    }
  };

  /****************QueryString 기능 구현을 위한 변수******************************************** */
  //const [searchParams] = useSearchParams();
  /****************QueryString 기능 구현을 위한 검색바 초기화 함수 (구현중)******************************************** */
  /*useEffect(() => {
    const stockType = Number(searchParams.get("type")) || -1;
    const stockForm = Number(searchParams.get("form")) || -1;
    setSearchStockType(stockType);
    setSearchStockForm(stockForm);
  }, [searchParams]);*/

  useEffect(() => {
    // addEventListener만을 위한 코드. addEventListener 내부에서 state변수는 ref를 얻어오거나, 아니면 초기화해줘야 한다.
    searchKeyWordRef.current = searchKeyWord;
    locationCodeRef.current = searchLocationCode;
    searchStockFormRef.current = searchStockForm;
    searchStockTypeRef.current = searchStockType;
  }, [searchKeyWord, searchLocationCode, searchStockForm, searchStockType]); // 페이지 처음 로딩시 state변수의 ref 현재값(current) 초기화
  useEffect(() => {
    const setCoord = async () => {
      // SearchBar에 검색 Location이 변경될때 해당 지역을 보여주기 위한 useEffect()
      /***********실행전에 mapRef 초기화*********** */
      console.log("mapRef : ", mapRef.current);
      const container = mapRef.current; // 지도를 표시할 div
      const options = {
        center: new window.kakao.maps.LatLng(37.567937, 126.983001), // KH종로지원 대략적인 위도, 경도
        level: 7, // 지도의 확대 레벨
      };
      const map = new window.kakao.maps.Map(container, options);
      mapInstanceRef.current = map;
      /************************ */

      if ([...searchParams.entries()].length !== 0) {
        //searchParams가 비지 않았을때! (비엇을떄도 spring server에 request를 보낼 필요 없음!)
        console.log("coordsFromStock searchLocationCode: ", searchLocationCode);
        try {
          const resp = await axiosAPI.post(
            // 검색창에 있는 모든 조건 loading.
            "/stock/coordsFromStock",
            /*{
              searchKeyWord: searchKeyWordRef.current || "",
              locationCode: searchLocationCode.current ?? -1,
              stockType: searchStockForm.current ?? -1,
              stockForm: searchStockType.current ?? -1,
            }*/
            {
              searchKeyWord: searchParams.get("keyWord") || "",
              locationCode: Number(
                searchParams.get("sigungu") || searchParams.get("sido") || -1
              ),
              stockType: Number(searchParams.get("type") ?? -1),
              stockForm: Number(searchParams.get("form") ?? -1),
            }
          );
          console.log("coordsFromStock resp:", resp.data);
          if (resp.data) {
            const { latCenter, lngCenter, minLat, minLng, maxLat, maxLng } =
              resp.data; // 요청으로 얻어온 평균 좌표, 최소 lat, 최소 lng, 최대 lat, 최대 lng를 저장.
            const center = new window.kakao.maps.LatLng(latCenter, lngCenter);

            mapInstanceRef.current.setCenter(center);

            mapInstanceRef.current.setLevel(7);
          }
        } catch (error) {
          console.log(error);
        }
      }
    };
    setCoord();
  }, [searchParams]);
  /*********************Kakao map 로드 Kakao Map에 spring서버로 매물 리스트 요청하는 eventListener 추가************** */
  useEffect(() => {
    if (!mapInstanceRef.current) return;

    const bounds = mapInstanceRef.current.getBounds();
    const sw = bounds.getSouthWest();
    const ne = bounds.getNorthEast();

    const fetchData = async () => {
      try {
        console.log("API 요청 전 locationCode:", searchLocationCode);
        const resp = await axiosAPI.post("/stock/items", {
          coords: {
            swLat: sw.getLat(),
            swLng: sw.getLng(),
            neLat: ne.getLat(),
            neLng: ne.getLng(),
          },
          searchKeyWord: searchKeyWord || "",
          locationCode: searchLocationCode ?? -1,
          stockType: searchStockForm ?? -1,
          stockForm: searchStockType ?? -1,
        });

        if (resp.status === 200) {
          setStockList(resp.data);
        }
      } catch (error) {
        console.error("검색 조건 변경에 따른 요청 중 오류:", error);
      }
    };

    fetchData();
  }, [searchKeyWord, searchLocationCode, searchStockForm, searchStockType]);
  /*********************Kakao map 로드 & 초기화***************************/

  useEffect(() => {
    // 카카오 지도 API가 로드되었는지 확인
    if (window.kakao && window.kakao.maps) {
      const container = mapRef.current; // 지도를 표시할 div
      const options = {
        center: new window.kakao.maps.LatLng(37.567937, 126.983001), // KH종로지원 대략적인 위도, 경도
        level: 7, // 지도의 확대 레벨
      };
      const map = new window.kakao.maps.Map(container, options);
      mapInstanceRef.current = map; // ✅ map 저장
      //화면을 움직였을떄 서버에 itemList를 요청하는 addListener
      window.kakao.maps.event.addListener(map, "idle", async () => {
        // "bounds_changed는 마우스를 떼지 않아도 요청이 가기떄문에, 서버에 가는 요청의 개수가 너무 많음. "idle"을 쓰면 마우스가 떼어지면 요청을 보내게 수정함.
        const bounds = map.getBounds();
        const sw = bounds.getSouthWest();
        const ne = bounds.getNorthEast();

        console.log("현재 화면 범위:");
        console.log("좌하단(SW):", sw.getLat(), sw.getLng());
        console.log("우상단(NE):", ne.getLat(), ne.getLng());
        try {
          const resp = await axiosAPI.post("/stock/items", {
            coords: {
              swLat: sw.getLat(),
              swLng: sw.getLng(),
              neLat: ne.getLat(),
              neLng: ne.getLng(),
            },
            searchKeyWord: searchKeyWordRef.current || "", //keyword ||
            locationCode: locationCodeRef.current ?? -1, // -1 : 서버측에서 무시하는 value selectedLocation ||
            stockType: searchStockFormRef.current ?? -1, // -1 : 서버측에서 무시하는 valueselectedType ||
            stockForm: searchStockTypeRef.current ?? -1, // -1 : 서버측에서 무시하는 valueselectedForm ||
          });
          console.log("locationCode:", locationCodeRef.current);

          if (resp.status === 200) {
            console.log(resp.data);

            setStockList(resp.data);
            updateMarker();

            // same code : 매물 좌표를 받아서 지도상에 마커로 매물 위치 추가
          }
        } catch (error) {
          console.log("매물 items 조회 중 error 발생 : ", error);
        }
      });

      // 마커를 추가하고 싶다면 여기에 추가
      const markerPosition = new window.kakao.maps.LatLng(37.5451, 127.0425);
      const marker = new window.kakao.maps.Marker({
        position: markerPosition,
      });

      marker.setMap(map);
    }

    getLikeStock();
  }, []);

  useEffect(() => {
    updateMarker();
  }, [stockList]); // stockList(맨 왼쪽에 있는 매물 Item들을 저장하는 state변수), searchLocationCode(검색창SearchBox에서 선택한 지역을 저장하는 state변수)
  // updateMarker : 요청을 보낼때마다 지도에 표시되는 마커들을 새로 세팅하는 함수
  const updateMarker = () => {
    const map = mapInstanceRef.current;
    itemMarkersRef.current.forEach((marker) => marker.setMap(null)); // 이전에 itemMarkersRef에 저장해둔 markers 하나하나 취소
    itemMarkersRef.current = []; // itemMarkersRef 초기화
    stockList?.forEach((item) => {
      const itemMarkerPosition = new window.kakao.maps.LatLng(
        item.lat,
        item.lng
      );
      // /********************todo : 여기부터 겹치는 마커 처리로직 입력할것.*************************
      //  * ******해시격자 로직******
      //  * 지금 보는 kakao Map을 일정 간격을 가진 격자로 분해하여
      //  * 매물이 소속된 격자와 인접 격자내부에 지금까지 불러운 모든 매물들을 불러와 겹치는지 확인
      //  *
      //  * □□□
      //  * □■□
      //  * □□□
      //  */
      // //screenPoint : 현재 item의 lat/lng를 screen상의 좌표를 저장함
      // const screenPoint = map
      //   .getProjection()
      //   .containerPointFromCoords(itemMarkerPosition); // 📍지도 좌표 → 화면 좌표(px) 변환
      // //🔎 주변 셀 9개 키 가져오기
      // const nearbyKeys = getAdjacentCellKeys(screenPoint); // 🔎 주변 셀 9개 키 가져오기
      // let isOverlapping = false; // 겹침 여부 초기화
      // let overlappingTarget = null; // 혹시 이미 불러온 item들중 겹치는 것이 있다면 여기다가 저장.
      // // 🧩 불러온 주변 셀들을 순회하며 겹치는 오버레이가 있는지 검사
      // for (const key of nearbyKeys) {
      //   const cell = cellMap[key];
      //   if (!cell) continue;

      //   for (const other of cell) {
      //     const dx = screenPoint.x - other.point.x;
      //     const dy = screenPoint.y - other.point.y;
      //     const dist = Math.sqrt(dx * dx + dy * dy);

      //     if (dist < 40) {
      //       // 만약 두 매물간의 거리가 40 이하라면
      //       // 🔴 실제 겹침 판단 거리 기준 (px)
      //       isOverlapping = true;
      //       break;
      //     }
      //   }
      //   if (isOverlapping) break;
      // }

      // if (!isOverlapping) {
      //   //***************************** */ ✅ 겹치지 않는 경우 → 셀에 마커 정보 저장
      //   const cellKey = getCellKey(screenPoint);
      //   if (!cellMap[cellKey]) cellMap[cellKey] = [];

      //   // 좌표와 매물 정보를 셀에 등록
      //   cellMap[cellKey].push({ point: screenPoint, item: item });

      //   // 🟢 여기에 커스텀 오버레이 생성 로직 추가
      //   const content = `
      //   <div class="custom-overlay" >
      //     <div class="area">${item.exclusiveArea}㎡</div>
      //     ${
      //       item.stockType === 0
      //         ? `<div class="label">
      //           매매 <strong>${priceConvertToString(
      //             item.stockSellPrice
      //           )}</strong>
      //           </div>`
      //         : item.stockType === 1
      //         ? `<div class="label">
      //           전세 <strong>${priceConvertToString(
      //             item.stockSellPrice
      //           )}</strong>
      //           </div>`
      //         : item.stockType === 2
      //         ? `<div class="label">
      //           월세 <strong>${priceConvertToString(
      //             item.stockSellPrice
      //           )}/${priceConvertToString(item.stockFeeMonth)}</strong>
      //           </div>`
      //         : "기타 "
      //     }
      //   </div>
      // `; // 커스텀 마커 저장
      //   //클릭 이벤트 리스너 바인딩을 위한 코드
      //   const customOverlay = document.createElement("div");
      //   customOverlay.innerHTML = content;

      //   // ㄴ 여기서 직접 이벤트 바인딩
      //   customOverlay
      //     .querySelector(".custom-overlay")
      //     .addEventListener("click", (item, index) => {
      //       console.log(`${item.index} clicked`);
      //       handleItemClick(item, index);
      //     });

      //   const itemMarker = new window.kakao.maps.CustomOverlay({
      //     position: itemMarkerPosition,
      //     content: customOverlay,
      //     yAnchor: 1,
      //   }); // 카카오 map에 커스텀오버레이 등록
      //   itemMarker.setMap(map);
      //   itemMarkersRef.current.push(itemMarker);
      // } else {
      //   //********************************* */ ❌ 겹치는 경우 → 생략하거나, 클러스터 오버레이를 만들 수도 있음
      //   console.log(`❗ 겹치는 마커 발생: ${item.id}`);
      // }

      // /********************end of 겹침처리****************************************************************** */

      const content = `
      <div class="custom-overlay" >
        <div class="area">${item.exclusiveArea}㎡</div>
        ${
          item.stockType === 0
            ? `<div class="label">
              매매 <strong>${priceConvertToString(item.stockSellPrice)}</strong>
              </div>`
            : item.stockType === 1
            ? `<div class="label">
              전세 <strong>${priceConvertToString(item.stockSellPrice)}</strong>
              </div>`
            : item.stockType === 2
            ? `<div class="label">
              월세 <strong>${priceConvertToString(
                item.stockSellPrice
              )}/${priceConvertToString(item.stockFeeMonth)}</strong>
              </div>`
            : "기타 "
        }
      </div>
    `; // 커스텀 마커 저장
      //클릭 이벤트 리스너 바인딩을 위한 코드
      const customOverlay = document.createElement("div");
      customOverlay.innerHTML = content;

      // 여기서 직접 이벤트 바인딩(클릭한번)
      customOverlay
        .querySelector(".custom-overlay")
        .addEventListener("click", () => {
          handleItemClick(item);
        });
      // 여기서 직접 이벤트 바인딩(더블클릭)
      customOverlay
        .querySelector(".custom-overlay")
        .addEventListener("dblclick", () => {
          handleItemClick(item);
        });

      const itemMarker = new window.kakao.maps.CustomOverlay({
        position: itemMarkerPosition,
        content: customOverlay,
        yAnchor: 1,
      }); // 카카오 map에 커스텀오버레이 등록
      itemMarker.setMap(map);
      itemMarkersRef.current.push(itemMarker); // 새 마커 저장*/
    });
  };

  const subDate = (e) => {
    return e.substring(0, 10);
  };

  useEffect(() => {
    // kakao map이 로딩된 후에 SearchBar 관련 검색 매개변수들이 바뀔때마다 서버에 post요청으로 매물정보를 다시 받아오는 함수. -> setStockList(), updateMarker() 다시 실행함!
    const fetchData = async () => {
      //SearchBar의 조건이 바뀔때마다 다시요청.
      const bounds = mapInstanceRef.current.getBounds(); // 현재 맵 인스턴스에서 getBounds() 실행
      const sw = bounds.getSouthWest();
      const ne = bounds.getNorthEast();

      try {
        const resp = await axiosAPI.post("/stock/items", {
          coords: {
            swLat: sw.getLat(),
            swLng: sw.getLng(),
            neLat: ne.getLat(),
            neLng: ne.getLng(),
          },
          searchKeyWord: searchKeyWordRef.current || "", //keyword ||
          locationCode: locationCodeRef.current ?? -1, // -1 : 서버측에서 무시하는 value selectedLocation ||
          stockType: searchStockFormRef.current ?? -1, // -1 : 서버측에서 무시하는 valueselectedType ||
          stockForm: searchStockTypeRef.current ?? -1, // -1 : 서버측에서 무시하는 valueselectedForm ||
        });
        console.log("매물:", resp.data);
        if (resp.status === 200) {
          console.log(resp.data);

          setStockList(resp.data);
          updateMarker();

          // same code : 매물 좌표를 받아서 지도상에 마커로 매물 위치 추가
        }
      } catch (error) {
        console.log("매물 items 조회 중 error 발생 : ", error);
      }
    };
    fetchData();
  }, [searchKeyWord, searchLocationCode, searchStockType, searchStockForm]);

  // 매물 item을 클릭했을떄 수행되는 핸들러 함수
  const handleItemClick = async (item) => {
    setClickedStockItem(item); // 클릭한 item의 index를 저장.
    setIsAsideVisible(true); //클릭시 상세창 표시=true 함.

    //map?.setDraggable(false); // 사용자가 지도를 드래그하지 못하게 막음!
    if (member !== null) {
      const resp = await axiosAPI.post("/myPage/addSawStock", {
        memberNo: member.memberNo,
        stockNo: item.stockNo,
      });
    }

    navigate(`/stock/${item.stockNo}`);
    console.log("stockNo:", item.stockNo);
  };
  const handleItemdblClick = async (item) => {
    var coord = new kakao.maps.LatLng(item.lat, item.lng);
    //mapInstanceRef.current.setLevel(4); // 4레벨로 줌 후
    mapInstanceRef.current.panTo(coord); // 이동 애니메이션 설정
  };
  const closeStockDetail = () => {
    setIsAsideVisible(false);
    setClickedStockItem(null);
    navigate("/stock", { replace: true });
  };
  //  URL에 매물 번호가 있을 경우 상세 정보 표시 --> 파쿠리!!
  useEffect(() => {
    const fetchDetail = async () => {
      try {
        const res = await axiosAPI.get("/stock/detail", {
          params: { stockNo },
        });
        if (res.status === 200) {
          setClickedStockItem(res.data);
          setIsAsideVisible(true);
          const movedLatLng = new window.kakao.maps.LatLng(
            res.data.lat,
            res.data.lng
          );

          mapInstanceRef.current.panTo(movedLatLng); // 해당 매물 위치로 이동
          mapInstanceRef.current.setDraggable(true);
          mapInstanceRef.current.setZoomable(true);
          /*
          const map = mapInstanceRef.current;
          const shouldFocus = searchParamsLocal.get("focus") === "true";

          if (shouldFocus && map && res.data.lat && res.data.lng) {
            const offsetLng = -0.07; // 기존 지도 이동 기준
            const movedLatLng = new window.kakao.maps.LatLng(
              res.data.lat,
              res.data.lng + offsetLng
            );
            map.panTo(movedLatLng); // 해당 매물 위치로 이동
          }

          updateMarker(stockList); // 전체 마커 다시 그림*/
        }
      } catch (e) {
        console.error("상세 매물 조회 실패:", e);
      }
    };

    if (stockNo) fetchDetail();
  }, [stockNo]);
  //updateMarker() 뒤에 queryString 조건에 따라 화면전환하는 useEffect() 사용

  const StockItemDetail = ({ item }) => {
    if (item) {
      //null 오류 방지

      const stockFormMap = {
        //same code:  매물 형태(아파트, 빌라, 오피스텔 중 하나)를 int형에서 string으로 변환
        1: "아파트",
        2: "빌라",
        3: "오피스텔",
      };
      const stockForm = stockFormMap[item.stockForm] || "기타";

      return (
        <>
          <div className="stock-detail-panel">
            {/* 상단 이미지 2장 (예시) */}
            {/*<div className="stock-detail-images">
              <img
                src={`http://localhost:8080${item.imgUrls[0]}`}
                alt="상세1"
                className="stock-detail-mainimg"
              />
              <img
                src={`http://localhost:8080${item.imgUrls[2]}`}
                alt="상세2"
                className="stock-detail-mainimg"
              />
            </div>*/}
            {item?.imgUrls?.length >= 3 ? (
              <div className="stock-detail-panel">
                <div className="stock-detail-images">
                  <img
                    src={`http://localhost:8080${item.imgUrls[0]}`}
                    alt="상세1"
                    className="stock-detail-mainimg"
                  />
                  <img
                    src={`http://localhost:8080${item.imgUrls[2]}`}
                    alt="상세2"
                    className="stock-detail-mainimg"
                  />
                </div>
              </div>
            ) : (
              <p>이미지를 불러오는 중입니다...</p>
            )}
            <div className="sale-section-divider" />

            {/* Block 1: 매매/가격/찜 */}

            <div className="stock-detail-info-block">
              <div className="stock-detail-header">
                <span className="stock-detail-type">
                  {item.stockType === 0
                    ? "매매 "
                    : item.stockType === 1
                    ? "전세 "
                    : item.stockType === 2
                    ? "월세 "
                    : "기타 "}
                </span>
                <span className="stock-detail-price">
                  {item.stockType === 0
                    ? priceConvertToString(item.stockSellPrice)
                    : item.stockType === 1
                    ? priceConvertToString(item.stockSellPrice)
                    : item.stockType === 2
                    ? " " +
                      priceConvertToString(item.stockSellPrice) +
                      " / " +
                      priceConvertToString(item.stockFeeMonth) +
                      " "
                    : "기타"}
                </span>
                {member && member.memberNo !== null ? (
                  <button
                    onClick={() => {
                      handleStockLike(item.stockNo);
                    }}
                    className="stock-detail-like-btn"
                    aria-label="찜하기"
                  >
                    <Bookmark
                      className={`like-stock-bookmark ${
                        likeStock.has(item.stockNo) ? "active" : ""
                      }`}
                    />
                  </button>
                ) : (
                  <div />
                )}
              </div>
              <div className="stock-detail-name">{item.stockName}</div>
              <div className="stock-detail-desc">
                {item.stockInfo}
                <button
                  className="stock-around-info-btn"
                  onClick={async () => {
                    var coord = new kakao.maps.LatLng(item.lat, item.lng);
                    await mapInstanceRef.current.setLevel(4); // 4레벨로 줌 후
                    // 🔽 약간의 지연을 주자 (줌 적용 후 panTo 애니메이션 작동하도록)
                    await new Promise((resolve) => setTimeout(resolve, 300)); // 300ms 딜레이
                    await mapInstanceRef.current.panTo(coord); // 이동 애니메이션 설정

                    setIsInfraCategoryVisible(!isInfraCategoryVisible);
                  }}
                >
                  주변시설 {isInfraCategoryVisible ? "보지 않기" : "보기"}
                </button>
              </div>
            </div>

            <div className="sale-section-divider" />

            {/* Block 2: 평면도 */}
            <div className="stock-detail-info-block">
              <div className="stock-detail-plan">
                {item?.imgUrls?.[1] > 1 && (
                  <img
                    src={`http://localhost:8080${item.imgUrls[1]}`}
                    alt="평면도 이미지"
                  />
                )}
              </div>
            </div>

            <div className="sale-section-divider" />

            {/* Block 3: 상세정보 */}
            <div className="stock-detail-info-block">
              <div className="stock-detail-section">
                <div className="stock-detail-section-title">상세정보</div>
                <table className="stock-detail-table">
                  <tbody>
                    <tr>
                      <td>매물형태</td>
                      <td>{stockFormMap[item.stockForm]}</td>
                    </tr>
                    <tr>
                      <td>주소</td>
                      <td>{item.stockAddress.split("^^^")[1]}</td>
                    </tr>
                    <tr>
                      <td>전용/공급면적</td>
                      <td>
                        {item.exclusiveArea}㎡ / {item.supplyArea}㎡
                      </td>
                    </tr>
                    <tr>
                      <td>해당층/건물층</td>
                      <td>
                        {item.currentFloor} / {item.floorTotalCount}층
                      </td>
                    </tr>
                    <tr>
                      <td>방/욕실 수</td>
                      <td>
                        {item.roomCount} / {item.bathCount}개
                      </td>
                    </tr>
                    <tr>
                      <td>방향</td>
                      <td>{item.stockDirection}</td>
                    </tr>
                    <tr>
                      <td>관리비</td>
                      <td>{item.stockManageFee}</td>
                    </tr>
                    <tr>
                      <td>입주가능일</td>
                      <td>{subDate(item.ableDate)}</td>
                    </tr>
                    <tr>
                      <td>사용승인일</td>
                      <td>{subDate(item.useApprovalDate)}</td>
                    </tr>
                    <tr>
                      <td>최초등록일</td>
                      <td>{subDate(item.registDate)}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <div className="sale-section-divider" />

            {/* Block 4: 상세설명 */}
            <div className="stock-detail-info-block">
              <div className="stock-detail-section">
                <div className="stock-detail-section-title">상세설명</div>
                <div className="stock-detail-description">
                  {item.stockDetail}
                </div>
              </div>
            </div>

            <div className="sale-section-divider" />

            {/* Block 5: 중개사무소 정보 */}
            <div className="stock-detail-info-block stock-detail-office">
              <div className="stock-detail-section">
                <div className="stock-detail-section-title">
                  중개사무소 정보
                </div>
                <table className="stock-detail-table">
                  <tbody>
                    <tr>
                      <td>이름</td>
                      <td>{item.companyName}</td>
                    </tr>
                    <tr>
                      <td>주소</td>
                      <td>{item.companyLocation?.split("^^^")[1]}</td>
                    </tr>
                    <tr>
                      <td>대표</td>
                      <td>{item.presidentName}</td>
                    </tr>
                    <tr>
                      <td>중개등록번호</td>
                      <td>{item.brokerNo}</td>
                    </tr>
                    <tr>
                      <td>대표번호</td>
                      <td>{item.presidentPhone}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </>
      );
    }
  };
  const StockList = ({ stockList }) => {
    const typeMap = {
      1: "아파트 ",
      2: "빌라 ",
      3: "오피스텔 ",
    };
    return (
      <section className="item-list">
        {stockList?.length === 0 ? (
          <div className="no-result">
            <img src={warning} alt="경고 이미지" />
            <p>
              조건에 맞는 매물이 없습니다.
              <br />
              위치 및 맞춤 필터를 조정해 보세요.
            </p>
          </div>
        ) : (
          stockList?.map((item, index) => (
            <div
              className="stock-item-list"
              onClick={() => handleItemClick(item, index)}
            >
              <div className="stock-header">
                <img
                  src={`http://localhost:8080${item.imgUrls[0]}`}
                  alt="썸네일"
                  className="stock-img"
                />
                <div>
                  <div className="stock-item-price">
                    <span className="item-type">
                      {item.stockType === 0
                        ? "매매 "
                        : item.stockType === 1
                        ? "전세 "
                        : item.stockType === 2
                        ? "월세 "
                        : "기타 "}
                    </span>
                    <span>&nbsp;</span> {/* 띄어쓰기용 */}
                    <span className="item-price">
                      {item.stockType === 0
                        ? priceConvertToString(item.stockSellPrice)
                        : item.stockType === 1
                        ? priceConvertToString(item.stockSellPrice)
                        : item.stockType === 2
                        ? " " +
                          priceConvertToString(item.stockSellPrice) +
                          " / " +
                          priceConvertToString(item.stockFeeMonth) +
                          " "
                        : "기타"}
                    </span>
                  </div>

                  <div className="stock-item-name">
                    {/**매물 이름 */}
                    {typeMap[Number(item.stockForm)] || "기타 "} ·{" "}
                    {item.stockName}
                  </div>

                  <div className="stock-item-summary">
                    {item.currentFloor}/{item.floorTotalCount}층<span> | </span>
                    {item.exclusiveArea}㎡<span> | </span>관리비{" "}
                    {item.stockManageFee !== 0
                      ? `${item.stockManageFee / 10000}만원`
                      : "없음"}
                  </div>
                  <div className="stock-item-info">
                    {item.stockInfo.length > 16
                      ? item.stockInfo.slice(0, 16) + ".."
                      : item.stockInfo}
                  </div>
                  <div className="item-font-broker">
                    <span>
                      <img src={agent} alt="중개사 아이콘" />
                    </span>
                    {item.companyName}
                  </div>
                </div>
              </div>
            </div>
          ))
        )}
      </section>
    );
  };
  /************주소의 stock/:stockNo에 따라 detail창 보여주지 말지 결정. */
  /*useEffect(() => {
    console.log("stockNo : ", stockNo);

    setClickedStockItem(stockNo);
    if (stockNo) setIsAsideVisible(true);
  }, [stockNo]);*/
  /******************매물 List 초기화***************** **/
  /*
  const [stockItems, setStockItems] = useState(null);

  // 매물 List 불러오는 함수 (매개변수 추가 필요)
  const getItemList = async () => {
    try {
      const resp = await axiosApi.get("/admin/withdrawnMemberList");

      console.log(resp.data);
      if (resp.status === 200) {
        setWithdrawnMembers(resp.data);
      }
    } catch (error) {
      console.log("탈퇴 회원 목록 조회 중 에러 발생 : ", error);
    }
  };*/
  /****************** return ***************** **/
  return (
    <>
      <SearchBar
        showSearchType={true}
        searchKeyWord={searchKeyWord}
        setSearchKeyWord={setSearchKeyWord}
        searchLocationCode={searchLocationCode}
        setSearchLocationCode={setSearchLocationCode}
        searchStockForm={searchStockForm}
        setSearchStockForm={setSearchStockForm}
        searchStockType={searchStockType}
        setSearchStockType={setSearchStockType}
      />{" "}
      {/**showSearchType : 현재 페이지가 StockPage인가, SalePage인가 따지는 변수 */}
      {/**list */}
      <div className="container">
        <aside className="side-panel">
          <StockList stockList={stockList} />
        </aside>

        {/**detail */}
        {isAsideVisible && (
          <>
            <aside className="stock-detail-panel detail-panel">
              <StockItemDetail item={clickedStockItem} />
            </aside>
            <button className="stock-close-button" onClick={closeStockDetail}>
              ✕
            </button>
          </>
        )}
        <InfraMark mapInstanceRef={mapInstanceRef} />
        <main className="map-area" ref={mapRef}>
          {/* 카카오 맵이 여기에 렌더링됩니다. */}
        </main>
      </div>
    </>
  );
};

export default StockPageCopy;

/* priceConvertToString()
  int형인 price를 한글 String으로 보기좋게 바꿈 (억 만 천 단위로 )
  ex. 4,0000,0000 -> 4억
  ex. 750,000,000 -> 7억 5천
*/
const priceConvertToString = (price) => {
  let resultString = "";

  if (Number.isInteger(price) && price > 0) {
    const eok = Math.floor(price / 100000000);
    if (eok > 0) {
      resultString += `${eok}억 `;
      price %= 100000000;
    }

    const man = Math.floor(price / 10000);
    if (man > 0) {
      resultString += man;
      price %= 10000;
    }
    const baek = Math.floor(price / 1000000);

    if (price / 10000 > 0) {
      resultString += "만";
      price %= 10000;
    }

    if (price % 10000 > 0) {
      resultString += price;
    }
  }
  return resultString.trim();
};
