import React, { useEffect, useState, useContext } from "react";
import { fetchPosts } from "./AnnounceApi";
import Pagination from "../common/Pagination";
import { useNavigate } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import "../../css/announce/Announce.css";

const Announce = () => {
  const [posts, setPosts] = useState([]);
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

  return (
    <div className="announce-container">
      <h2>공지사항</h2>

      <div className="announce-search">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="제목 검색"
        />
        <button onClick={handleSearch}>검색</button>
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
                <tr key={id}>
                  <td>{id}</td>
                  <td
                    className="announce-title"
                    onClick={() => navigate(`/announce/detail/${id}`)}
                    style={{ cursor: "pointer" }}
                  >
                    {title}
                  </td>
                  <td>{author}</td>
                  <td>{date}</td>
                </tr>
              );
            })
          ) : (
            <tr>
              <td colSpan={4} style={{ textAlign: "center" }}>
                게시글이 없습니다.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      <div className="announce-bottom">
        {isAdmin && (
          <button
            className="write-button"
            onClick={() => navigate("/announce/write")}
          >
            글쓰기
          </button>
        )}

        <Pagination
          currentPage={pageInfo.currentPage}
          totalPages={pageInfo.totalPages}
          onPageChange={loadPosts}
        />
      </div>
    </div>
  );
};

export default Announce;
