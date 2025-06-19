import { useEffect, useRef, useState } from "react"; // useRef 추가
import { axiosAPI } from "../../api/axiosApi";
import "../../css/sale/salePage.css";
import SearchBar from "../common/SearchBar";

const StockPage = () => {
  // 카카오 지도 API 세팅 *****
  const mapRef = useRef(null); // 지도를 담을 div의 ref
  const mapInstanceRef = useRef(null); //생성한 map instance를 저장 - const map = new window.kakao.maps.Map(container, options);
  const itemMarkersRef = useRef([]); // 지도내 표시된 마커 배열 저장

  const [isAsideVisible, setIsAsideVisible] = useState(false); // 지정한 side-panel 숨김여부 저장 state

  const [stockList, setStockList] = useState([]); // spring 서버에서 받아오는 매물 List
  const [clickedStockItem, setClickedStockItem] = useState(null); // 자세히 보기창에 띄울 매물

  // 카카오 지도 *****
  useEffect(() => {
    // 카카오 지도 API가 로드되었는지 확인
    if (window.kakao && window.kakao.maps) {
      const container = mapRef.current; // 지도를 표시할 div
      const options = {
        center: new window.kakao.maps.LatLng(37.5451, 127.0425), // 아크로서울포레스트아파트 대략적인 위도, 경도
        level: 3, // 지도의 확대 레벨
      };
      const map = new window.kakao.maps.Map(container, options);
      mapInstanceRef.current = map; // map 저장

      // 화면을 움직였을떄 서버에 itemList를 요청하는 addListener
      window.kakao.maps.event.addListener(map, "bounds_changed", async () => {
        // "bounds_changed는 마우스를 떼지 않아도 요청이 가기떄문에, 서버에 가는 요청의 개수가 너무 많음. "idle"을 쓰면 마우스가 떼어지면 요청을 보내게 수정함.
        const bounds = map.getBounds();
        const sw = bounds.getSouthWest();
        const ne = bounds.getNorthEast();

        console.log("현재 화면 범위:");
        console.log("좌하단(SW):", sw.getLat(), sw.getLng());
        console.log("우상단(NE):", ne.getLat(), ne.getLng());
        try {
          const resp = await axiosAPI.post("/sale/selectSaleMap", {
            swLat: sw.getLat(),
            swLng: sw.getLng(),
            neLat: ne.getLat(),
            neLng: ne.getLng(),
          });
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
  }, [stockList]); // chatgpt가 지시한 사항 : updateMarker를 수행하는 useeffect를 분리

  // 요청을 보낼때마다 지도에 표시되는 마커들을 새로 세팅하는 함수
  const updateMarker = () => {
    const map = mapInstanceRef.current;

    itemMarkersRef.current.forEach((marker) => marker.setMap(null)); // 이전에 itemMarkersRef에 저장해둔 markers 하나하나 취소
    itemMarkersRef.current = []; // itemMarkersRef 초기화

    stockList?.forEach((item) => {
      const itemMarkerPosition = new window.kakao.maps.LatLng(
        item.lat,
        item.lng
      );
      const itemMarker = new window.kakao.maps.Marker({
        position: itemMarkerPosition,
      });
      itemMarker.setMap(map);
      itemMarkersRef.current.push(itemMarker); // 새 마커 저장
    });
  };

  // 매물 item을 클릭했을떄 수행되는 핸들러 함수
  const handleItemClick = (item, index) => {
    setIsAsideVisible(true); //클릭시 상세창 표시=true 함.
    setClickedStockItem(item); // 클릭한 item의 index를 저장.
    // map?.setDraggable(false); // 사용자가 지도를 드래그하지 못하게 막음!
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
      // null 오류 방지

      const stockFormMap = {
        // same code: 매물 형태(아파트, 빌라, 오피스텔 중 하나)를 int형에서 string으로 변환
        1: "아파트",
        2: "빌라",
        3: "오피스텔",
      };

      const stockForm = stockFormMap[item.stockForm] || "기타";

      return (
        <>
          <div className="stock-header">
            <img
              src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https://blog.kakaocdn.net/dn/bQwQwA/btrb1QwQwQw/1.jpg"
              alt="아파트"
              className="stock-img"
            />
            <div className="stock-title">
              <div className="stock-name">
                아파트명: 아크로서울포레스트아파트
              </div>
              <div className="stock-price">평균 19억 1,000 ~ 19억 4,000</div>
              <div className="stock-address">서울 성동구 성수동1가 685-700</div>
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
          <p>등록 된 매물이 없습니다.</p>
        ) : (
          stockList.map((item, index) => (
            <div
              className="sale-list-item"
              key={index}
              onClick={() => handleItemClick(item, index)}
            >
              <div className="sale-header">
                <img
                  src={saleThumbnail}
                  alt="썸네일 이미지"
                  className="sale-img"
                />
                <div className="sale-title">
                  <div className="sale-name">아파트 · {item.stockName}</div>
                  <div className="sale-price">
                    <span>분양가</span> {item.salePrice}
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
      <SearchBar showSearchType={false} />{" "}
      {/* showSearchType : 현재 페이지가 StockPage인가, SalePage인가 따지는 변수 */}
      {/* list */}
      <div className="container">
        <aside className="sale-side-panel">
          <StockList stockList={stockList} />
        </aside>

        <aside className={`sale-side-panel ${isAsideVisible ? "" : "hidden"}`}>
          <button onClick={closeStockDetail}></button>
          <StockItemDetail item={clickedStockItem} />
        </aside>

        <main className="map-area" ref={mapRef}></main>
      </div>
    </>
  );
};

export default StockPage;
