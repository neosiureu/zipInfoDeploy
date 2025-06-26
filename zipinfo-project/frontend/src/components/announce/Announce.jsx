import React, { useEffect, useState, useContext, useMemo } from "react";
import { fetchPosts } from "../../api/AnnounceApi";
import { useNavigate } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import "../../css/announce/Announce.css";

const Announce = () => {
  const [posts, setPosts] = useState([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [keyword, setKeyword] = useState("");

  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  // 관리자 여부 판단: memberAuth === 0
  const isAdmin = useMemo(() => {
    if (!user) return false;
    const memberAuth = user.memberAuth ?? user.member_auth;
    return Number(memberAuth) === 0;
  }, [user]);

  // 디버깅용 콘솔 로그
  useEffect(() => {
    console.log("현재 user:", user);
    console.log("isAdmin:", isAdmin);
  }, [user, isAdmin]);

  // 공지사항 불러오기
  const loadPosts = async (page = 0) => {
    try {
      const data = await fetchPosts(page, 10, keyword);
      setPosts(data.posts || []);
      setCurrentPage(page);
      setTotalPages(data.totalPages || 1);
    } catch (error) {
      console.error("공지사항 불러오기 실패", error);
      setPosts([]);
      setCurrentPage(0);
      setTotalPages(1);
    }
  };

  useEffect(() => {
    loadPosts(0);
  }, []);

  // 검색
  const handleSearch = () => {
    loadPosts(0);
  };

  // 페이지네이션
  const renderPagination = () => {
    const pageButtons = [];
    for (let i = 0; i < totalPages; i++) {
      pageButtons.push(
        <button
          key={i}
          className={`an-page-btn ${i === currentPage ? "an-page-active" : ""}`}
          onClick={() => loadPosts(i)}
        >
          {(i + 1).toString().padStart(2, "0")}
        </button>
      );
    }
    return pageButtons;
  };

  // 글쓰기 버튼 클릭 시 관리자 확인 및 이동
  const handleWriteClick = () => {
    if (!isAdmin) {
      alert("관리자만 글을 작성할 수 있습니다.");
      return;
    }
    navigate("/announce/write");
  };

  return (
    <div className="an-container">
      <div className="an-board-wrapper">
        <h1 className="an-title">공지사항</h1>

        {/* 검색 UI */}
        <div style={{ marginBottom: "1rem" }}>
          <input
            type="text"
            placeholder="검색어를 입력하세요"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
            style={{ padding: "0.5rem", width: "200px" }}
          />
          <button
            onClick={handleSearch}
            style={{ marginLeft: "0.5rem", padding: "0.5rem 1rem" }}
          >
            검색
          </button>
        </div>

        {/* 게시판 테이블 */}
        <div className="an-board-table">
          <div className="an-header">
            <div className="an-header-cell an-header-number">번호</div>
            <div className="an-header-cell an-header-title">제목</div>
            <div className="an-header-cell an-header-author">작성자</div>
            <div className="an-header-cell an-header-date">날짜</div>
            <div className="an-header-cell an-header-views">조회</div>
          </div>

          <div className="an-body">
            {posts.length > 0 ? (
              posts.map((post) => {
                const id = post.boardNo;
                return (
                  <div key={id} className="an-row">
                    <div className="an-cell an-cell-number">{id}</div>
                    <div
                      className="an-cell an-cell-title"
                      onClick={() => navigate(`/announce/detail/${id}`)}
                      style={{ cursor: "pointer" }}
                    >
                      {post.announceTitle ?? "제목 없음"}
                    </div>
                    <div className="an-cell an-cell-author">
                      {post.memberNickname ?? "작성자 없음"}
                    </div>
                    <div className="an-cell an-cell-date">
                      {post.announceWriteDate
                        ? new Date(post.announceWriteDate).toLocaleDateString()
                        : "날짜 없음"}
                    </div>
                    <div className="an-cell an-cell-views">
                      {post.announceReadCount ?? 0}
                    </div>
                  </div>
                );
              })
            ) : (
              <div className="an-row">
                <div
                  className="an-cell"
                  style={{ gridColumn: "1 / -1", textAlign: "center" }}
                >
                  게시글이 없습니다.
                </div>
              </div>
            )}
          </div>
        </div>

        {/* 페이지네이션 + 글쓰기 */}
        <div className="an-pagination-container">
          <div className="an-pagination">
            <button
              className="an-page-btn"
              onClick={() => loadPosts(Math.max(0, currentPage - 1))}
            >
              ‹
            </button>
            {renderPagination()}
            <button
              className="an-page-btn"
              onClick={() =>
                loadPosts(Math.min(totalPages - 1, currentPage + 1))
              }
            >
              ›
            </button>
          </div>

          {/* ✅ 관리자만 글쓰기 버튼 표시 */}
          {isAdmin && (
            <button className="an-write-btn" onClick={handleWriteClick}>
              글쓰기
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default Announce;
