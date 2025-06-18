import { useState } from "react";
import "../../css/common/SearchBar.css";

import search from "../../assets/search-icon.svg";
import refresh from "../../assets/refresh.svg";
import arrowDown from "../../assets/arrow-down.svg";

const SearchBar = ({ showSearchType = true }) => {
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
            <option>시/도</option>
          </select>
          <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
        </div>

        {/* 구/군 */}
        <div className="select-wrap">
          <select>
            <option>구/군</option>
          </select>
          <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
        </div>

        {/* 매매/전세/월세 or 분양상태 */}
        <div className="select-wrap">
          <select value={dealType} onChange={handleDealChange}>
            {showSearchType ? (
              <>
                <option value="" disabled hidden>
                  매매/전세/월세
                </option>
                <option value="매매">매매</option>
                <option value="전세">전세</option>
                <option value="월세">월세</option>
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
            <option value="" disabled hidden>
              주거/매물형태
            </option>
            <option value="아파트">아파트</option>
            <option value="주택/빌라">주택/빌라</option>
            <option value="오피스텔">오피스텔</option>
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
