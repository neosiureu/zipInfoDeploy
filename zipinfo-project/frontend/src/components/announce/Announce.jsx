import React, { useEffect, useState, useContext } from "react";
import { fetchPosts, deletePost } from "./AnnounceApi"; // API 함수 임포트
import Pagination from "../common/Pagination";
import { useNavigate } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import "../../css/announce/Announce.css";

const Announce = () => {
  const [posts, setPosts] = useState([]);
  const [pageInfo, setPageInfo] = useState({ currentPage: 0, totalPages: 0 });
  const [keyword, setKeyword] = useState("");
  const navigate = useNavigate();

  const authContext = useContext(AuthContext);
  const user = authContext?.user || null;
  console.log("user 전체:", user); // 여기에 memberAuth가 제대로 들어있는지
  const isAdmin = user && (user.memberAuth === 0 || user.authority === 0);

  console.log("Auth user:", user);
  console.log("isAdmin?", isAdmin);

  const loadPosts = async (page) => {
    try {
      const data = await fetchPosts(page, 10, keyword);
      setPosts(data.content || []);
      setPageInfo({
        currentPage: data.number ?? 0,
        totalPages: data.totalPages ?? 0,
      });
    } catch (error) {
      console.error("공지사항 불러오기 실패", error);
      setPosts([]);
      setPageInfo({ currentPage: 0, totalPages: 0 });
    }
  };

  const handleSearch = () => {
    loadPosts(0);
  };

  // 삭제 함수 유지 가능 (수정/삭제 버튼 UI가 없으면 호출 안 됨)
  const handleDelete = async (id) => {
    if (window.confirm("정말 삭제하시겠습니까?")) {
      await deletePost(id);
      loadPosts(pageInfo.currentPage);
    }
  };

  useEffect(() => {
    loadPosts(0);
  }, []);

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
            {/* 수정/삭제 버튼 관련 th 제거 */}
          </tr>
        </thead>
        <tbody>
          {Array.isArray(posts) && posts.length > 0 ? (
            posts.map((post) => (
              <tr key={post.id ?? post.boardNo}>
                <td>{post.id ?? post.boardNo ?? "N/A"}</td>
                <td
                  className="announce-title"
                  onClick={() =>
                    navigate(`/announce/detail/${post.id ?? post.boardNo}`)
                  }
                  style={{ cursor: "pointer" }}
                >
                  {post.title ?? "제목 없음"}
                </td>
                <td>{post.author ?? post.writer ?? "작성자 없음"}</td>
                <td>
                  {post.createdAt
                    ? new Date(post.createdAt).toLocaleDateString()
                    : post.createDate
                    ? new Date(post.createDate).toLocaleDateString()
                    : "날짜 없음"}
                </td>
                {/* 수정/삭제 버튼 관련 td 제거 */}
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan="4" style={{ textAlign: "center" }}>
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
