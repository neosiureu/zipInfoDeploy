// AnnounceDetail.jsx
import React, { useEffect, useState, useContext } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { fetchPostById, deletePost } from "./AnnounceApi"; // 파일명 및 경로 변경
import { AuthContext } from "../admin/AuthContext";

import "../../css/announce/AnnounceDetail.css"; // CSS 경로 및 이름 변경

const AnnounceDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [post, setPost] = useState(null);
  const { user } = useContext(AuthContext);

  // 관리자 권한 체크 (예시, 필요에 따라 수정)
  const isAdmin = user?.authority === 0;

  useEffect(() => {
    const loadPost = async () => {
      try {
        const data = await fetchPostById(id);
        setPost(data);
      } catch (error) {
        console.error("공지사항 상세 조회 실패", error);
      }
    };
    loadPost();
  }, [id]);

  const handleDelete = async () => {
    if (window.confirm("정말 삭제하시겠습니까?")) {
      try {
        await deletePost(id);
        alert("삭제가 완료되었습니다.");
        navigate("/announce"); // 경로 변경
      } catch (error) {
        console.error("삭제 실패", error);
        alert("삭제 중 오류가 발생했습니다.");
      }
    }
  };

  if (!post) return <div>로딩 중...</div>;

  return (
    <div className="announce-detail-container">
      <h2 className="announce-title">{post.title}</h2>
      <div className="announce-meta">
        <span>작성자: {post.author}</span>
        <span>작성일: {new Date(post.createdAt).toLocaleDateString()}</span>
      </div>
      <div className="announce-content">{post.content}</div>

      {isAdmin && (
        <div className="announce-buttons">
          <button
            className="btn-edit"
            onClick={() => navigate(`/announce/edit/${id}`)} // 경로 변경
          >
            수정
          </button>
          <button className="btn-delete" onClick={handleDelete}>
            삭제
          </button>
        </div>
      )}

      <button className="back-button" onClick={() => navigate("/announce")}>
        목록으로
      </button>
    </div>
  );
};

export default AnnounceDetail;
