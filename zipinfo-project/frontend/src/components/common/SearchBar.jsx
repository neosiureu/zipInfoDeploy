import { useState } from "react";
import "../../css/common/SearchBar.css";

import search from "../../assets/search-icon.svg";
import refresh from "../../assets/refresh.svg";
import arrowDown from "../../assets/arrow-down.svg";

const SearchBar = ({
  showSearchType = true,
  searchKeyWord,
  setSearchKeyWord, //useState
  searchLocationCode,
  setSearchLocationCode,
  searchStockForm,
  setSearchStockForm,
  searchStockType,
  setSearchStockType,
  //todo : SalePage 내부에 선언된 매물 유형 state 매개변수 추가할것.
}) => {
  const [dealType, setDealType] = useState(""); // 매물/분양 상태
  const [residenceType, setResidenceType] = useState(""); // 주거/매물형태

  const handleDealChange = (e) => setDealType(e.target.value);
  const handleResidenceChange = (e) => setResidenceType(e.target.value);

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
          <select>
            <option value={-1} disabled>
              시/도
            </option>
            <option>전국</option>
            <option>서울특별시</option>
            <option>부산광역시</option>
            <option>인천광역시</option>
            <option>대전광역시</option>
            <option>대구광역시</option>
            <option>광주광역시</option>
            <option>울산광역시</option>
          </select>
          <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
        </div>

        {/* 구/군 */}
        <div className="select-wrap">
          <select>
            <option value={-1} disabled>
              시/도
            </option>
            <option></option>
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
