import React, { useState, useEffect, useContext } from "react";
import "../../css/neighborhood/NeighborhoodBoard.css";
import arrowDown from "../../assets/arrow-down.svg";
import { CITY, TOWN } from "../common/Gonggong";
import { useNavigate } from "react-router-dom";
import { axiosAPI } from "../../api/axiosAPI";

const NeighborhoodBoard = ({}) => {
  const [currentPage, setCurrentPage] = useState(1); // cp를 관리해서 뒤로가기 시 유지시키기 위함
  const [selectedCity, setSelectedCity] = useState(-1); // 선택된 시도 (e.target)
  const [selectedTown, setSelectedTown] = useState(-1); // 선택된 시군구 (e.target)
  const [selectedSubject, setSelectedSubject] = useState(-1); // 선택된 주제 (e.target)
  const [searchKey, setSearchKey] = useState("t"); // 일단 t만하고 c는 내용 tc는 제목+내용 w는 작성자로 확장하자.
  const [searchQuery, setSearchQuery] = useState(""); //실제로 검색될 대상

  //--------------서버(DB)에서 받을 데이터에 대하여--------------//
  const [boardList, setBoardList] = useState([]);

  const [pagination, setPagination] = useState({});

  const [boardName, setBoardName] = useState("게시판");

  const [loginMember, setLoginMember] = useState(null);

  const [loading, setLoading] = useState(true);

  const navigate = useNavigate();

  //--------------서버(DB)에서 받을 데이터 마무리--------------//

  //--------------데이터를 매번 로딩 하는 방법 = useEffect 생성--------------//

  // 따로 서버에서 갔다와서 로드하는 기능을 가져온다음 페이지네이션은 그 이후에 진행하도록 한다

  // 페이지네이션 자체를 만들어내는 함수 만약 nextPage prevPage가 없다면 0이나 false를 반환한다.
  const calculatePage = () => {
    const { startPage, endPage, maxPage } = pagination;
    if (pagination.nextPage !== undefined && pagination.prevPage !== undefined)
      return {
        prevPage: pagination.prevPage,
        nextPage: pagination.nextPage,
        hasPrev: pagination.prevPage > 0,
        hasNext: pagination.nextPage <= maxPage,
      };
    return {
      prevPage: 0,
      nextPage: 0,
      hasPrev: false,
      hasNext: false,
    };
  };

  //
  const handleSearch = () => {
    if (currentPage != 1) {
      setCurrentPage(1);
    } else {
      boardData();
    }
  };

  const handlePaginationChange = (page) => setCurrentPage(page);

  // 서버에서 온 board객체에 대해 boardNo가 넘어올거니까 그에 따라 상세페이지로 이동

  const handleBoardClick = async (item) => {
    navigate(`/neighborhoodBoard/detail/${item.boardNo}`);
  };

  // 글쓰기 버튼에서 이동하게 만들 함수
  const handleBoardWriteClick = (board) => {
    navigate(`/editBoard/insert`);
  };

  const renderPagination = () => {
    if (!pagination.maxPage) return [];
    const pages = [];
    for (let i = pagination.startPage; i <= pagination.endPage; i++) {
      pages.push(i); // 페이지 네이션 부분 자체를 1234...10까지 표현하게끔 만들기 위해 필요한 함수
    }
    return pages;
  };

  // 다음 함수를 소환하여 총 페이지 개수를 pages에 상수로 저장한다.
  const pages = renderPagination();

  const { prevPage, nextPage, hasPrev, hasNext } = calculatePage();

  const boardData = async () => {
    try {
      setLoading(true);

      // 1) 쿼리 파라미터 조립 (키, 검색어자체, 선택된 시도, 선택된 시군구, 선택된 주제라는 5개의 선택 사항이 쿼리로 넘어가 하나도 없을 떄만 일반적인 페이지네이션 리스트로 가게 됨)
      const params = new URLSearchParams({ cp: currentPage });
      if (searchQuery.trim()) {
        params.append("key", searchKey);
        params.append("query", searchQuery);
        params.append("city", selectedCity);
        params.append("town", selectedTown);
        params.append("subject", selectedSubject);
      }

      // 2) API 호출
      const resp = await axiosAPI.get(`/board/neighborhoodList?${params}`);
      const { boardList = [], pagination = {} } = resp.data; // 구조 분해

      // 3) 상태 저장
      setBoardList(boardList);
      setPagination(pagination);
    } catch (err) {
      console.error("페이지 로딩 중 오류", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    boardData();
  }, [currentPage, searchKey, searchQuery]);

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
        {/* 여기에 검색 영역을 넣으라는 말인듯 */}
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
            <img
              className="arrow-icon"
              src={arrowDown}
              alt="아래 화살표 아이콘을 통한 select 화면"
            />
          </div>

          <div className="select-wrap">
            <select
              className="nb-select"
              value={selectedTown}
              onChange={handleTownChange}
              disabled={selectedCity === -1}
            >
              <option value={-1}>시/군/구</option>
              {filteredTowns.map((town) => (
                <option key={town.fullcode} value={town.fullcode}>
                  {town.name}
                </option>
              ))}
            </select>

            <img
              className="arrow-icon"
              src={arrowDown}
              alt="아래 화살표 아이콘을 통한 select 화면"
            />
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
        {/* <div className="nb-search">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="검색어를 입력하세요"
          />
          <button className="nb-search-btn" onClick={handleSearch}>
            검색
          </button>
        </div> */}

        <div className="nb-board-table">
          <div className="nb-header">
            <div className="nb-header-cell nb-header-number">번호</div>
            <div className="nb-header-cell nb-header-title">제목</div>
            <div className="nb-header-cell nb-header-author">작성자</div>
            <div className="nb-header-cell nb-header-date">날짜</div>
            <div className="nb-header-cell nb-header-views">조회</div>
          </div>

          {boardList.map((item, index) => (
            <div key={index} className="nb-row">
              <div className="nb-cell nb-cell-number">{item.boardNo}</div>
              <div
                className="nb-cell nb-cell-title"
                onClick={() => handleBoardClick(item)} // 클릭 이벤트로 상세화면으로 이동하게
                style={{ cursor: "pointer" }}
              >
                {item.boardTitle}
              </div>
              <div className="nb-cell nb-cell-author">
                {item.memberNickName}
              </div>
              <div className="nb-cell nb-cell-date">{item.boardWriteDate}</div>
              <div className="nb-cell nb-cell-views">{item.readCount}</div>
            </div>
          ))}
        </div>

        <div className="nb-pagination-container">
          <div className="nb-pagination">
            <button
              className="nb-page-btn nb-page-prev"
              onClick={() => handlePaginationChange(1)}
              disabled={currentPage === 1}
            >
              ‹‹
            </button>
            <button
              className="nb-page-btn nb-page-prev"
              onClick={() => handlePaginationChange(prevPage)}
              disabled={!hasPrev}
            >
              ‹
            </button>

            {/* 페이지 번호를 나타내는 함수 */}
            {pages.map((page) => (
              <button
                key={page}
                className={`nb-page-btn ${
                  page === currentPage ? "nb-page-active" : ""
                }`}
                onClick={() => handlePaginationChange(page)}
              >
                {page.toString().padStart(2, "0")}
              </button>
            ))}
            <button
              className="nb-page-btn nb-page-next"
              onClick={() => {
                handlePaginationChange(nextPage);
              }}
              disabled={!hasNext}
            >
              ›
            </button>
            <button
              className="nb-page-btn nb-page-next"
              onClick={() => {
                handlePaginationChange(pagination.maxPage);
              }}
              disabled={!hasNext}
            >
              ››
            </button>
          </div>

          <button className="nb-write-btn" onClick={handleBoardWriteClick}>
            글쓰기
          </button>
        </div>
      </div>
    </div>
  );
};

export default NeighborhoodBoard;
