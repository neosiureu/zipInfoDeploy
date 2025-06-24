import React, { useEffect, useState, useContext } from "react";
import { fetchPosts } from "./AnnounceApi";
import { useNavigate } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import "../../css/announce/Announce.css";
import arrowDown from "../../assets/arrow-down.svg";

const Announce = () => {
  const [posts, setPosts] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageInfo, setPageInfo] = useState({ currentPage: 0, totalPages: 1 });
  const [keyword, setKeyword] = useState("");

  const navigate = useNavigate();
  const { user } = useContext(AuthContext);


  // 관리자 권한 체크: memberAuth가 숫자 0일 때만 true 반환

  const checkAdmin = (user) => {
    if (!user) return false;
    const memberAuth = user.memberAuth ?? null;
    return memberAuth === 0 || memberAuth === "0";
  };

  const isAdmin = checkAdmin(user);

  useEffect(() => {
    loadPosts(0);
  }, []);

  const loadPosts = async (page = 0) => {
    try {
      const data = await fetchPosts(page, 10, keyword);

      // fetchPosts 응답 예: { posts: [], totalPages: number }
      setPosts(data.posts || []);
      setPageInfo({
        currentPage: page,
        totalPages: data.totalPages || 1,
      });
    } catch (error) {
      console.error("공지사항 불러오기 실패", error);
      setPosts([]);
      setPageInfo({ currentPage: 0, totalPages: 1 });
    }
  };

  const handleSearch = () => {
    loadPosts(0); // 검색 시 첫 페이지로
  };


  useEffect(() => {
    loadPosts(0);
  }, []);

  // 페이지네이션용 페이지 배열
  const pages = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

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


      <table className="announce-table">
        <thead>
          <tr>
            <th>번호</th>
            <th>제목</th>
            <th>작성자</th>
            <th>작성일</th>
          </tr>
        </thead>
        <tbody>

          {Array.isArray(posts) && posts.length > 0 ? (
            posts.map((post) => {
              const id = post.boardNo;
              const title = post.boardTitle ?? "제목 없음";
              const author = post.memberNickname ?? "작성자 없음";
              const date = post.boardWriteDate
                ? new Date(post.boardWriteDate).toLocaleDateString()
                : "날짜 없음";

              return (
                <div key={id} className="an-row">
                  <div className="an-cell an-cell-number">{id}</div>
                  <div
                    className="an-cell an-cell-title"
                    onClick={() => navigate(`/announce/detail/${id}`)}
                  >
                    {title}

                  </div>
                  <div className="an-cell an-cell-author">{author}</div>
                  <div className="an-cell an-cell-date">{date}</div>
                  <div className="an-cell an-cell-views">0</div>
                </div>
              );
            })
          ) : (
            <div className="an-row">
              <div
                className="an-cell"
                style={{
                  gridColumn: "1 / -1",
                  textAlign: "center",
                  justifyContent: "center",
                }}
              >
                게시글이 없습니다.
              </div>
            </div>
          )}
        </div>

        <div className="an-pagination-container">
          <div className="an-pagination">
            <button className="an-page-btn an-page-prev">‹</button>
            <button className="an-page-btn an-page-prev">‹‹</button>

            {pages.map((page) => (
              <button
                key={page}
                className={`an-page-btn ${page === 1 ? "an-page-active" : ""}`}
                onClick={() => setCurrentPage(page)}
              >
                {page.toString().padStart(2, "0")}
              </button>
            ))}

            <button className="an-page-btn an-page-next">›</button>
            <button className="an-page-btn an-page-next">››</button>
          </div>

          <button
            className="an-write-btn"
            onClick={() => navigate("/announce/write")}
          >
            글쓰기
          </button>
        </div>
      </div>
    </div>
  );
};

export default Announce;
