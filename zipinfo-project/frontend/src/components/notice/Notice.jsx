import React, { useEffect, useState, useContext } from "react";
import { fetchPosts, deletePost } from "./boardApi";
import Pagination from "./Pagination";
import { useNavigate } from "react-router-dom";
import { AuthContext } from "./AuthContext";
import "../../css/notice/Notice.css"; // 스타일을 따로 분리

// 공통 헤더, 푸터 import (경로는 프로젝트 구조에 맞게 수정하세요)
import Header from "../../components/common/Header";
import Footer from "../../components/common/Footer";

const Notice = () => {
  const [posts, setPosts] = useState([]);
  const [pageInfo, setPageInfo] = useState({ currentPage: 0, totalPages: 0 });
  const [keyword, setKeyword] = useState("");
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);
  const isAdmin = user?.role === "ADMIN";

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
    <>
      <Header />

      <div className="notice-container">
        <h2>📢 공지사항</h2>

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
                  onClick={() => navigate(`/notice/${post.id}`)}
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
          <button
            className="write-button"
            onClick={() => navigate("/notice/write")}
          >
            글쓰기
          </button>
          <Pagination
            currentPage={pageInfo.currentPage}
            totalPages={pageInfo.totalPages}
            onPageChange={loadPosts}
          />
        </div>
      </div>

      <Footer />
    </>
  );
};

export default Notice;
