import React, { useEffect, useState, useContext, useCallback } from "react";
import Pagination from "../common/Pagination";
import { useNavigate } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";

import "../../css/neighborhood/Neighborhood.css";

// API 함수들 (컴포넌트 밖에서 정의)
const fetchPosts = async (page, size, keyword, filters) => {
  const response = await fetch(
    `/neighborhood/posts?page=${page}&size=${size}&keyword=${keyword}&city=${filters.city}&district=${filters.district}&category=${filters.category}`
  );
  return await response.json();
};

const deletePost = async (id) => {
  const response = await fetch(`/neighborhood/posts/${id}`, {
    method: "DELETE",
  });
  return response.ok;
};

const Neighborhood = () => {
  // 모든 상태를 컴포넌트 안에서 정의
  const [currentMode, setCurrentMode] = useState("list");
  const [posts, setPosts] = useState([]);
  const [pageInfo, setPageInfo] = useState({ currentPage: 0, totalPages: 0 });
  const [keyword, setKeyword] = useState("");
  const [filters, setFilters] = useState({
    city: "",
    district: "",
    category: "",
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const navigate = useNavigate();
  const authContext = useContext(AuthContext);
  const user = authContext?.user || null;
  const isAdmin = user?.memberRole?.toUpperCase() === "ADMIN";

  const showAddForm = () => {
    setCurrentMode("add");
  };

  const showList = () => {
    setCurrentMode("list");
  };

  const regions = {
    "": [],
    서울: ["강남구", "서초구", "송파구", "강동구", "마포구", "종로구"],
    경기: ["수원시", "성남시", "용인시", "안양시", "부천시", "고양시"],
    부산: ["해운대구", "부산진구", "동래구", "서구", "남구", "중구"],
  };

  const loadPosts = useCallback(
    async (page) => {
      setLoading(true);
      setError(null);
      try {
        const data = await fetchPosts(page, 10, keyword, filters);
        setPosts(data.content || []);
        setPageInfo({
          currentPage: data.number || 0,
          totalPages: data.totalPages || 0,
        });
      } catch (error) {
        console.error("게시글 불러오기 실패", error);
        setError("게시글을 불러오는데 실패했습니다.");
        setPosts([]);
      } finally {
        setLoading(false);
      }
    },
    [keyword, filters]
  );

  const handleSearch = () => {
    loadPosts(0);
  };

  const handleKeyPress = (e) => {
    if (e.key === "Enter") {
      handleSearch();
    }
  };

  const handleFilterChange = (filterType, value) => {
    const newFilters = { ...filters, [filterType]: value };
    if (filterType === "city") {
      newFilters.district = "";
    }
    setFilters(newFilters);
  };

  const resetFilters = () => {
    setFilters({
      city: "",
      district: "",
      category: "",
    });
    setKeyword("");
    loadPosts(0);
  };

  useEffect(() => {
    if (currentMode === "list") {
      loadPosts(0);
    }
  }, [loadPosts, currentMode]);

  if (currentMode === "add") {
    return (
      <div className="notice-container">
        <div className="mode-header">
          <h2>글쓰기</h2>
          <button onClick={showList} className="cancel-button">
            취소
          </button>
        </div>
        {/* NeighborhoodAdd 컴포넌트 자리 */}
        <div
          style={{
            textAlign: "center",
            padding: "50px",
            backgroundColor: "#f8f9fa",
            borderRadius: "4px",
            border: "2px dashed #007bff",
          }}
        >
          <p> 글쓰기 폼의 대략적인 위치</p>
          <p style={{ color: "#6c757d", fontSize: "14px" }}>
            NeighborhoodAdd 컴포넌트를 import하고 활성화하거나 상태로 관리하는
            편이 url을 숨기는데 유리하지 않을까?
          </p>
        </div>
      </div>
    );
  }

  // 리스트 화면
  return (
    <div className="notice-container">
      <h2> 우리동네 게시판</h2>
      <button onClick={resetFilters} disabled={loading}>
        필터 초기화
      </button>
      <div className="neighborhood-filter">
        <select
          value={filters.city}
          onChange={(e) => handleFilterChange("city", e.target.value)}
          disabled={loading}
        >
          <option value="">시/도 선택</option>
          <option value="서울">서울</option>
          <option value="경기">경기</option>
          <option value="부산">부산</option>
        </select>

        <select
          value={filters.district}
          onChange={(e) => handleFilterChange("district", e.target.value)}
          disabled={loading || !filters.city}
        >
          <option value="">구/군 선택</option>
          {regions[filters.city]?.map((district) => (
            <option key={district} value={district}>
              {district}
            </option>
          ))}
        </select>

        <select
          value={filters.category}
          onChange={(e) => handleFilterChange("category", e.target.value)}
          disabled={loading}
        >
          <option value="">주제 분류</option>
          <option value="inform">정보</option>
          <option value="question">질문</option>
          <option value="trade">거래</option>
          <option value="review">리뷰</option>
          <option value="etc">기타</option>
        </select>
      </div>

      {error && (
        <div
          className="error-message"
          style={{ color: "red", textAlign: "center", margin: "10px 0" }}
        >
          {error}
        </div>
      )}

      {loading ? (
        <div
          className="loading"
          style={{ textAlign: "center", padding: "20px" }}
        >
          로딩중...
        </div>
      ) : (
        <>
          <table className="notice-table">
            <thead>
              <tr>
                <th>번호</th>
                <th>제목</th>
                <th>작성자</th>
                <th>작성일</th>
                <th>조회수</th>
                {isAdmin && <th>관리</th>}
              </tr>
            </thead>
            <tbody>
              {posts.length === 0 ? (
                <tr>
                  <td
                    colSpan={isAdmin ? 6 : 5}
                    style={{ textAlign: "center", padding: "20px" }}
                  >
                    게시글이 없습니다.
                  </td>
                </tr>
              ) : (
                posts.map((post, index) => (
                  <tr key={post.id}>
                    <td>{pageInfo.currentPage * 10 + index + 1}</td>
                    <td
                      className="notice-title"
                      onClick={() =>
                        navigate(`/neighborhood/detail/${post.id}`)
                      }
                      style={{ cursor: "pointer" }}
                    >
                      {post.category && (
                        <span className="category-tag">[{post.category}]</span>
                      )}
                      {post.title}
                      {post.commentCount > 0 && (
                        <span className="comment-count">
                          {" "}
                          ({post.commentCount})
                        </span>
                      )}
                    </td>
                    <td>{post.author}</td>
                    <td>{new Date(post.createdAt).toLocaleDateString()}</td>
                    <td>{post.viewCount || 0}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>

          <div className="neighborhood-bottom">
            <button
              className="write-button"
              onClick={showAddForm}
              disabled={loading}
            >
              글쓰기
            </button>

            {pageInfo.totalPages > 0 && (
              <Pagination
                currentPage={pageInfo.currentPage}
                totalPages={pageInfo.totalPages}
                onPageChange={loadPosts}
              />
            )}
          </div>
        </>
      )}
    </div>
  );
};

export default Neighborhood;
