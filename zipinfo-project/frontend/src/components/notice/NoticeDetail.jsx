// NoticeDetail.jsx
import React, { useEffect, useState, useContext } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { fetchPostById, deletePost } from "./noticeApi";
import { AuthContext } from "../admin/AuthContext";

import "../../css/notice/NoticeDetail.css";

const NoticeDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [post, setPost] = useState(null);
  const { user } = useContext(AuthContext);

  // 권한 숫자 기반 예시 (관리자 권한이 0일 경우)
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
        navigate("/notice");
      } catch (error) {
        console.error("삭제 실패", error);
        alert("삭제 중 오류가 발생했습니다.");
      }
    }
  };

  if (!post) return <div>로딩 중...</div>;

  return (
    <div className="notice-detail-container">
      <h2 className="notice-title">{post.title}</h2>
      <div className="notice-meta">
        <span>작성자: {post.author}</span>
        <span>작성일: {new Date(post.createdAt).toLocaleDateString()}</span>
      </div>
      <div className="notice-content">{post.content}</div>

      {isAdmin && (
        <div className="notice-buttons">
          <button
            className="btn-edit"
            onClick={() => navigate(`/notice/edit/${id}`)}
          >
            수정
          </button>
          <button className="btn-delete" onClick={handleDelete}>
            삭제
          </button>
        </div>
      )}

      <button className="back-button" onClick={() => navigate("/notice")}>
        목록으로
      </button>
    </div>
  );
};

export default NoticeDetail;
