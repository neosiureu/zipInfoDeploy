import { useEffect, useRef, useState } from "react";
import { axiosAPI } from "../../api/axiosApi";
import "../../css/sale/salePage.css";
import SearchBar from "../common/SearchBar";
import saleThumbnail from "../../assets/sale-page-thumbnail.svg"; // 썸네일 이미지 추가

const SalePage = () => {
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

  useEffect(() => {
    if (window.kakao && window.kakao.maps) {
      const container = mapRef.current;
      const options = {
        center: new window.kakao.maps.LatLng(37.5451, 127.0425),
        level: 3,
      };
      const map = new window.kakao.maps.Map(container, options);
      mapInstanceRef.current = map;

      window.kakao.maps.event.addListener(map, "bounds_changed", async () => {
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

      const markerPosition = new window.kakao.maps.LatLng(37.5451, 127.0425);
      const marker = new window.kakao.maps.Marker({ position: markerPosition });
      marker.setMap(map);
    }
  }, []);

  useEffect(() => {
    updateMarker();
  }, [stockList]);

  const updateMarker = () => {
    const map = mapInstanceRef.current;
    itemMarkersRef.current.forEach((marker) => marker.setMap(null));
    itemMarkersRef.current = [];

    stockList?.forEach((item) => {
      const itemMarkerPosition = new window.kakao.maps.LatLng(
        item.lat,
        item.lng
      );
      const itemMarker = new window.kakao.maps.Marker({
        position: itemMarkerPosition,
      });
      itemMarker.setMap(map);
      itemMarkersRef.current.push(itemMarker);
    });
  };

  const handleItemClick = (item) => {
    setIsAsideVisible(true);
    setClickedStockItem(item);
  };

  const closeStockDetail = () => {
    setIsAsideVisible(false);
    setClickedStockItem(null);
  };

  const StockItemDetail = ({ item }) => {
    const stockFormMap = {
      1: "아파트",
      2: "빌라",
      3: "오피스텔",
    };
    const stockForm = stockFormMap[item?.stockForm] || "기타";

    return item ? (
      <>
        <div className="stock-header">
          <img
            src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https://blog.kakaocdn.net/dn/bQwQwA/btrb1QwQwQw/1.jpg"
            alt="아파트"
            className="stock-img"
          />
          <div className="stock-title">
            <div className="stock-name">아파트명: {item.stockName}</div>
            <div className="stock-price">
              분양가: {item.salePrice.toLocaleString()}원
            </div>
            <div className="stock-address">{item.stockAddress}</div>
          </div>
        </div>

        <div
          className="stock-img-overview"
          style={{
            margin: "20px 0",
            padding: "20px 0",
            borderBottom: "1px solid #eee",
          }}
        >
          <p>평면도</p>
          <img
            src="https://www.apt2you.com/images/apt/apt2you/apt2you_apt_1.png"
            alt="평면도"
            className="plan-img"
          />
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
                <td>{item.stockAddress}</td>
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
        </div>

        <div className="section">
          <div className="section-title">중개사무소 정보</div>
          {/* 중개사무소 정보 입력 필요 */}
        </div>
      </>
    ) : null;
  };

  const StockList = ({ stockList }) => {
    return (
      <section className="item-list">
        {stockList?.length === 0 ? (
          <p>등록 된 매물이 없습니다.</p>
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
                  <div className="sale-name">아파트 · {item.stockName}</div>
                  <div className="sale-price">
                    <span>분양가</span> {item.salePrice.toLocaleString()}원
                  </div>
                  <div className="sale-address">{item.stockAddress}</div>
                  <div className="sale-status">분양상태</div>
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
      />{" "}
      <div className="container">
        <aside className="sale-side-panel">
          <StockList stockList={stockList} />
        </aside>

        <aside className={`sale-side-panel ${isAsideVisible ? "" : "hidden"}`}>
          <button onClick={closeStockDetail}>닫기</button>
          <StockItemDetail item={clickedStockItem} />
        </aside>

        <main className="map-area" ref={mapRef}></main>
      </div>
    </>
  );
};

export default SalePage;
