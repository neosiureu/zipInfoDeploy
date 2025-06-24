import React, { useState } from "react";
import "../../css/neighborhood/NeighborhoodBoard.css";
import arrowDown from "../../assets/arrow-down.svg";

const NeighborhoodBoard = () => {
  const [currentPage, setCurrentPage] = useState(1);

  const boardData = Array(9).fill({
    number: 1,
    title:
      "오늘 하루도 열심히 하이팅 힘냅시다!! 오늘 하루도 열심히 하이팅 힘냅시다!",
    author: "관리자",
    date: "2023.11.20",
    views: 0,
  });

  const pages = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  return (
    <div className="nb-container">
      <div className="nb-board-wrapper">
        <h1 className="nb-title">우리동네</h1>

        <div className="nb-filters">
          <div className="select-wrap">
            <select className="nb-select">
              <option value="-1">시/도</option>
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
              <option value={51}>강원도</option>
              {/*행정구역 개편으로 시/도 코드 51로 변경됨. */}
              <option value={43}>충청북도</option>
              <option value={44}>충청남도</option>
              <option value={52}>전북특별자치도</option>
              {/*행정구역 개편으로 시/도 코드 52로 변경됨. */}
              <option value={46}>전라남도</option>
              <option value={47}>경상북도</option>
              <option value={48}>경상남도</option>
              <option value={50}>제주특별자치도</option>
            </select>
            <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
          </div>

          <div className="select-wrap">
            <select className="nb-select">
              <option value="-1">시/구/군</option>
            </select>
            <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
          </div>

          <div className="select-wrap">
            <select className="nb-select nb-select-wide">
              <option value="-1">주제 분류</option>
              <option value="1">자유</option>
              <option value="2">질문</option>
              <option value="3">유머</option>
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
