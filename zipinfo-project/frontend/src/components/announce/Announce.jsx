import React, { useEffect, useState, useContext } from "react";
import { fetchPosts } from "./AnnounceApi";
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

  const isAdmin = user && (user.memberAuth === 0 || user.memberAuth === "0");

  console.log("🔍 현재 user:", user);
  console.log("🔐 현재 memberAuth:", user?.memberAuth);
  console.log("🧑‍💻 관리자 여부:", isAdmin);

  useEffect(() => {
    loadPosts(0);
  }, []);

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

  const handleSearch = () => {
    loadPosts(0);
  };

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

  return (
    <div className="an-container">
      <div className="an-board-wrapper">
        <h1 className="an-title">공지사항</h1>

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
                    >
                      {post.boardTitle ?? "제목 없음"}
                    </div>
                    <div className="an-cell an-cell-author">
                      {post.memberNickname ?? "작성자 없음"}
                    </div>
                    <div className="an-cell an-cell-date">
                      {post.boardWriteDate
                        ? new Date(post.boardWriteDate).toLocaleDateString()
                        : "날짜 없음"}
                    </div>
                    <div className="an-cell an-cell-views">0</div>
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

          {isAdmin && (
            <button
              className="an-write-btn"
              onClick={() => navigate("/announce/write")}
            >
              글쓰기
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default Announce;
