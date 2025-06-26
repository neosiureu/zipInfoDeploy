import React, { useState } from 'react';
import "../../css/myPage/myPost.css";
import "../../css/myPage/menu.css";
import Menu from "./Menu";
import { useNavigate } from 'react-router-dom';
import { axiosAPI } from '../../api/axiosApi';

const MyPost = () => {
  const nav = useNavigate();

  const [posts, setPosts] = useState([]);

  return (
    <div className="my-page">
        <div className="my-page-container">
      
          <Menu/>

          <div className="my-page-board-container">
      {/* 게시판 헤더 */}
      <div className="my-page-board-header">
        <div className="my-page-header-row">
          <div className="my-page-col-number">번호</div>
          <div className="my-page-col-title">제목</div>
          <div className="my-page-col-author">작성자</div>
          <div className="my-page-col-date">날짜</div>
          <div className="my-page-col-views">조회</div>
        </div>
      </div>

      {/* 게시글 목록 */}
      <div className="my-page-board-content">
        {posts.length!==0? posts.map((post) => (
          <div 
            key={post.id}
            className="my-page-post-row"
            onClick={() => handlePostClick(post.id)}
          >
            <div className="my-page-col-number">
              {post.id}
            </div>
            <div className="my-page-col-title">
              <div className="my-page-title-text">
                {post.title}
              </div>
            </div>
            <div className="my-page-col-author">
              {post.author}
            </div>
            <div className="my-page-col-date">
              {post.date}
            </div>
            <div className="my-page-col-views">
              {post.views}
            </div>
          </div>
        )) : <div>작성한 게시글이 없습니다.</div>}
      </div>
      </div>
      </div>
    </div>
  );
};

export default MyPost;