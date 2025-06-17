import React, { useEffect, useRef } from "react"; // useRef 추가
import SearchBar from "../common/SearchBar";
import "../../css/sale/salePage.css";

const SalePage = () => {
  const mapRef = useRef(null); // 지도를 담을 div의 ref

  useEffect(() => {
    // 카카오 지도 API가 로드되었는지 확인
    if (window.kakao && window.kakao.maps) {
      const container = mapRef.current; // 지도를 표시할 div
      const options = {
        center: new window.kakao.maps.LatLng(37.5451, 127.0425), // 아크로서울포레스트아파트 대략적인 위도, 경도
        level: 3, // 지도의 확대 레벨
      };
      const map = new window.kakao.maps.Map(container, options);

      // 마커를 추가하고 싶다면 여기에 추가
      const markerPosition = new window.kakao.maps.LatLng(37.5451, 127.0425);
      const marker = new window.kakao.maps.Marker({
        position: markerPosition,
      });
      marker.setMap(map);
    }
  }, []);

  return (
    <>
      <SearchBar />
      <div className="container">
        <aside className="side-panel">
          <div className="apt-header">
            <img
              src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https://blog.kakaocdn.net/dn/bQwQwA/btrb1QwQwQw/1.jpg"
              alt="아파트"
              className="apt-img"
            />
            <div className="apt-title">
              <div className="apt-name">아파트명: 아크로서울포레스트아파트</div>
              <div className="apt-price">평균 19억 1,000 ~ 19억 4,000</div>
              <div className="apt-address">서울 성동구 성수동1가 685-700</div>
            </div>
          </div>
          <div className="section">
            <div className="section-title">기본정보</div>
            <table>
              <tbody>
                <tr>
                  <td>주소</td>
                  <td>서울 성동구 성수동1가 685-700, 아크로서울포레스트</td>
                </tr>
                <tr>
                  <td>건물용도</td>
                  <td>공동주택(아파트)</td>
                </tr>
                <tr>
                  <td>입주일</td>
                  <td>2019.09.09</td>
                </tr>
                <tr>
                  <td>세대수</td>
                  <td>280세대</td>
                </tr>
                <tr>
                  <td>전화번호</td>
                  <td>02-1234-1234</td>
                </tr>
                <tr>
                  <td>평면도</td>
                  <td>
                    <img
                      src="https://www.apt2you.com/images/apt/apt2you/apt2you_apt_1.png"
                      alt="평면도"
                      className="plan-img"
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div className="section">
            <div className="section-title">평형정보</div>
            <table>
              <tbody>
                <tr>
                  <td>10평형</td>
                  <td>39.00㎡ / 20세대</td>
                </tr>
                <tr>
                  <td>20평형</td>
                  <td>59.00㎡ / 30세대</td>
                </tr>
                <tr>
                  <td>30평형</td>
                  <td>84.00㎡ / 230세대</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div className="section">
            <div className="section-title">중도금납입정보</div>
            <table>
              <tbody>
                <tr>
                  <td>계약금</td>
                  <td>191,000,000원 / 10%</td>
                </tr>
                <tr>
                  <td>중도금</td>
                  <td>382,000,000원 / 60%</td>
                </tr>
                <tr>
                  <td>잔금</td>
                  <td>514,000,000원 / 30%</td>
                </tr>
              </tbody>
            </table>
          </div>
        </aside>
        <main className="map-area" ref={mapRef}>
          {/* 카카오 맵이 여기에 렌더링됩니다. */}
        </main>
      </div>
    </>
  );
};

export default SalePage;
