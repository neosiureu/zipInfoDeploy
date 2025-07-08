import React, {
  useState,
  useEffect,
  useContext,
  useCallback,
  useRef,
} from "react";
import { useSearchParams } from "react-router-dom";
import "../../css/neighborhood/NeighborhoodBoard.css";
import { useNavigate } from "react-router-dom";
import { axiosAPI } from "../../api/axiosApi";
import NeighborhoodFilters from "./NeighborhoodFilters";
import { MemberContext } from "../member/MemberContext";
import search from "../../assets/search-icon.svg";
import arrowDown from "../../assets/arrow-down.svg";
import refresh from "../../assets/refresh.svg";

const NeighborhoodBoard = ({}) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const { member } = useContext(MemberContext);

  const initCp = Number(searchParams.get("cp") ?? 1);

  const [currentPage, setCurrentPage] = useState(initCp);
  const [selectedCity, setSelectedCity] = useState(
    searchParams.get("cityNo") || "-1"
  );
  const [selectedTown, setSelectedTown] = useState(
    searchParams.get("townNo") || "-1"
  );
  const [selectedSubject, setSelectedSubject] = useState(
    searchParams.get("boardsubject") || "-1"
  );

  //  수정: 검색 상태를 하나로 통합
  const [searchKey, setSearchKey] = useState(searchParams.get("key") || "t");
  const [searchQuery, setSearchQuery] = useState(
    searchParams.get("query") || ""
  );

  //  추가: 검색 실행 상태 관리
  const [isSearching, setIsSearching] = useState(false);

  const [boardList, setBoardList] = useState([]);
  const [pagination, setPagination] = useState({});
  const [loading, setLoading] = useState(true);

  const navigate = useNavigate();

  // 새로고침 함수
  const handleRefresh = () => {
    setSelectedCity("-1");
    setSelectedTown("-1");
    setSelectedSubject("-1");
    setCurrentPage(1);
    setSearchQuery("");
    setSearchKey("t");
    setSearchParams({}, { replace: true });

    const elements = document.querySelectorAll(".glow-target");
    if (elements) {
      elements.forEach((el) => {
        el.classList.add("select-glow");
        setTimeout(() => el.classList.remove("select-glow"), 200);
      });
    }
  };

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

  //  수정: 검색 함수 개선
  const handleSearch = useCallback(() => {
    console.log("🔍 검색 실행:", { searchKey, searchQuery });

    setCurrentPage(1);
    setIsSearching(true);

    setSearchParams(
      (prev) => {
        const newParams = new URLSearchParams(prev);
        newParams.set("cp", 1);

        if (searchQuery.trim()) {
          newParams.set("query", searchQuery.trim());
          newParams.set("key", searchKey);
        } else {
          newParams.delete("query");
          newParams.delete("key");
        }

        // 기존 필터 유지
        if (selectedCity !== "-1") {
          newParams.set("cityNo", selectedCity);
        }
        if (selectedTown !== "-1") {
          newParams.set("townNo", selectedTown);
        }
        if (selectedSubject !== "-1") {
          newParams.set("boardsubject", selectedSubject);
        }

        return newParams;
      },
      { replace: true }
    );
  }, [
    searchKey,
    searchQuery,
    selectedCity,
    selectedTown,
    selectedSubject,
    setSearchParams,
  ]);

  const handleSearchKeyChange = (e) => {
    const newKey = e.target.value;
    setSearchKey(newKey);
  };

  const handleSearchQueryChange = (e) => {
    const newQuery = e.target.value;
    setSearchQuery(newQuery);
  };

  const searchKeyOptions = [
    { value: "t", label: "제목" },
    { value: "c", label: "내용" },
    { value: "tc", label: "제목+내용" },
    { value: "w", label: "작성자" },
  ];

  const handlePaginationChange = (page) => {
    setCurrentPage(page);
    const params = new URLSearchParams(searchParams);
    params.set("cp", page);
  };

  const handleBoardClick = (item) => {
    navigate(`/neighborhoodBoard/detail/${item.boardNo}?cp=${currentPage}`);
  };

  const handleBoardWriteClick = (board) => {
    navigate(
      `/neighborhoodBoard/edit${currentPage ? `?cp=${currentPage}` : ""}`
    );
  };

  const renderPagination = () => {
    if (!pagination.maxPage) return [];
    const pages = [];
    for (let i = pagination.startPage; i <= pagination.endPage; i++) {
      pages.push(i);
    }
    return pages;
  };

  const pages = renderPagination();
  const { prevPage, nextPage, hasPrev, hasNext } = calculatePage();

  //  수정: boardData 함수 개선
  const boardData = useCallback(async () => {
    try {
      setLoading(true);
      console.log("📡 API 호출:", {
        currentPage,
        searchKey,
        searchQuery: searchQuery.trim(),
        selectedCity,
        selectedTown,
        selectedSubject,
      });

      //  수정: URL params에서 실제 값 가져오기
      const urlSearchKey = searchParams.get("key") || searchKey;
      const urlSearchQuery = searchParams.get("query") || "";

      const params = new URLSearchParams({ cp: currentPage });

      if (urlSearchQuery.trim()) {
        params.append("key", urlSearchKey);
        params.append("query", urlSearchQuery);
      }
      if (selectedCity !== "-1") {
        params.append("cityNo", selectedCity);
      }
      if (selectedTown !== "-1") {
        params.append("townNo", selectedTown);
      }
      if (selectedSubject !== "-1") {
        params.append("boardSubject", selectedSubject);
      }

      console.log("📤 실제 전송 파라미터:", params.toString());

      const resp = await axiosAPI.get(`/board/neighborhoodList?${params}`);
      const { boardList = [], pagination = {} } = resp.data;

      console.log("📥 응답 데이터:", {
        boardList: boardList.length,
        pagination,
      });

      setBoardList(boardList);
      setPagination(pagination);
    } catch (err) {
      console.error("페이지 로딩 중 오류", err);
      setBoardList([]);
    } finally {
      setLoading(false);
      setIsSearching(false);
    }
  }, [currentPage, searchParams, selectedCity, selectedTown, selectedSubject]);

  // 시도 선택 핸들러
  const handleCityChange = async (e) => {
    const newCity = e.target.value;
    setSelectedCity(newCity);
    setSelectedTown("-1");
    setCurrentPage(1);

    setSearchParams(
      (prev) => {
        const newParams = new URLSearchParams(prev);
        newParams.set("cp", 1);

        if (newCity !== "-1") {
          newParams.set("cityNo", newCity);
        } else {
          newParams.delete("cityNo");
        }

        newParams.delete("townNo");
        return newParams;
      },
      { replace: true }
    );
  };

  // 시군구 선택 핸들러
  const handleTownChange = (e) => {
    const newTown = e.target.value;
    setSelectedTown(newTown);
    setCurrentPage(1);

    setSearchParams(
      (prev) => {
        const newParams = new URLSearchParams(prev);
        newParams.set("cp", 1);

        if (newTown !== "-1") {
          newParams.set("townNo", newTown);
        } else {
          newParams.delete("townNo");
        }

        return newParams;
      },
      { replace: true }
    );
  };

  // 주제 선택 핸들러
  const handleSubjectChange = (e) => {
    const newSubject = e.target.value;
    setSelectedSubject(newSubject);
    setCurrentPage(1);

    setSearchParams(
      (prev) => {
        const newParams = new URLSearchParams(prev);
        newParams.set("cp", 1);

        if (newSubject !== "-1") {
          newParams.set("boardsubject", newSubject);
        } else {
          newParams.delete("boardsubject");
        }

        return newParams;
      },
      { replace: true }
    );
  };

  //  수정: 단일 useEffect로 통합
  useEffect(() => {
    boardData();
  }, [boardData]);

  //  추가: 검색 파라미터 변경 감지 및 상태 동기화
  useEffect(() => {
    const urlSearchKey = searchParams.get("key");
    const urlSearchQuery = searchParams.get("query");

    if (urlSearchKey && urlSearchKey !== searchKey) {
      setSearchKey(urlSearchKey);
    }
    if (urlSearchQuery !== null && urlSearchQuery !== searchQuery) {
      setSearchQuery(urlSearchQuery);
    }
  }, [searchParams]);

  return (
    <div className="nb-container">
      <div className="nb-board-wrapper">
        <h1 className="nb-title">우리동네</h1>

        <div className="nb-filter-bar">
          <NeighborhoodFilters
            selectedCity={selectedCity}
            selectedTown={selectedTown}
            selectedSubject={selectedSubject}
            onCityChange={handleCityChange}
            onTownChange={handleTownChange}
            onSubjectChange={handleSubjectChange}
          />
          <button className="searcbar-refresh-btn" onClick={handleRefresh}>
            <img src={refresh} alt="새로고침" />
          </button>
        </div>

        {/*  추가: 로딩 상태 표시 */}
        {(loading || isSearching) && (
          <div className="loading-indicator">
            {isSearching ? "검색 중..." : "로딩 중..."}
          </div>
        )}

        <table className="nb-board-table">
          <thead>
            <tr className="nb-header">
              <th className="nb-header-number">번호</th>
              <th className="nb-header-subject">분류</th>
              <th className="nb-header-title">제목</th>
              <th className="nb-header-area">지역</th>
              <th className="nb-header-author">작성자</th>
              <th className="nb-header-likes">좋아요</th>
              <th className="nb-header-date">날짜</th>
              <th className="nb-header-views">조회</th>
            </tr>
          </thead>
          <tbody>
            {boardList.length === 0 ? (
              <tr>
                <td
                  colSpan="8"
                  style={{ textAlign: "center", padding: "12px" }}
                >
                  {loading ? "로딩 중..." : "게시글이 없습니다."}
                </td>
              </tr>
            ) : (
              boardList.map((item, index) => (
                <tr key={`${item.boardNo}-${index}`} className="nb-row">
                  <td className="nb-cell-number">{item.boardNo}</td>
                  <td className="nb-cell-subject">
                    {item.boardSubject === "Q"
                      ? "질문답변"
                      : item.boardSubject === "R"
                      ? "리뷰"
                      : "자유"}
                  </td>
                  <td
                    className="nb-cell-title"
                    onClick={() => handleBoardClick(item)}
                    style={{ cursor: "pointer" }}
                  >
                    {item.boardTitle}
                  </td>
                  <td className="nb-cell-area">
                    {item.cityName} {">"} {item.townName}
                  </td>
                  <td className="nb-cell-author">{item.memberNickName}</td>
                  <td className="nb-cell-likes">{item.likeCount}</td>
                  <td className="nb-cell-date">{item.boardWriteDate}</td>
                  <td className="nb-cell-views">{item.readCount}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>

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

        <div className="nb-search">
          <div className="nb-select-wrap">
            <select
              className="nb-search-type"
              value={searchKey}
              onChange={handleSearchKeyChange}
            >
              {searchKeyOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <img className="arrow-icon" src={arrowDown} alt="아래 아이콘" />
          </div>
          <div className="search-input-wrap">
            <span className="header-search-icon">
              <img src={search} alt="검색 아이콘" />
            </span>
            <input
              className="nb-search-input"
              type="text"
              value={searchQuery}
              onChange={handleSearchQueryChange}
              onKeyDown={(e) => e.key === "Enter" && handleSearch()}
              placeholder="검색어를 입력하세요"
              disabled={isSearching}
            />
          </div>
          <button
            className="nb-search-btn"
            onClick={handleSearch}
            disabled={isSearching}
          >
            {isSearching ? "검색 중..." : "검색"}
          </button>
        </div>
      </div>
    </div>
  );
};

export default NeighborhoodBoard;
