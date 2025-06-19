import { useState, useEffect } from "react";
import "../../css/common/SearchBar.css";

import arrowDown from "../../assets/arrow-down.svg";
import refresh from "../../assets/refresh.svg";
import search from "../../assets/search-icon.svg";

const SearchBar = ({
  showSearchType = true,
  searchKeyWord,
  setSearchKeyWord, //State searchKeyWord
  searchLocationCode,
  setSearchLocationCode, //State searchLocationCode
  searchStockForm,
  setSearchStockForm, //State searchStockForm
  searchStockType,
  setSearchStockType, //State searchStockType
  //todo : SalePage 내부에 선언된 매물 유형 state 매개변수 추가할것.
}) => {
  const [dealType, setDealType] = useState(""); // 매물/분양 상태
  const [residenceType, setResidenceType] = useState(""); // 주거/매물형태

  const [sidoSelected, setSidoSelected] = useState(-1); // 현재 선택한 시/도 상태 저장
  const [sigunguSelected, setSigunguSelected] = useState(-1); // 현재 선택한 시/군/구 상태 저장
  const handleDealChange = (e) => setDealType(e.target.value);
  const handleResidenceChange = (e) => setResidenceType(e.target.value);

  /*************************** 위치 선택 기능 *******************************/
  const handleSidoChange = (e) => {
    setSidoSelected(parseInt(e.target.value, 10)); //SeachBar.jsx 파일에 있는 SidoSelected를 바꾼뒤
    //아래 useEffect에서 매개변수로 들어온 searchLocationCode를 알맞게 바꾼다.
  };
  const handleSigunguChange = (e) => {
    setSigunguSelected(parseInt(e.target.value, 10)); //SeachBar.jsx 파일에 있는 SidoSelected를 바꾼뒤
    //아래 useEffect에서 매개변수로 들어온 searchLocationCode를 알맞게 바꾼다.
  };

  useEffect(() => {
    console.log(
      "sidoSelected:",
      sidoSelected,
      "sigunguSelected:",
      sigunguSelected
    );
    if (sigunguSelected === -1 && sidoSelected !== -1) {
      // 시/도 가 선택된 상태에서 시/군/구가 선택이 안되있거나 전체로 선택되어있을떄
      console.log("조건문 1");

      //setSearchLocationCode(sidoSelected); // 시도(lower than < 100)를 SearchLocationCode로 끌어올림
      setSearchLocationCode(1000000000000);
    } else if (sigunguSelected !== -1 && sidoSelected !== -1) {
      // 시/도 가 선택된 상태에서 시/군/구도 선택되었을떄
      console.log("조건문 2");
      setSearchLocationCode(sigunguSelected);
    } else if (sigunguSelected === -1 && sidoSelected === -1) {
      // 시/도, 시/군/구 모두 선택을 안헀을떄(또는 전체로 선택되었을떄)
      console.log("조건문 3");
      setSearchLocationCode(-1);
    } else {
      console.log(
        "잘못된 시/도, 시/군/구 선택이 발생했습니다:",
        sidoSelected,
        sigunguSelected
      );
    }
  }, [sidoSelected, sigunguSelected]);
  return (
    <div className="searchbar-wrap">
      <div className="searchbar">
        {/* 검색어 입력 */}
        <div className="search-input-wrap">
          <span className="header-search-icon">
            <img src={search} alt="검색 아이콘" />
          </span>
          <input type="text" placeholder="검색어를 입력하세요" />
        </div>

        {/* 시/도 */}
        <div className="select-wrap">
          <select value={sidoSelected} onChange={handleSidoChange}>
            <option value={-1} disabled>
              시/도
            </option>
            <option value={-1}>전국</option>
            <option value={11}>서울특별시</option>
            <option value={26}>부산광역시</option>
            <option value={28}>인천광역시</option>
            <option value={30}>대전광역시</option>
            <option value={27}>대구광역시</option>
            <option value={29}>광주광역시</option>
            <option value={31}>울산광역시</option>
            <option value={36}>세종특별자치시</option>
            <option value={41}>경기도</option>
            <option value={42}>강원도</option>
            <option value={43}>충청북도</option>
            <option value={44}>충청남도</option>
            <option value={45}>전북특별자치도</option>
            <option value={46}>전라남도</option>
            <option value={47}>경상북도</option>
            <option value={48}>경상남도</option>
            <option value={50}>제주특별자치도</option>
          </select>
          <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
        </div>

        {/* 구/군 */}
        <div className="select-wrap">
          <select value={sigunguSelected} onChange={handleSigunguChange}>
            <option value={-1} disabled>
              시/도
            </option>
            <option value={11110}>종로구</option>
          </select>
          <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
        </div>

        {/* 매매/전세/월세 or 분양상태 */}
        <div className="select-wrap">
          <select value={dealType} onChange={handleDealChange}>
            {showSearchType ? (
              <>
                <option value="-1" disabled hidden>
                  매매/전세/월세
                </option>
                <option value="-1">전체</option>
                <option value="0">매매</option>
                <option value="1">전세</option>
                <option value="2">월세</option>
              </>
            ) : (
              <>
                <option value="" disabled hidden>
                  분양상태
                </option>
                <option value="분양예정">분양예정</option>
                <option value="분양중">분양중</option>
                <option value="분양완료">분양완료</option>
              </>
            )}
          </select>
          <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
        </div>

        {/* 주거/매물형태 */}
        <div className="select-wrap">
          <select value={residenceType} onChange={handleResidenceChange}>
            <option value="-1" disabled hidden>
              주거/매물형태
            </option>
            <option value="-1">전체</option>
            <option value="1">아파트</option>
            <option value="2">주택/빌라</option>
            <option value="3">오피스텔</option>
          </select>
          <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
        </div>

        {/* 새로고침 */}
        <button className="searcbar-refresh-btn">
          <img src={refresh} alt="새로고침" />
        </button>
      </div>
    </div>
  );
};

export default SearchBar;
