import { useEffect, useRef, useState } from "react"; // useRef 추가
import { axiosAPI } from "../../api/axiosApi";
import "../../css/stock/stockPage.css";
import SearchBar from "../common/SearchBar";
import warning from "../../assets/circle_warning.svg"; // 미검색 결과 아이콘
import saleThumbnail from "../../assets/sale-page-thumbnail.svg"; // 썸네일 이미지 추가
import { useNavigate, useLocation, useSearchParams } from "react-router-dom";
const StockPage = () => {
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

  /****************QueryString 기능 구현을 위한 변수******************************************** */
  const [searchParams] = useSearchParams();
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
      let fullName = null; // 요청으로 얻어온 시군구 이름을 여기다 저장
      //1. 지역 코드를 매개변수로 Spring 서버에 시군구 이름을 요청한다. ( ex. req : 11110 -> resp : "종로구")
      if (searchParams.get("sigungu")) {
        // queryString에 sigungu가 존재한다면

        try {
          const resp = await axiosAPI.post(
            "/stock/getSigunguFullName",
            searchParams.get("sigungu")
          );
          fullName = resp.data;
        } catch (error) {
          console.log("error:", error);
          return;
        }
      }

      //2. 이렇게 얻은 지역코드를 카카오 api로 요청하여 반환되는 좌표를 받는다. (ex. 서울시 종로구 -> lat, lng)
      if (fullName) {
      }
      //3. 지금 보고있는 Map의 위치를 이동시킨다.
    };
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
        const resp = await axiosAPI.post("/stock/selectItems", {
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
        center: new window.kakao.maps.LatLng(37.5451, 127.0425), // 아크로서울포레스트아파트 대략적인 위도, 경도
        level: 3, // 지도의 확대 레벨
      };
      const map = new window.kakao.maps.Map(container, options);
      mapInstanceRef.current = map; // ✅ map 저장
      //화면을 움직였을떄 서버에 itemList를 요청하는 addListener
      window.kakao.maps.event.addListener(map, "bounds_changed", async () => {
        // "bounds_changed는 마우스를 떼지 않아도 요청이 가기떄문에, 서버에 가는 요청의 개수가 너무 많음. "idle"을 쓰면 마우스가 떼어지면 요청을 보내게 수정함.
        const bounds = map.getBounds();
        const sw = bounds.getSouthWest();
        const ne = bounds.getNorthEast();

        console.log("현재 화면 범위:");
        console.log("좌하단(SW):", sw.getLat(), sw.getLng());
        console.log("우상단(NE):", ne.getLat(), ne.getLng());
        try {
          const resp = await axiosAPI.post("/stock/selectItems", {
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

      // 여기서 직접 이벤트 바인딩
      customOverlay
        .querySelector(".custom-overlay")
        .addEventListener("click", (item, index) => {
          console.log(`${item.index} clicked`);
          handleItemClick(item, index);
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

  useEffect(() => {
    // kakao map이 로딩된 후에 SearchBar 관련 검색 매개변수들이 바뀔때마다 서버에 post요청으로 매물정보를 다시 받아오는 함수. -> setStockList(), updateMarker() 다시 실행함!
    const fetchData = async () => {
      //SearchBar의 조건이 바뀔때마다 다시요청.
      const bounds = mapInstanceRef.current.getBounds(); // 현재 맵 인스턴스에서 getBounds() 실행
      const sw = bounds.getSouthWest();
      const ne = bounds.getNorthEast();

      console.log("현재 화면 범위:");
      console.log("좌하단(SW):", sw.getLat(), sw.getLng());
      console.log("우상단(NE):", ne.getLat(), ne.getLng());
      try {
        const resp = await axiosAPI.post("/stock/selectItems", {
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
    };
    fetchData();
  }, [searchKeyWord, searchLocationCode, searchStockType, searchStockForm]);

  // 매물 item을 클릭했을떄 수행되는 핸들러 함수
  const handleItemClick = (item, index) => {
    setIsAsideVisible(true); //클릭시 상세창 표시=true 함.
    setClickedStockItem(item); // 클릭한 item의 index를 저장.
    //map?.setDraggable(false); // 사용자가 지도를 드래그하지 못하게 막음!
  };
  const closeStockDetail = () => {
    setIsAsideVisible(false);
    setClickedStockIndex(null);
  };

  const StockItemDetail = ({ item }) => {
    /*let stockForm; // 매물 형태(아파트, 빌라, 오피스텔 중 하나)를 int형에서 string으로 변환
    switch (item.stockForm) {
      case 1: // 아파트
        stockForm = "아파트";
        break;
      case 2: // 빌라
        stockForm = "빌라";
        break;
      case 3: // 오피스텔
        stockForm = "오피스텔";
        break;
      default: // 기타
        stockForm = "기타";
    }*/

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
          <div>
            {/** detail 창닫기 버튼 */}
            <button
              style={{ float: "right" }}
              onClick={() => setIsAsideVisible(false)}
            >
              X
            </button>
          </div>
          <div></div>
          <div className="stock-header">
            <img
              src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https://blog.kakaocdn.net/dn/bQwQwA/btrb1QwQwQw/1.jpg"
              alt="아파트"
              className="stock-img"
            />
            <div className="stock-title">
              <div className="stock-name">
                {item.stockName},{item.stockType}
              </div>
              <div className="stock-address">{item?.stockAddress}</div>
            </div>
          </div>
          <div
            className="stock-img-overview"
            style={{
              margin: "20px 0px",
              padding: "20px 0px",
              borderBottom: "1px solid #eee",
            }}
          >
            <p>평면도</p>
            <div>
              <img
                src="https://www.apt2you.com/images/apt/apt2you/apt2you_apt_1.png"
                alt="평면도"
                className="plan-img"
                style={{
                  margin: "20px 0px",
                  padding: "20px 0px",
                }}
              />
            </div>
          </div>

          <div className="section">
            <div className="section-title">기본정보</div>
            <table>
              <tbody>
                <tr>
                  <td>매물형태</td>
                  <td>{stockForm}</td>
                </tr>
                <tr>
                  <td>주소</td>
                  <td>{item?.stockAddress}</td>
                </tr>

                <tr>
                  <td>전용/공급 면적</td>
                  <td>
                    {item.exclusiveArea}㎡ / {item.supplyArea}㎡
                  </td>
                </tr>
                <tr>
                  <td>방/화장실 수</td>
                  <td>
                    {item.roomCount}개 / {item.bathCount}개
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
                  <td>{item.ableDate}</td>
                </tr>
                <tr>
                  <td>사용승인일</td>
                  <td>{item.useApprovalDate}</td>
                </tr>
                <tr>
                  <td>최초등록일</td>
                  <td>{item.registDate}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div className="section">
            <div className="section-title">상세정보</div>
            <table>
              <tbody>
                <tr>
                  <td>{item.stockDetail}</td>
                </tr>
              </tbody>
            </table>

            <div></div>
          </div>
          <div className="section">
            <div className="section-title">중개사무소 정보</div>
          </div>
        </>
      );
    }
  };
  const StockList = ({ stockList }) => {
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
              className="stock-title"
              onClick={() => handleItemClick(item, index)}
            >
              <img src={saleThumbnail} alt="썸네일" className="stock-img" />
              <div
                style={{
                  display: "flex",
                  flexDirection: "column",
                  gap: "10px",
                }}
              >
                <div
                  style={{
                    display: "flex",
                    flexDirection: "row",
                    gap: "10px",
                  }}
                >
                  <div className="item-type">
                    {item.stockType === 0
                      ? "매매 "
                      : item.stockType === 1
                      ? "전세 "
                      : item.stockType === 2
                      ? "월세 "
                      : "기타 "}
                  </div>
                  <div className="item-price">
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
                  </div>
                </div>

                <div className="item-name item-font-default">
                  {/**매물 이름 */}
                  {item.stockName}
                </div>

                <div className="item-font-default">
                  {item.exclusiveArea}㎡ | {item.currentFloor}층/{" "}
                  {/**여기 한글자 오타났었어요... */}
                  {item.floorTotalCount}층 | 관리비 {item.stockManageFee}원
                </div>
                <div className="item-font-default">
                  {/**매물 주소 */}
                  {item.stockAddress}
                </div>
                <div className="item-font-broker"> ⌂뭉탱이공인중개사사무소</div>
              </div>
            </div>
          ))
        )}
      </section>
    );
  };

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
        <aside className={`side-panel ${isAsideVisible ? "" : "hidden"}`}>
          <button onClick={closeStockDetail}></button>
          <StockItemDetail item={clickedStockItem} />{" "}
          {/**매개변수로 클릭한 매물의 stock DTO를 매개변수로 전달 */}
        </aside>
        <main className="map-area" ref={mapRef}>
          {/* 카카오 맵이 여기에 렌더링됩니다. */}
        </main>
      </div>
    </>
  );
};

export default StockPage;

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
