import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import "../../css/neighborhood/NeighborhoodDetail.css";

const NeighborhoodDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);

  // 샘플 데이터 (실제로는 API에서 가져와야 함)
  useEffect(() => {
    // 실제 구현시에는 fetchPostDetail(id) 같은 API 호출
    const samplePost = {
      id: id,
      title: "공지사항 게시글 제목",
      author: "관리자",
      date: "2025.05.21",
      views: 20,
      content: `코딩이란 프로그래밍 코드를 어디가에 직는 것을 말한다. 예를 들어 메모장을 켜고 명령한 글을 쓸 수도 있고 프로그램 코드를 쓸 수도 있는데,

      휴지통 위에 코딩이다. 보통은 코딩을 위한 전용 프로그램 IDE를 사용하는데, 그 이유는 단지 메모장보다 더 편리하기 때문이다.

      보통은 코딩을 할 때 컴퓨터의 이용하기에 키보드를 마구 누를지 키가 코딩을 하겠지만,

      종이나 화이트보드 위에 손으로 직접 코드를 써 가면서 코딩을 할 수도 있다.

      공학 전 분야, 특히 컴퓨터 공학 전공자들이 많이 하는 행위이다.`,
    };

    setTimeout(() => {
      setPost(samplePost);
      setLoading(false);
    }, 500);
  }, [id]);

  if (loading) {
    return <div className="nb-detail-loading">로딩 중...</div>;
  }

  if (!post) {
    return <div className="nb-detail-error">게시글을 찾을 수 없습니다.</div>;
  }

  return (
    <div className="nb-detail-container">
      <div className="nb-detail-wrapper">
        <div className="nb-detail-header">
          <h1 className="nb-detail-title">{post.title}</h1>
          <div className="nb-detail-meta">
            <span className="nb-detail-author">작성자 : {post.author}</span>
            <span className="nb-detail-separator">|</span>
            <span className="nb-detail-date">등록일 : {post.date}</span>
            <span className="nb-detail-separator">|</span>
            <span className="nb-detail-views">조회수 : {post.views}</span>
          </div>
        </div>

        <div className="nb-detail-content">
          <div className="nb-detail-image">
            <img
              src="https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=800&h=400&fit=crop"
              alt="게시글 이미지"
            />
          </div>
          <div className="nb-detail-text">
            {post.content.split("\n\n").map((paragraph, index) => (
              <p key={index}>{paragraph}</p>
            ))}
          </div>
        </div>

        <div className="nb-detail-buttons">
          <button className="nb-detail-btn nb-detail-btn-edit">수정</button>
          <button className="nb-detail-btn nb-detail-btn-delete">삭제</button>
          <button
            className="nb-detail-btn nb-detail-btn-list"
            onClick={() => navigate("/announce")}
          >
            목록보기
          </button>
        </div>
      </div>
    </div>
  );
};

export default NeighborhoodDetail;
