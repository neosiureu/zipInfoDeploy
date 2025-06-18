import React, { useEffect, useRef } from "react";
import SearchBar from "../common/SearchBar";
import saleThumbnail from "../../assets/sale-page-thumbnail.svg";
import floor from "../../assets/floor.svg";
import "../../css/sale/SalePage.css";

const SalePage = () => {
  const mapRef = useRef(null);

  // 카카오 지도 API
  useEffect(() => {
    if (window.kakao && window.kakao.maps) {
      const container = mapRef.current;
      const options = {
        center: new window.kakao.maps.LatLng(37.5451, 127.0425),
        level: 3,
      };
      const map = new window.kakao.maps.Map(container, options);
      const markerPosition = new window.kakao.maps.LatLng(37.5451, 127.0425);
      const marker = new window.kakao.maps.Marker({ position: markerPosition });
      marker.setMap(map);
    }
  }, []);

  return (
    <>
      {/* 분양 페이지일 경우 보여주는 검색창 */}
      <SearchBar showSearchType={false} />

      <div className="container">
        <aside className="sale-side-panel">
          <div className="sale-header">
            <img src={saleThumbnail} alt="썸네일 이미지" className="sale-img" />
            <div className="sale-title">
              <div className="sale-name">아파트 · 아크로서울포레스트아파트</div>
              <div className="sale-price">
                <span>분양가</span> 10억 9,000
              </div>
              <div className="sale-address">서울 성동구 성수동1가 685-700</div>
              <div className="sale-status">분양상태</div>
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
                    <td>서울시 강동구 성내동 459-3</td>
                  </tr>
                  <tr>
                    <td>규모</td>
                    <td>15-16층, 1개동, 총 58세대 / 일반분양 9세대</td>
                  </tr>
                  <tr>
                    <td>청약접수</td>
                    <td>25.06.09 ~ 25.06.10</td>
                  </tr>
                  <tr>
                    <td>당첨자발표</td>
                    <td>25.06.13</td>
                  </tr>
                  <tr>
                    <td>건설사</td>
                    <td>에스테크건설(주), (주)이엔건설</td>
                  </tr>
                  <tr>
                    <td>분양문의</td>
                    <td>02-1234-1234</td>
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
                    <td>10억 9,000</td>
                  </tr>
                  <tr>
                    <td>취득세</td>
                    <td>3,597만원</td>
                  </tr>
                  <tr>
                    <td>공급면적</td>
                    <td>70.02㎡</td>
                  </tr>
                  <tr>
                    <td>전용면적</td>
                    <td>52.02㎡</td>
                  </tr>
                  <tr>
                    <td>대지지분</td>
                    <td>28.11㎡</td>
                  </tr>
                  <tr>
                    <td>방/욕실수</td>
                    <td>3개 / 2개</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          {/* 중도금납입정보 */}
          <div className="sale-section sale-last-section">
            <div className="sale-section-line" />
            <div className="sale-section-content">
              <div className="sale-section-title">중도금납입정보</div>
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
        </aside>

        <main className="map-area" ref={mapRef}></main>
      </div>
    </>
  );
};

export default SalePage;
