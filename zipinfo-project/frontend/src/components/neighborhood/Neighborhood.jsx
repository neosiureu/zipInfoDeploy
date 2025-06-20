import React, { useEffect, useState } from "react";
import Pagination from "../common/Pagination";
import { useNavigate } from "react-router-dom";
import "../../css/neighborhood/Neighborhood.css";
import { fetchPosts } from "../../api/neighborhoodApi";

const NeighborhoodBoard = () => {
  const [posts, setPosts] = useState([]);
  const [pageInfo, setPageInfo] = useState({ currentPage: 0, totalPages: 0 });
  const [keyword, setKeyword] = useState("");
  const [filters, setFilters] = useState({
    city: "",
    district: "",
    category: "",
  });

  const navigate = useNavigate();

  const loadPosts = async (page) => {
    try {
      const data = await fetchPosts(page, 10, keyword, filters);
      setPosts(data.content);
      setPageInfo({ currentPage: data.number, totalPages: data.totalPages });
    } catch (error) {
      console.error("게시글 불러오기 실패", error);
    }
  };

  const handleSearch = () => {
    loadPosts(0);
  };

  useEffect(() => {
    loadPosts(0);
  }, []);

  return (
    <div className="notice-container">
      <h2>🏘 우리동네 게시판</h2>

      <div className="neighborhood-filter">
        <select
          value={filters.city}
          onChange={(e) => setFilters({ ...filters, city: e.target.value })}
        >
          <option value="">시/도 선택</option>
          <option value="서울">서울</option>
          <option value="경기">경기</option>
          <option value="부산">부산</option>
        </select>

        <select
          value={filters.district}
          onChange={(e) => setFilters({ ...filters, district: e.target.value })}
        >
          <option value="">구/군 선택</option>
          <option value="강남구">강남구</option>
          <option value="서초구">서초구</option>
          <option value="수원시">수원시</option>
        </select>

        <select
          value={filters.category}
          onChange={(e) => setFilters({ ...filters, category: e.target.value })}
        >
          <option value="">주제 분류</option>
          <option value="정보">정보</option>
          <option value="질문">질문</option>
          <option value="거래">거래</option>
        </select>
      </div>

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
          </tr>
        </thead>
        <tbody>
          {posts.map((post) => (
            <tr key={post.boardNo}>
              <td>{post.boardNo}</td>
              <td
                className="notice-title"
                onClick={() => navigate(`/neighborhood/detail/${post.boardNo}`)}
                style={{ cursor: "pointer" }}
              >
                [{post.category}] {post.title}
              </td>
              <td>{post.author}</td>
              <td>{new Date(post.createdAt).toLocaleDateString()}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="neighborhood-bottom">
        <button
          className="write-button"
          onClick={() => navigate("/NeighborhoodWrite")}
          style={{ marginBottom: "10px" }}
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
  );
};

export default NeighborhoodBoard;
