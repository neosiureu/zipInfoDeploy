import { useEffect, useRef, useState } from "react";
import { axiosAPI } from "../../api/axiosApi";
import "../../css/sale/salePage.css";
import SearchBar from "../common/SearchBar";
import warning from "../../assets/circle_warning.svg";

import { useNavigate, useParams } from "react-router-dom";
import { useSaleContext } from "./SaleContext"; // Context 사용

const SalePage = () => {
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
    searchSaleStatus,
    setSearchSaleStatus,
    saleStatusRef,
    searchSaleType,
    setSearchSaleType,
    saleTypeRef,
    navigate,
    searchParams,
  } = useSaleContext();

  const { saleStockNo } = useParams();

  // 분양가 표기 함수
  const formatPrice = (price) => {
    if (!price || isNaN(price)) return "";

    const num = Number(price);
    const billion = Math.floor(num / 100000000); // 억
    const tenThousand = Math.floor((num % 100000000) / 10000); // 만

    if (billion > 0 && tenThousand > 0)
      return `${billion}억 ${tenThousand}만원`;
    if (billion > 0) return `${billion}억`;
    if (tenThousand > 0) return `${tenThousand}만원`;
    return `${num.toLocaleString()}원`; // 1만 미만일 경우
  };

  // 분양가가 0이거나 잘못된 값이면 0으로 처리
  const calculateRate = (amount, total) => {
    if (!total || total <= 0) return 0;
    return Math.floor((amount / total) * 100); // 소수점 버림
  };

  // 날짜 데이터 변환 함수
  const formatDate = (dateString) => {
    if (!dateString) return "-";
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

  // 1. 쿼리스트링 → 검색 조건 초기화
  useEffect(() => {
    const status = Number(searchParams.get("status") ?? -1);
    const type = Number(searchParams.get("type") ?? -1);
    const region = Number(
      searchParams.get("sigungu") || searchParams.get("sido") || -1
    );
    const keyword = searchParams.get("keyWord") || "";

    setSearchSaleStatus(status);
    setSearchSaleType(type);
    setSearchLocationCode(region);
    setSearchKeyWord(keyword);
  }, [searchParams]);

  // 2. 검색 조건 state → ref 최신화
  useEffect(() => {
    searchKeyWordRef.current = searchKeyWord;
    locationCodeRef.current = searchLocationCode;
    saleStatusRef.current = searchSaleStatus;
    saleTypeRef.current = searchSaleType;
  }, [searchKeyWord, searchLocationCode, searchSaleStatus, searchSaleType]);

  useEffect(() => {
    if (!mapInstanceRef.current) return;

    const bounds = mapInstanceRef.current.getBounds();
    const sw = bounds.getSouthWest();
    const ne = bounds.getNorthEast();

    const fetchData = async () => {
      try {
        const resp = await axiosAPI.post("/sale/selectSaleMap", {
          coords: {
            swLat: sw.getLat(),
            swLng: sw.getLng(),
            neLat: ne.getLat(),
            neLng: ne.getLng(),
          },
          searchKeyWord,
          locationCode: searchLocationCode,
          saleStatus: searchSaleStatus,
          saleType: searchSaleType,
        });

        setStockList(resp.data);
        updateMarker(resp.data);
      } catch (error) {
        console.error("검색 조건 변경에 따른 요청 중 오류:", error);
      }
    };

    fetchData();
  }, [searchKeyWord, searchLocationCode, searchSaleStatus, searchSaleType]);

  // 3. Kakao Map 초기화 및 idle 이벤트로 API 요청
  useEffect(() => {
    if (window.kakao && window.kakao.maps) {
      const container = mapRef.current;
      const options = {
        center: new window.kakao.maps.LatLng(37.567937, 126.983001),
        level: 7,
      };
      const map = new window.kakao.maps.Map(container, options);
      mapInstanceRef.current = map;

      // 지도 생성 직후 첫 API 호출
      const fetchInitialData = async () => {
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
            searchKeyWord: searchKeyWordRef.current || "",
            locationCode: locationCodeRef.current ?? -1,
            saleStatus: saleStatusRef.current ?? -1,
            saleType: saleTypeRef.current ?? -1,
          });

          setStockList(resp.data);
          updateMarker(resp.data);
        } catch (error) {
          console.error("초기 매물 요청 실패:", error);
        }
      };

      // 최초 실행
      fetchInitialData();

      // idle 이벤트 등록 (기존처럼 유지)
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
            searchKeyWord: searchKeyWordRef.current || "",
            locationCode: locationCodeRef.current ?? -1,
            saleStatus: saleStatusRef.current ?? -1,
            saleType: saleTypeRef.current ?? -1,
          });

          setStockList(resp.data);
          updateMarker(resp.data);
        } catch (error) {
          console.error("매물 요청 실패:", error);
        }
      });
    }
  }, [searchParams]);

  // 4. URL에 매물 번호가 있을 경우 상세 정보 표시
  useEffect(() => {
    const fetchDetail = async () => {
      try {
        const res = await axiosAPI.get("/sale/detail", {
          params: { saleStockNo },
        });
        if (res.status === 200) {
          setClickedStockItem(res.data);
          setIsAsideVisible(true);

          const map = mapInstanceRef.current;
          if (map && res.data.lat && res.data.lng) {
            const offsetLng = -0.07;
            const movedLatLng = new window.kakao.maps.LatLng(
              res.data.lat,
              res.data.lng + offsetLng
            );
            map.panTo(movedLatLng);
            updateMarker([res.data]);
          }
        }
      } catch (e) {
        console.error("상세 매물 조회 실패:", e);
      }
    };

    if (saleStockNo) fetchDetail();
  }, [saleStockNo]);

  useEffect(() => {
    updateMarker();
  }, [stockList]); // stockList(맨 왼쪽에 있는 매물 Item들을 저장하는 state변수), searchLocationCode(검색창SearchBox에서 선택한 지역을 저장하는 state변수)

  // updateMarker : 요청을 보낼때마다 지도에 표시되는 마커들을 새로 세팅하는 함수
  // 5. 마커 렌더링 함수
  const updateMarker = (list = stockList) => {
    const map = mapInstanceRef.current;
    if (!map) return;

    // 기존 마커 제거
    itemMarkersRef.current.forEach((marker) => marker.setMap(null));
    itemMarkersRef.current = [];

    list.forEach((item) => {
      const position = new window.kakao.maps.LatLng(item.lat, item.lng);
      const div = document.createElement("div");
      div.className = "custom-overlay";
      div.innerHTML = `
        <div class="area">${item.saleSupplyArea}㎡</div>
        <div class="label">분양가 <strong>${formatPrice(
          item.salePrice
        )}</strong></div>
      `;
      div.addEventListener("click", () => {
        setClickedStockItem(item);
        setIsAsideVisible(true);
        navigate(`/sale/${item.saleStockNo}`);
      });

      const overlay = new window.kakao.maps.CustomOverlay({
        position,
        content: div,
        yAnchor: 1,
      });
      overlay.setMap(map);
      itemMarkersRef.current.push(overlay);
    });
  };

  const handleItemClick = (item) => {
    setIsAsideVisible(true);
    setClickedStockItem(item);
    navigate(`/sale/${item.saleStockNo}`);
  };

  const closeDetail = () => {
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
                  <td>{item.saleAddress || "-"}</td>
                </tr>
                <tr>
                  <td>규모</td>
                  <td>{item.scale || "-"}</td>
                </tr>
                <tr>
                  <td>청약접수</td>
                  <td>
                    {item.applicationStartDate && item.applicationEndDate
                      ? `${formatDate(
                          item.applicationStartDate
                        )} ~ ${formatDate(item.applicationEndDate)}`
                      : "-"}
                  </td>
                </tr>
                <tr>
                  <td>당첨자발표</td>
                  <td>{formatDate(item.announcementDate)}</td>
                </tr>
                <tr>
                  <td>건설사</td>
                  <td>{item.company || "-"}</td>
                </tr>
                <tr>
                  <td>분양문의</td>
                  <td>{item.contactInfo || "-"}</td>
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
              <img
                src={`http://localhost:8080${item.floorplanUrl}`}
                alt="평면도"
                className="sale-floorplan"
              />
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
                  <td>{formatPrice(item.salePrice)}</td>
                </tr>
                <tr>
                  <td>취득세</td>
                  <td>
                    {item.acquisitionTax
                      ? `${formatPrice(item.acquisitionTax)}`
                      : "-"}
                  </td>
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
                  <td>
                    {formatPrice(item.deposit)} /{" "}
                    {calculateRate(item.deposit, item.salePrice)}%
                  </td>
                </tr>
                <tr>
                  <td>중도금</td>
                  <td>
                    {formatPrice(item.middlePayment)} /{" "}
                    {calculateRate(item.middlePayment, item.salePrice)}%
                  </td>
                </tr>
                <tr>
                  <td>잔금</td>
                  <td>
                    {formatPrice(item.balancePayment)} /{" "}
                    {calculateRate(item.balancePayment, item.salePrice)}%
                  </td>
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
                <img
                  src={`http://localhost:8080${item.thumbnailUrl}`}
                  alt="썸네일"
                  className="sale-img"
                />
                <div className="sale-title">
                  <div className="sale-name">
                    {stockFormMap[item.saleStockForm]} ·{" "}
                    {item.saleStockName.length > 9
                      ? `${item.saleStockName.slice(0, 9)}...`
                      : item.saleStockName}
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
      <SearchBar
        showSearchType={false}
        searchKeyWord={searchKeyWord}
        setSearchKeyWord={setSearchKeyWord}
        searchLocationCode={searchLocationCode}
        setSearchLocationCode={setSearchLocationCode}
        searchStockForm={searchSaleStatus}
        setSearchStockForm={setSearchSaleStatus}
        searchStockType={searchSaleType}
        setSearchStockType={setSearchSaleType}
      />

      <div className="container">
        <aside className="sale-side-panel">
          <StockList stockList={stockList} />
        </aside>

        {isAsideVisible && clickedStockItem && (
          <>
            <aside className="sale-side-panel detail-panel">
              <StockItemDetail item={clickedStockItem} />
            </aside>
            <button className="sale-close-button" onClick={closeDetail}>
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
