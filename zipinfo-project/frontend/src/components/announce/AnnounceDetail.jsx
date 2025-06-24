import React, { useEffect, useState, useContext } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { fetchPostById, deletePost } from "./AnnounceApi";
import { AuthContext } from "../admin/AuthContext";

import "../../css/announce/AnnounceDetail.css";

const AnnounceDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [post, setPost] = useState(null);
  const { user } = useContext(AuthContext);

  const isAdmin =

    user?.authority === 0 || user?.memberAuth === 0 || user?.role === "ADMIN";
    user?.role === "ADMIN" ||
    user?.roles?.includes("ROLE_ADMIN") ||
    user?.memberAuth === 0 ||
    user?.member_auth === 0;

  useEffect(() => {
    const loadPost = async () => {
      try {
        const data = await fetchPostById(id); // 조회수 증가 포함
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
        navigate("/announce");
      } catch (error) {
        console.error("삭제 실패", error);
        alert("삭제 중 오류가 발생했습니다.");
      }
    }
  };

  if (!post) return <div className="an-detail-loading">로딩 중...</div>;

  return (

    <div className="an-detail-container">
      <h2 className="an-detail-title">{post.title}</h2>
      <div className="an-detail-meta">
        <span>작성자: {post.author}</span>
        <span>작성일: {new Date(post.createdAt).toLocaleDateString()}</span>
        <span>조회수: {post.viewCount ?? 0}</span>
      </div>

      <div className="an-detail-content">{post.content}</div>

      {isAdmin && (
        <div className="an-detail-buttons">
          <button
            className="an-detail-btn-edit"
            onClick={() => navigate(`/announce/edit/${id}`)}
          >
            수정
          </button>
          <button className="an-detail-btn-delete" onClick={handleDelete}>
            삭제
          </button>
        </div>
      )}

      <button
        className="an-detail-btn-back"
        onClick={() => navigate("/announce")}
      >
        목록으로
      </button>
    </div>
  );
};

export default AnnounceDetail;
