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

  const [currentPage, setCurrentPage] = useState(initCp); // cp를 관리해서 뒤로가기 시 유지시키기 위함
  const [selectedCity, setSelectedCity] = useState(
    searchParams.get("cityNo") || "-1"
  ); // 선택된 시도 (e.target)
  const [selectedTown, setSelectedTown] = useState(
    searchParams.get("townNo") || "-1"
  ); // 선택된 시군구 (e.target)
  const [selectedSubject, setSelectedSubject] = useState(
    searchParams.get("boardsubject") || "-1"
  ); // 선택된 주제 (e.target)
  const [searchKey, setSearchKey] = useState("t"); // 일단 t만하고 c는 내용 tc는 제목+내용 w는 작성자로 확장하자.
  const [searchQuery, setSearchQuery] = useState(
    searchParams.get("query") || ""
  ); //실제로 검색될 대상이지만 일단 화면 표시용 상태를 따로 관리한다
  const [activeSearchQuery, setActiveSearchQuery] = useState(
    searchParams.get("query") || ""
  ); // 실제로 넘길 것

  const [activeSearchKey, setActiveSearchKey] = useState(
    searchParams.get("key") || "t"
  );

  const [likeCount, setLikeCount] = useState(0);
  //--------------서버(DB)에서 받을 데이터에 대하여--------------//
  const [boardList, setBoardList] = useState([]);

  const [pagination, setPagination] = useState({});

  const [loading, setLoading] = useState(true);

  const navigate = useNavigate();

  //--------------서버(DB)에서 받을 데이터 마무리--------------//

  //--------------데이터를 매번 로딩 하는 방법 useEffect 생성--------------//

  // 따로 서버에서 갔다와서 로드하는 기능을 가져온다음 페이지네이션은 그 이후에 진행하도록 한다

  // 페이지네이션 자체를 만들어내는 함수 만약 nextPage prevPage가 없다면 0이나 false를 반환한다.

  // 새로고침 함수
  const handleRefresh = () => {
    setSelectedCity("-1");
    setSelectedTown("-1");
    setSelectedSubject("-1");
    setCurrentPage("1");
    setSearchParams({}, { replace: true });

    // setSearchParams(
    //   (prev) => {
    //     const newParams = new URLSearchParams(prev);
    //     newParams.delete("cp");
    //     newParams.delete("query");
    //     newParams.delete("key");
    //     newParams.delete("subject");
    //     newParams.delete("cityNo");
    //     newParams.delete("townNo");
    //     return newParams;
    //   },
    //   { replace: true }
    // );
    const elements = document.querySelectorAll(".glow-target"); // 모든 .glow-target 속성을 가진 select문 요소들을 저장.
    if (elements) {
      elements.forEach((el) => {
        el.classList.add("select-glow");
        setTimeout(() => el.classList.remove("select-glow"), 200);
      }); // 400ms동안 해당 glow 효과 유지.
    }
  };

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
    setActiveSearchKey(searchKey);
    setActiveSearchQuery(searchQuery);
    setCurrentPage(1);
    setSearchParams(
      (prev) => {
        const newParams = new URLSearchParams(prev);
        newParams.set("cp", 1);

        if (searchQuery.trim()) {
          newParams.set("query", searchQuery);
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

    setTimeout(() => {
      boardData();
    }, 100);
  };

  const handleSearchKeyChange = (e) => {
    const newKey = e.target.value;
    setSearchKey(newKey);
    setCurrentPage(1);
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
      if (activeSearchQuery.trim()) {
        params.append("key", activeSearchKey);
        params.append("query", activeSearchQuery);
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
    activeSearchKey,
    activeSearchQuery,
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
    const newCity = e.target.value;
    setSelectedCity(newCity);
    setSelectedTown("-1"); // 시도 변경시 시군구 초기화
    setCurrentPage(1);

    // URL 파라미터 즉시 업데이트
    setSearchParams(
      (prev) => {
        const newParams = new URLSearchParams(prev);
        newParams.set("cp", 1);

        if (newCity !== "-1") {
          newParams.set("cityNo", newCity);
        } else {
          newParams.delete("cityNo");
        }

        // 시도 변경시 townNo도 제거
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

    // URL 파라미터 즉시 업데이트
    setSearchParams(
      (prev) => {
        const newParams = new URLSearchParams(prev);
        newParams.set("cp", 1);

        if (newTown !== "-1") {
          newParams.set("townNo", newTown);
        } else {
          newParams.delete("townNo"); // -1일 때 파라미터 제거
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

    // URL 파라미터 즉시 업데이트
    setSearchParams(
      (prev) => {
        const newParams = new URLSearchParams(prev);
        newParams.set("cp", 1);

        if (newSubject !== "-1") {
          newParams.set("boardsubject", newSubject);
        } else {
          newParams.delete("boardsubject"); // -1일 때 파라미터 제거
        }

        return newParams;
      },
      { replace: true }
    );
  };

  const updateUrlParams = useCallback(() => {
    setSearchParams(
      (prev) => {
        const newParams = new URLSearchParams(prev);
        // 현재 페이지를 서치 파라미터에 넘긴다
        newParams.set("cp", currentPage);
        // 검색 관련
        // 쿼리가 있는경우와 없는 경우
        if (activeSearchQuery.trim()) {
          newParams.set("query", activeSearchQuery);
          newParams.set("key", activeSearchKey);
        } else {
          newParams.delete("query");
          newParams.delete("key");
        }

        if (selectedCity !== "-1") {
          newParams.set("cityNo", selectedCity);
        } else {
          newParams.delete("cityNo");
        }
        if (selectedTown !== "-1") {
          newParams.set("townNo", selectedTown);
        } else {
          newParams.delete("townNo");
        }

        if (selectedSubject !== "-1") {
          newParams.set("boardsubject", selectedSubject);
        } else {
          newParams.delete("boardsubject");
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
        {/* 여기에 검색 영역을*/}
        {/* 공통 필터 컴포넌트 */}
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
                  게시글이 없습니다.
                </td>
              </tr>
            ) : (
              boardList.map((item, index) => (
                <tr key={index} className="nb-row">
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
            />
          </div>
          <button className="nb-search-btn" onClick={handleSearch}>
            검색
          </button>
        </div>
      </div>
    </div>
  );
};

export default NeighborhoodBoard;
