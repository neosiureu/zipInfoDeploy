import React, { useEffect, useState } from 'react';
import "../../css/myPage/myPost.css";
import "../../css/myPage/menu.css";
import Menu from "./Menu";
import { useLocation,useNavigate } from 'react-router-dom';
import { axiosAPI } from '../../api/axiosAPI';

const MyPost = () => {
  const nav = useNavigate();

  const [posts, setPosts] = useState([]);

  const [loading, setLoading] = useState(true);

  const fetchProperties = async () => {
  try {
    const response = await axiosAPI.get('/myPage/getMyPost');
    setPosts(response.data);
    console.log(response.data);
  } catch (err) {
    console.error("매물 불러오기 실패:", err);
  }finally {
    setLoading(false);
  }
  };

  useEffect(() => {
    fetchProperties();
  }, []);

  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const initialPage = parseInt(queryParams.get("cp")) || 1;
  
  const [currentPage, setCurrentPage] = useState(initialPage);
  const itemsPerPage = 10;

  const indexOfLastItem = currentPage * itemsPerPage;
  const indexOfFirstItem = indexOfLastItem - itemsPerPage;
  const currentPosts = posts.slice(indexOfFirstItem, indexOfLastItem);
  const totalPages = Math.ceil(posts.length / itemsPerPage);

  const pageGroupSize = 10;
  const currentGroup = Math.floor((currentPage - 1) / pageGroupSize);
  const startPage = currentGroup * pageGroupSize + 1;
  const endPage = Math.min(startPage + pageGroupSize - 1, totalPages);

  const handlePageChange = (page) => {
  setCurrentPage(page);
  nav(`/myPage/myPost?cp=${page}`); // URL 업데이트
  };

  const handleBoardClick = (item) => {
    nav(`/neighborhoodBoard/detail/${item.boardNo}?cp=${currentPage}`);
  };

  return (
    <div className="my-page">
        <div className="my-page-container">
      
          <Menu/>

          <div className="my-page-board-container">
      {/* 게시판 헤더 */}
        <div className="nb-board-table">
          <div className="nb-header">
            <div className="nb-header-cell nb-header-number">번호</div>
            <div className="nb-header-cell nb-header-subject">분류</div>
            <div className="nb-header-cell nb-header-title">제목</div>
            <div className="nb-header-cell nb-header-area">지역</div>

            <div className="nb-header-cell nb-header-author">작성자</div>
            <div className="nb-header-cell nb-header-date">날짜</div>
            <div className="nb-header-cell nb-header-views">조회</div>
          </div>

          {currentPosts.length!==0? currentPosts.map((item, index) => (
            <div key={index} className="nb-row">
              <div className="nb-cell nb-cell-number">{item.boardNo}</div>
              <div className="nb-cell nb-cell-subject">
                {item.boardSubject === "Q"
                  ? "질문답변"
                  : item.boardSubject === "R"
                  ? "리뷰"
                  : "자유"}
              </div>
              <div
                className="nb-cell nb-cell-title"
                onClick={() => handleBoardClick(item)} // 클릭 이벤트로 상세화면으로 이동하게
                style={{ cursor: "pointer" }}
              >
                {item.boardTitle}
              </div>
              <div className="nb-cell nb-cell-area">
                {item.cityName} {">"} {item.townName}
              </div>
              <div className="nb-cell nb-cell-author">
                {item.memberNickName}
              </div>
              <div className="nb-cell nb-cell-date">{item.boardWriteDate}</div>
              <div className="nb-cell nb-cell-views">{item.readCount}</div>
            </div>
          )): <div className='no-my-post'>작성한 게시글이 없습니다.</div>}
          </div>
          <div className="my-stock-pagination">

            <button
              className='my-stock-page-prev'
              onClick={() => handlePageChange(1)}
              disabled={currentPage === 1}
            >
              ‹‹
            </button>

            {/* 이전 그룹으로 이동 또는 1페이지로 */}
            <button
              className='my-stock-page-prev'
              onClick={() => {
                if (startPage === 1) {
                  handlePageChange(1);
                } else {
                  handlePageChange(startPage - 1);
                }
              }}
              disabled={currentPage === 1}
            >
              ‹
            </button>
            
            {/* 현재 그룹 페이지들 */}
            {Array.from({ length: endPage - startPage + 1 }, (_, index) => {
              const page = startPage + index;
              return (
                <button
                  key={page}
                  onClick={() => handlePageChange(page)}
                  className={currentPage === page ? "active-page" : ""}
                >
                  {page}
                </button>
              );
            })}

            {/* 다음 그룹으로 이동 또는 마지막 페이지로 */}
            <button
              className='my-stock-page-next'
              onClick={() => {
                if (endPage >= totalPages) {
                  handlePageChange(totalPages);
                } else {
                  handlePageChange(endPage + 1);
                }
              }}
              disabled={currentPage === totalPages}
            >
              ›
            </button>
            
            {/* 맨 마지막 페이지로 */}
            <button
              className='my-stock-page-next'
              onClick={() => handlePageChange(totalPages)}
              disabled={currentPage === totalPages}
            >
              ››
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MyPost;