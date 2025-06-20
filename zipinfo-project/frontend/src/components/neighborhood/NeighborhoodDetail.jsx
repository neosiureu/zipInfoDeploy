import React, { useState, useEffect, useContext } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import "../../css/neighborhood/Neighborhood.css";

const fetchPostDetail = async (id) => {
  try {
    const response = await fetch(`/api/neighborhood/posts/${id}`);
    if (!response.ok) {
      throw new Error("게시글을 찾을 수 없습니다.");
    }
    return await response.json();
  } catch (error) {
    console.error("API Error:", error);
    throw error;
  }
};

const deletePost = async (id) => {
  try {
    const response = await fetch(`/neighborhood/posts/${id}`, {
      method: "DELETE",
    });
    return response.ok;
  } catch (error) {
    console.error("Delete Error:", error);
    return false;
  }
};

const NeighborhoodDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const authContext = useContext(AuthContext);
  const user = authContext?.user || null;
  const isAdmin = user?.memberRole?.toUpperCase() === "ADMIN";
  const isAuthor = user && post && user.id === post.authorId;

  useEffect(() => {
    const loadPost = async () => {
      try {
        setLoading(true);
        const data = await fetchPostDetail(id);
        setPost(data);
      } catch (error) {
        console.error("게시글 불러오기 실패", error);
        setError("게시글을 불러오는데 실패했습니다.");
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      loadPost();
    }
  }, [id]);

  const handleDelete = async () => {
    if (confirm("정말 삭제하시겠습니까?")) {
      try {
        const success = await deletePost(id);
        if (success) {
          alert("게시글이 삭제되었습니다.");
          navigate("/neighborhood");
        } else {
          alert("삭제에 실패했습니다.");
        }
      } catch (error) {
        console.error("삭제 실패", error);
        alert("삭제 중 오류가 발생했습니다.");
      }
    }
  };

  const handleBack = () => {
    if (window.history.length > 1) {
      navigate(-1);
    } else {
      navigate("/neighborhood");
    }
  };

  if (loading) {
    return (
      <div className="notice-container">
        <div style={{ textAlign: "center", padding: "50px" }}>로딩 중...</div>
      </div>
    );
  }

  if (error || !post) {
    return (
      <div className="notice-container">
        <div style={{ textAlign: "center", padding: "50px" }}>
          <p>{error || "게시글을 찾을 수 없습니다."}</p>
          <button
            onClick={() => navigate("/neighborhood")}
            style={{
              backgroundColor: "#007bff",
              color: "white",
              border: "none",
              padding: "8px 16px",
              borderRadius: "4px",
              cursor: "pointer",
            }}
          >
            목록으로 돌아가기
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="notice-container">
      <div className="breadcrumb" style={{ marginBottom: "20px" }}>
        <button
          onClick={handleBack}
          style={{
            background: "none",
            border: "none",
            color: "#007bff",
            cursor: "pointer",
            fontSize: "14px",
          }}
        >
          뒤로가기
        </button>
        <span style={{ margin: "0 10px", color: "#6c757d" }}>|</span>
        <button
          onClick={() => navigate("/neighborhood")}
          style={{
            background: "none",
            border: "none",
            color: "#007bff",
            cursor: "pointer",
            fontSize: "14px",
          }}
        >
          목록으로
        </button>
      </div>

      <div className="notice-detail">
        <div className="detail-header">
          <div className="detail-category">[{post.category || "기타"}]</div>
          <h2 className="detail-title">{post.title}</h2>

          <div className="detail-info">
            <div className="detail-meta">
              <span className="author">작성자: {post.author || "익명"}</span>
              <span className="date">
                작성일: {new Date(post.createdAt).toLocaleDateString()}
              </span>
              <span className="views">조회수: {post.viewCount || 0}</span>
            </div>

            {post.location && (
              <div className="detail-location">
                {post.location.city} {post.location.district}
              </div>
            )}
          </div>
        </div>

        <div className="detail-content">
          <div className="content-text">
            {post.content?.split("\n").map((line, index) => (
              <p key={index}>{line}</p>
            ))}
          </div>

          {post.images && post.images.length > 0 && (
            <div className="content-images">
              {post.images.map((image, index) => (
                <img
                  key={index}
                  src={image.url}
                  alt={`게시글 이미지 ${index + 1}`}
                  style={{ maxWidth: "100%", marginBottom: "10px" }}
                />
              ))}
            </div>
          )}
        </div>

        <div
          className="detail-actions"
          style={{ marginTop: "20px", textAlign: "center" }}
        >
          <button
            className="back-button"
            onClick={handleBack}
            style={{
              backgroundColor: "#6c757d",
              color: "white",
              border: "none",
              padding: "8px 16px",
              borderRadius: "4px",
              cursor: "pointer",
              marginRight: "8px",
            }}
          >
            뒤로가기
          </button>

          {(isAdmin || isAuthor) && (
            <button
              className="delete-button"
              onClick={handleDelete}
              style={{
                backgroundColor: "#ff4757",
                color: "white",
                border: "none",
                padding: "8px 16px",
                borderRadius: "4px",
                cursor: "pointer",
              }}
            >
              삭제
            </button>
          )}
        </div>

        {/* 댓글 섹션 */}
        <div className="comments-section" style={{ marginTop: "30px" }}>
          <h3>댓글 ({post.commentCount || 0})</h3>
          <div
            className="comments-placeholder"
            style={{
              padding: "20px",
              backgroundColor: "#f8f9fa",
              borderRadius: "4px",
              textAlign: "center",
              color: "#6c757d",
            }}
          >
            <p>대충 댓글 위치</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default NeighborhoodDetail;
