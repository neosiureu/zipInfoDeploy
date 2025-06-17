import "../../css/common/SearchBar.css";

import search from "../../assets/search-icon.svg";
import refresh from "../../assets/refresh.svg";
import arrowDown from "../../assets/arrow-down.svg";

const SearchBar = () => {
  return (
    <div className="searchbar-wrap">
      <div className="searchbar">
        <div className="search-input-wrap">
          <span className="search-icon">
            <img src={search} alt="검색 아이콘" />
          </span>
          <input type="text" placeholder="검색어를 입력하세요" />
        </div>
        <div className="select-wrap">
          <select>
            <option>시/도</option>
          </select>
          <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
        </div>
        <div className="select-wrap">
          <select>
            <option>구/군</option>
          </select>
          <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
        </div>
        <div className="select-wrap">
          <select>
            <option>매매/전세/월세</option>
          </select>
          <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
        </div>
        <div className="select-wrap">
          <select>
            <option>주거/매물형태</option>
          </select>
          <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
        </div>
        <button className="refresh-btn">
          <img src={refresh} alt="새로고침" />
        </button>
      </div>
    </div>
  );
};

export default SearchBar;
