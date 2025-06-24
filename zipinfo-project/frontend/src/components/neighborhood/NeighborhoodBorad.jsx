import React, { useState } from "react";
import "../../css/neighborhood/NeighborhoodBoard.css";
import arrowDown from "../../assets/arrow-down.svg";
import { CITY, TOWN } from "../../components/common/Gonggong";

const NeighborhoodBoard = () => {
  const [currentPage, setCurrentPage] = useState(1);
  const [selectedCity, setSelectedCity] = useState(-1); // 선택된 시도
  const [selectedTown, setSelectedTown] = useState(-1); // 선택된 시군구
  const [selectedSubject, setSelectedSubject] = useState(-1); // 선택된 주제

  const boardData = Array(9).fill({
    number: 1,
    title:
      "오늘 하루도 열심히 하이팅 힘냅시다!! 오늘 하루도 열심히 하이팅 힘냅시다!",
    author: "관리자",
    date: "2023.11.20",
    views: 0,
  });

  const pages = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  // 선택된 시도에 해당하는 시군구만 필터링
  const filteredTowns =
    selectedCity !== -1
      ? TOWN.filter((town) => town.code === parseInt(selectedCity))
      : [];

  // 시도 선택 핸들러
  const handleCityChange = (e) => {
    setSelectedCity(e.target.value);
    setSelectedTown(-1); // 시도 변경시 시군구 초기화
  };

  // 시군구 선택 핸들러
  const handleTownChange = (e) => {
    setSelectedTown(e.target.value);
  };

  // 주제 선택 핸들러
  const handleSubjectChange = (e) => {
    setSelectedSubject(e.target.value);
  };

  return (
    <div className="nb-container">
      <div className="nb-board-wrapper">
        <h1 className="nb-title">우리동네</h1>

        <div className="nb-filters">
          <div className="select-wrap">
            <select
              className="nb-select"
              value={selectedCity}
              onChange={handleCityChange}
            >
              <option value={-1}>시/도</option>
              {CITY.map((city) => (
                <option key={city.code} value={city.code}>
                  {city.name}
                </option>
              ))}
            </select>
            <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
          </div>

          <div className="select-wrap">
            <select
              className="nb-select"
              value={selectedTown}
              onChange={handleTownChange}
              disabled={selectedCity === -1}
            >
              <option value={-1}>군/구/시</option>
              {filteredTowns.map((town) => (
                <option key={town.fullcode} value={town.fullcode}>
                  {town.name}
                </option>
              ))}
            </select>
            <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
          </div>

          <div className="select-wrap">
            <select
              className="nb-select nb-select-wide"
              value={selectedSubject}
              onChange={handleSubjectChange}
            >
              <option value={-1}>주제 분류</option>
              <option value="Q">질문</option>
              <option value="R">리뷰</option>
              <option value="E">기타</option>
            </select>
            <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
          </div>
        </div>

        <div className="nb-board-table">
          <div className="nb-header">
            <div className="nb-header-cell nb-header-number">번호</div>
            <div className="nb-header-cell nb-header-title">제목</div>
            <div className="nb-header-cell nb-header-author">작성자</div>
            <div className="nb-header-cell nb-header-date">날짜</div>
            <div className="nb-header-cell nb-header-views">조회</div>
          </div>

          {boardData.map((item, index) => (
            <div key={index} className="nb-row">
              <div className="nb-cell nb-cell-number">{item.number}</div>
              <div className="nb-cell nb-cell-title">{item.title}</div>
              <div className="nb-cell nb-cell-author">{item.author}</div>
              <div className="nb-cell nb-cell-date">{item.date}</div>
              <div className="nb-cell nb-cell-views">{item.views}</div>
            </div>
          ))}
        </div>

        <div className="nb-pagination-container">
          <div className="nb-pagination">
            <button className="nb-page-btn nb-page-prev">‹</button>
            <button className="nb-page-btn nb-page-prev">‹‹</button>

            {pages.map((page) => (
              <button
                key={page}
                className={`nb-page-btn ${page === 1 ? "nb-page-active" : ""}`}
                onClick={() => setCurrentPage(page)}
              >
                {page.toString().padStart(2, "0")}
              </button>
            ))}

            <button className="nb-page-btn nb-page-next">›</button>
            <button className="nb-page-btn nb-page-next">››</button>
          </div>

          <button className="nb-write-btn">글쓰기</button>
        </div>
      </div>
    </div>
  );
};

export default NeighborhoodBoard;
