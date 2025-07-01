import React, { useState, useEffect, useContext, useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import "../../css/neighborhood/NeighborhoodBoard.css";
import { useNavigate } from "react-router-dom";
import { axiosAPI } from "../../api/axiosAPI";
import NeighborhoodFilters from "./NeighborhoodFilters";
import { MemberContext } from "../member/MemberContext";

const NeighborhoodBoard = ({}) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const { member } = useContext(MemberContext);

  const initCp = Number(searchParams.get("cp") ?? 1);

  const [currentPage, setCurrentPage] = useState(initCp); // cp를 관리해서 뒤로가기 시 유지시키기 위함
  const [selectedCity, setSelectedCity] = useState(
    searchParams.get("city") || -1
  ); // 선택된 시도 (e.target)
  const [selectedTown, setSelectedTown] = useState(
    searchParams.get("town") || -1
  ); // 선택된 시군구 (e.target)
  const [selectedSubject, setSelectedSubject] = useState(
    searchParams.get("subject") || -1
  ); // 선택된 주제 (e.target)
  const [searchKey, setSearchKey] = useState("t"); // 일단 t만하고 c는 내용 tc는 제목+내용 w는 작성자로 확장하자.
  const [searchQuery, setSearchQuery] = useState(
    searchParams.get("query") || ""
  ); //실제로 검색될 대상

  //--------------서버(DB)에서 받을 데이터에 대하여--------------//
  const [boardList, setBoardList] = useState([]);

  const [pagination, setPagination] = useState({});

  const [loading, setLoading] = useState(true);

  const navigate = useNavigate();

  //--------------서버(DB)에서 받을 데이터 마무리--------------//

  //--------------데이터를 매번 로딩 하는 방법 = useEffect 생성--------------//

  // 따로 서버에서 갔다와서 로드하는 기능을 가져온다음 페이지네이션은 그 이후에 진행하도록 한다

  // 페이지네이션 자체를 만들어내는 함수 만약 nextPage prevPage가 없다면 0이나 false를 반환한다.

  // 초기값 변경

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

  const handlePaginationChange = (page) => {
    setCurrentPage(page);
    const params = new URLSearchParams(searchParams);
    params.set("cp", page);
    // navigate(`/neighborhoodBoard?${params}`);
  };

  // 서버에서 온 board객체에 대해 boardNo가 넘어올거니까 그에 따라 상세페이지로 이동

  const handleBoardClick = (item) => {
    navigate(`/neighborhoodBoard/detail/${item.boardNo}?cp=${currentPage}`);
  };

  // 글쓰기 버튼에서 이동하게 만들 함수
  const handleBoardWriteClick = (board) => {
    navigate(
      `/neighborhoodBoard/edit${currentPage ? `?cp=${currentPage}` : ""}`
    );
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
      }
      if (selectedCity !== -1) {
        params.append("city", selectedCity);
      }
      if (selectedTown !== -1) {
        params.append("town", selectedTown);
      }
      if (selectedSubject !== -1) {
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
  }, [
    currentPage,
    searchKey,
    searchQuery,
    selectedCity,
    selectedTown,
    selectedSubject,
  ]); // 진짜 검색의 로직

  // // 선택된 시도에 해당하는 시군구만 필터링
  // const filteredTowns =
  //   selectedCity !== -1
  //     ? TOWN.filter((town) => town.code === parseInt(selectedCity))
  //     : [];

  // 시도 선택 핸들러
  const handleCityChange = async (e) => {
    setSelectedCity(e.target.value);
    setSelectedTown(-1); // 시도 변경시 시군구 초기화
    setCurrentPage(1);
  };

  // 시군구 선택 핸들러
  const handleTownChange = (e) => {
    setSelectedTown(e.target.value);
    setCurrentPage(1);
  };

  // 주제 선택 핸들러
  const handleSubjectChange = (e) => {
    setSelectedSubject(e.target.value);
  };

  const updateUrlParams = useCallback(() => {
    setSearchParams(
      (prev) => {
        const newParams = new URLSearchParams(prev);
        // 현재 페이지를 서치 파라미터에 넘긴다
        newParams.set("cp", currentPage);
        // 검색 관련
        // 쿼리가 있는경우와 없는 경우
        if (searchQuery.trim()) {
          newParams.set("query", searchQuery);
        } else {
          newParams.delete("query");
        }
        if (selectedCity !== -1) {
          newParams.set("city", selectedCity);
        } else {
          newParams.delete("city");
        }
        if (selectedTown !== -1) {
          newParams.set("town", selectedTown);
        } else {
          newParams.delete("town");
        }

        if (selectedSubject !== -1) {
          newParams.set("subject", selectedSubject);
        } else {
          newParams.delete("subject");
        }
        return newParams;
      },
      { replace: true } // 뒤로가기방지가 ux적으로 맞을듯
    );
  });

  useEffect(() => {
    if (currentPage !== 1) {
      boardData();
      updateUrlParams();
    }
    // cp는 따로 무조건 검색할때마다 1페이지로 향해야 한다
    // 가령 3페이지를 보고있다가 부산을 검색하면 부산만의 1페이지로 가야 논리적으로 맞을듯
  }, [
    currentPage,
    searchKey,
    searchQuery,
    searchKey,
    selectedCity,
    selectedTown,
    selectedSubject,
  ]);

  useEffect(() => {
    if (currentPage !== 1) setCurrentPage(1);
    else {
      updateUrlParams();
    }
  }, [searchKey, searchQuery, selectedCity, selectedSubject, selectedTown]);
  return (
    <div className="nb-container">
      <div className="nb-board-wrapper">
        <h1 className="nb-title">우리동네</h1>
        {/* 여기에 검색 영역을 넣으라는 말인듯 */}
        {/* 공통 필터 컴포넌트 */}
        <NeighborhoodFilters
          selectedCity={selectedCity}
          selectedTown={selectedTown}
          selectedSubject={selectedSubject}
          onCityChange={handleCityChange}
          onTownChange={handleTownChange}
          onSubjectChange={handleSubjectChange}
        />
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
            <div className="nb-header-cell nb-header-subject">분류</div>
            <div className="nb-header-cell nb-header-title">제목</div>
            <div className="nb-header-cell nb-header-area">지역</div>

            <div className="nb-header-cell nb-header-author">작성자</div>
            {/* <div className="nb-header-cell nb-header-author">좋아요</div> */}
            <div className="nb-header-cell nb-header-date">날짜</div>
            <div className="nb-header-cell nb-header-views">조회</div>
          </div>

          {boardList.map((item, index) => (
            <div key={index} className="nb-row">
              <div className="nb-cell nb-cell-number">{item.boardNo}</div>
              <div className="nb-cell nb-cell-subject">
                {item.boardSubject === "Q"
                  ? "질문답변"
                  : item.boardSubject === "R"
                  ? "리뷰"
                  : "자유"}
              </div>
              <div
                className="nb-cell nb-cell-title"
                onClick={() => handleBoardClick(item)} // 클릭 이벤트로 상세화면으로 이동하게
                style={{ cursor: "pointer" }}
              >
                {item.boardTitle}
              </div>
              <div className="nb-cell nb-cell-area">
                {item.cityName} {">"} {item.townName}
              </div>
              <div className="nb-cell nb-cell-author">
                {item.memberNickName}
              </div>
              <div className="nb-cell nb-cell-date">{item.boardWriteDate}</div>
              {/* <div className="nb-cell nb-cell-date">{item.like}</div> */}
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
          {member ? (
            <button className="nb-write-btn" onClick={handleBoardWriteClick}>
              글쓰기
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
};

export default NeighborhoodBoard;
