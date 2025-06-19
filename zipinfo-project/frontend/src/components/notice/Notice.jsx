import React, { useEffect, useState, useContext } from "react";
import { fetchPosts, deletePost } from "./noticeApi"; // API 함수 임포트
import Pagination from "../common/Pagination";
import { useNavigate } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext"; // Context 임포트
import "../../css/notice/Notice.css";

const Notice = () => {
  const [posts, setPosts] = useState([]);
  const [pageInfo, setPageInfo] = useState({ currentPage: 0, totalPages: 0 });
  const [keyword, setKeyword] = useState("");
  const navigate = useNavigate();

  // 변경 후
  const authContext = useContext(AuthContext);
  const user = authContext?.user || null;
  const isAdmin = user?.memberRole?.toUpperCase() === "ADMIN";

  const loadPosts = async (page) => {
    try {
      const data = await fetchPosts(page, 10, keyword);
      setPosts(data.content);
      setPageInfo({ currentPage: data.number, totalPages: data.totalPages });
    } catch (error) {
      console.error("공지사항 불러오기 실패", error);
    }
  };

  const handleSearch = () => {
    loadPosts(0);
  };

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
    <div className="notice-container">
      <h2> 공지사항</h2>

      <div className="notice-search">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="제목 검색"
        />
        <button onClick={handleSearch}>검색</button>
      </div>

      <table className="notice-table">
        <thead>
          <tr>
            <th>번호</th>
            <th>제목</th>
            <th>작성자</th>
            <th>작성일</th>
            {isAdmin && <th>관리</th>}
          </tr>
        </thead>
        <tbody>
          {posts.map((post) => (
            <tr key={post.id}>
              <td>{post.id}</td>
              <td
                className="notice-title"
                onClick={() => navigate(`/notice/detail/${post.id}`)}
              >
                {post.title}
              </td>
              <td>{post.author}</td>
              <td>{new Date(post.createdAt).toLocaleDateString()}</td>
              {isAdmin && (
                <td>
                  <button onClick={() => navigate(`/notice/edit/${post.id}`)}>
                    수정
                  </button>
                  <button onClick={() => handleDelete(post.id)}>삭제</button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>

      <div className="notice-bottom">
        {isAdmin && (
          <button
            className="write-button"
            onClick={() => navigate("/notice/write")}
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
export default Notice; // ← 이 부분이 반드시 있어야 함
