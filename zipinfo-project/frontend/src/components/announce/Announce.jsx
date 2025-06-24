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

  // 권한 체크 함수 (memberAuth, role, roles 필드가 다양하게 올 수 있으니 안전하게 처리)
  const checkAdmin = (user) => {
    if (!user) return false;
    const memberAuth = user.memberAuth ?? user.member_auth ?? null;
    const role = user.role ?? null;
    const roles = user.roles ?? [];
    return (
      memberAuth === 0 ||
      memberAuth === "0" ||
      role === "ADMIN" ||
      roles.includes("ROLE_ADMIN")
    );
  };

  const isAdmin = checkAdmin(user);

  // 디버깅용: user와 isAdmin 값 출력
  useEffect(() => {
    console.log("현재 user:", user);
    console.log("isAdmin:", isAdmin);
  }, [user, isAdmin]);

  // 공지사항 목록 로딩
  const loadPosts = async (page = 0) => {
    try {
      const data = await fetchPosts(page, 10, keyword);
      console.log("fetchPosts 결과:", data);
      setPosts(data || []);
      setPageInfo({ currentPage: page, totalPages: 1 }); // TODO: API가 totalPages 응답 시 반영
    } catch (error) {
      console.error("공지사항 불러오기 실패", error);
      setPosts([]);
    }
  };

  const handleSearch = () => {
    loadPosts(0);
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
            {/* 관리(삭제) 컬럼 제거 */}
          </tr>
        </thead>
        <tbody>
          {Array.isArray(posts) && posts.length > 0 ? (
            posts.map((post) => {
              const id = post.id ?? post.boardNo;
              const title = post.title ?? "제목 없음";
              const author = post.author ?? post.writer ?? "작성자 없음";
              const date =
                post.createdAt || post.createDate
                  ? new Date(
                      post.createdAt || post.createDate
                    ).toLocaleDateString()
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
                  {/* 삭제 버튼 제거 */}
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
