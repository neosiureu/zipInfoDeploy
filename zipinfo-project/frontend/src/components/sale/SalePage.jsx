import { useEffect, useRef, useState } from "react";
import { axiosAPI } from "../../api/axiosApi";
import "../../css/sale/salePage.css";
import SearchBar from "../common/SearchBar";
import saleThumbnail from "../../assets/sale-page-thumbnail.svg"; // 썸네일 이미지 추가
import floor from "../../assets/floor.svg"; // 평면도 이미지 추가
import warning from "../../assets/circle_warning.svg"; // 미검색 결과 아이콘

import { useNavigate, useParams } from "react-router-dom";

const SalePage = () => {
  // URL에서 매물 번호 받기
  const { saleStockNo } = useParams();

  // 카카오 API 세팅
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null); //생성한 map instance를 저장 -- const map = new window.kakao.maps.Map(container, options);
  const itemMarkersRef = useRef([]); // 지도내 표시된 마커 배열 저장

  // 사이드 바 관련 상태
  const [isAsideVisible, setIsAsideVisible] = useState(false); // 사이드 바 숨김 여부 저장

  // 매물 상태 변수
  const [stockList, setStockList] = useState([]); // 서버에서 받아오는 매물 리스트
  const [clickedStockItem, setClickedStockItem] = useState(null); // 자세히 보기창에 띄울 매물

  // SearchBar에 전달할 상태 추가
  const [searchKeyWord, setSearchKeyWord] = useState("");
  const [searchLocationCode, setSearchLocationCode] = useState(-1);
  const [searchSaleStatus, setSaleStatus] = useState(-1);
  const [searchSaleType, setSearchSaleType] = useState(-1);

  // 상세 디테일 페이지를 URL로 연결할 변수
  const navigate = useNavigate();

  // 분양가 표기 함수
  const formatPrice = (price) => {
    if (!price || isNaN(price)) return "";

    const num = Number(price);
    const billion = Math.floor(num / 100000000);
    const million = Math.floor((num % 100000000) / 10000);

    if (billion > 0 && million > 0) return `${billion}억 ${million}`;
    if (billion > 0) return `${billion}억`;
    if (million > 0) return `${million}`;
    return num.toLocaleString();
  };

  // 날짜 데이터 변환 함수
  const formatDate = (dateString) => {
    if (!dateString) return "";
    const date = new Date(dateString);
    return date.toISOString().slice(0, 10); // 'YYYY-MM-DD'
  };

  // 매물 형태 매핑
  const stockFormMap = {
    1: "아파트",
    2: "빌라",
    3: "오피스텔",
  };

  // 분양 상태 매핑
  const status = {
    1: "분양예정",
    2: "분양중",
    3: "분양완료",
  };

  useEffect(() => {
    if (window.kakao && window.kakao.maps) {
      const container = mapRef.current;
      const options = {
        center: new window.kakao.maps.LatLng(37.567937, 126.983001), // KH종로지원 대략적인 위도, 경도
        level: 7, // 지도의 확대 레벨
      };
      const map = new window.kakao.maps.Map(container, options);
      mapInstanceRef.current = map;

      window.kakao.maps.event.addListener(map, "idle", async () => {
        const bounds = map.getBounds();
        const sw = bounds.getSouthWest();
        const ne = bounds.getNorthEast();

        try {
          const resp = await axiosAPI.post("/sale/selectSaleMap", {
            coords: {
              swLat: sw.getLat(),
              swLng: sw.getLng(),
              neLat: ne.getLat(),
              neLng: ne.getLng(),
            },
            searchKeyWord: searchKeyWord || "", //keyword ||
            locationCode: searchLocationCode ?? -1, // -1 : 서버측에서 무시하는 value selectedLocation ||
            saleStatus: searchSaleStatus ?? -1, // -1 : 서버측에서 무시하는 valueselectedType ||
            saleType: searchSaleType ?? -1, // -1 : 서버측에서 무시하는 valueselectedForm ||
          });
          if (resp.status === 200) {
            setStockList(resp.data);
            updateMarker();
          }
        } catch (error) {
          console.log("매물 items 조회 중 error 발생 : ", error);
        }
      });
    }
  }, []);

  // useEffect : URL에 매물 번호가 있을 경우 자동 상세 정보 열기
  useEffect(() => {
    const fetchDetail = async () => {
      try {
        const res = await axiosAPI.get("/sale/detail", {
          params: { saleStockNo },
        });
        if (res.status === 200) {
          const data = res.data; // ← 변수 선언
          setClickedStockItem(data);
          setIsAsideVisible(true);

          // 지도 중심 이동
          const map = mapInstanceRef.current;
          if (map && data.lat && data.lng) {
            const offsetLng = -0.07; // ← 이동할 정도 (조절 가능)
            const movedLatLng = new window.kakao.maps.LatLng(
              data.lat,
              data.lng - offsetLng
            );
            map.panTo(movedLatLng);

            updateMarker([data]);
          }
        }
      } catch (error) {
        console.error("상세 매물 조회 실패:", error);
      }
    };

    if (saleStockNo) {
      fetchDetail(); // URL에 매물번호 있으면 API 호출로 불러옴
    }
  }, [saleStockNo]);

  useEffect(() => {
    updateMarker();
  }, [stockList]); // stockList(맨 왼쪽에 있는 매물 Item들을 저장하는 state변수), searchLocationCode(검색창SearchBox에서 선택한 지역을 저장하는 state변수)

  // updateMarker : 요청을 보낼때마다 지도에 표시되는 마커들을 새로 세팅하는 함수
  const updateMarker = (list = stockList) => {
    const map = mapInstanceRef.current;

    // 좌표 문자열 배열로 변환하여 비교
    const existingCoords = itemMarkersRef.current.map((m) =>
      m.getPosition().toString()
    );
    const newCoords = list.map((item) =>
      new window.kakao.maps.LatLng(item.lat, item.lng).toString()
    );

    const needUpdate = existingCoords.join(",") !== newCoords.join(",");
    if (!needUpdate) return;

    // 기존 마커 제거
    itemMarkersRef.current.forEach((marker) => marker.setMap(null));
    itemMarkersRef.current = [];

    // 새 마커 생성
    list.forEach((item) => {
      const position = new window.kakao.maps.LatLng(item.lat, item.lng);
      const content = `
      <div class="custom-overlay">
        <div class="area">${item.saleSupplyArea}㎡</div>
        <div class="label">분양가 <strong>${formatPrice(
          item.salePrice
        )}</strong></div>
      </div>
    `;

      const overlayDiv = document.createElement("div");
      overlayDiv.innerHTML = `
        <div class="custom-overlay">
          <div class="area">${item.saleSupplyArea}㎡</div>
          <div class="label">분양가 <strong>${formatPrice(
            item.salePrice
          )}</strong></div>
        </div>
      `;

      overlayDiv.addEventListener("click", () => {
        setIsAsideVisible(true);
        setClickedStockItem(item);
        navigate(`/sale/${item.saleStockNo}`); // ← 추가
      });

      const customOverlay = new window.kakao.maps.CustomOverlay({
        position,
        content: overlayDiv,
        yAnchor: 1,
      });
      customOverlay.setMap(map);
      itemMarkersRef.current.push(customOverlay);
    });
  };

  const handleItemClick = (item) => {
    setIsAsideVisible(true);
    setClickedStockItem(item);
    navigate(`/sale/${item.saleStockNo}`);
  };

  const closeStockDetail = () => {
    setIsAsideVisible(false);
    setClickedStockItem(null);
    navigate("/sale", { replace: true });
  };

  const StockItemDetail = ({ item }) => {
    const stockForm = stockFormMap[item?.stockForm] || "기타";

    return item ? (
      <>
        <div className="sale-detail-header">
          <div className="sale-title">
            <div className="sale-detail-type">
              {stockFormMap[item.saleStockForm]}
            </div>
            <div className="sale-detail-name">{item.saleStockName}</div>
            <div className="sale-price">
              <span>분양가</span> {formatPrice(item.salePrice)}
            </div>
            <div className="sale-detail-status">{status[item.saleStatus]}</div>
          </div>
        </div>

        {/* 기본정보 */}
        <div className="sale-section">
          <div className="sale-section-line" />
          <div className="sale-section-content">
            <div className="sale-section-title">기본정보</div>
            <table className="sale-table">
              <tbody>
                <tr>
                  <td>분양주소</td>
                  <td>{item.saleAddress}</td>
                </tr>
                <tr>
                  <td>규모</td>
                  <td>{item.scale}</td>
                </tr>
                <tr>
                  <td>청약접수</td>
                  <td>
                    {formatDate(item.applicationStartDate)} ~{" "}
                    {formatDate(item.applicationEndDate)}
                  </td>
                </tr>
                <tr>
                  <td>당첨자발표</td>
                  <td>{formatDate(item.announcementDate)}</td>
                </tr>
                <tr>
                  <td>건설사</td>
                  <td>{item.company}</td>
                </tr>
                <tr>
                  <td>분양문의</td>
                  <td>{item.contactInfo}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        {/* 평면도 */}
        <div className="sale-section">
          <div className="sale-section-line" />
          <div className="sale-section-content">
            <div className="sale-plan-section">
              <img src={floor} alt="평면도" className="plan-img" />
            </div>
          </div>
        </div>

        {/* 평형정보 */}
        <div className="sale-section">
          <div className="sale-section-line" />
          <div className="sale-section-content">
            <div className="sale-section-title">평형정보</div>
            <table className="sale-table">
              <tbody>
                <tr>
                  <td>분양가</td>
                  <td>{formatPrice(item.salePrice)}만원</td>
                </tr>
                <tr>
                  <td>취득세</td>
                  <td>{formatPrice(item.acquisitionTax)}만원</td>
                </tr>
                <tr>
                  <td>공급면적</td>
                  <td>{item.saleSupplyArea}㎡</td>
                </tr>
                <tr>
                  <td>전용면적</td>
                  <td>{item.saleExclusiveArea}㎡</td>
                </tr>

                <tr>
                  <td>방/욕실수</td>
                  <td>
                    {item.saleRoomCount} / {item.saleBathroomCount}개
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        {/* 중도금납입정보 */}
        <div className="sale-section sale-last-section">
          <div className="sale-section-line" />
          <div className="sale-section-content">
            <div className="sale-section-title">납입정보</div>
            <table className="sale-table">
              <tbody>
                <tr>
                  <td>계약금</td>
                  <td>1억 1,000만원 / 10%</td>
                </tr>
                <tr>
                  <td>중도금</td>
                  <td>3억 2,000만원 / 60%</td>
                </tr>
                <tr>
                  <td>잔금</td>
                  <td>5억 6,000만원 / 30%</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </>
    ) : null;
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
          stockList.map((item, index) => (
            <div
              className="sale-list-item"
              key={index}
              onClick={() => handleItemClick(item)}
            >
              <div className="sale-header">
                <img src={saleThumbnail} alt="썸네일" className="sale-img" />
                <div className="sale-title">
                  <div className="sale-name">
                    {stockFormMap[item.saleStockForm]} · {item.saleStockName}
                  </div>
                  <div className="sale-price">
                    <span>분양가</span> {formatPrice(item.salePrice)}
                  </div>
                  <div className="sale-address">
                    {item.saleSupplyArea}㎡ |{" "}
                    {item.saleAddress.length > 10
                      ? `${item.saleAddress.slice(0, 10)}...`
                      : item.saleAddress}
                  </div>
                  <div className="sale-status">{status[item.saleStatus]}</div>
                </div>
              </div>
              <div className="sale-divider" />
            </div>
          ))
        )}
      </section>
    );
  };

  return (
    <>
      {/* 오류 수정: 필요한 함수 props를 SearchBar에 전달 */}
      <SearchBar
        showSearchType={false}
        searchKeyWord={searchKeyWord}
        setSearchKeyWord={setSearchKeyWord}
        searchLocationCode={searchLocationCode}
        setSearchLocationCode={setSearchLocationCode}
        searchStockForm={searchSaleStatus}
        setSearchStockForm={setSaleStatus}
        searchStockType={searchSaleType}
        setSearchStockType={setSearchSaleType}
      />
      <div className="container">
        <aside className="sale-side-panel">
          <StockList stockList={stockList} />
        </aside>

        {isAsideVisible && (
          <>
            <aside className="sale-side-panel detail-panel">
              <StockItemDetail item={clickedStockItem} />
            </aside>
            <button className="sale-close-button" onClick={closeStockDetail}>
              ✕
            </button>
          </>
        )}

        <main className="map-area" ref={mapRef}></main>
      </div>
    </>
  );
};

export default SalePage;
