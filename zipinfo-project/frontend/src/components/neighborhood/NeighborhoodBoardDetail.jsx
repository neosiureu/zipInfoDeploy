import React, { useState, useEffect, useContext } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import "../../css/neighborhood/NeighborhoodBoardDetail.css";
import NeighborhoodCommentSection from "./NeighborhoodCommentSection";
import { axiosAPI } from "../../api/axiosApi";
import { CITY, TOWN } from "../common/Gonggong";
import { MemberContext } from "../member/MemberContext";
import { Heart } from "lucide-react";
import { toast } from "react-toastify";

const NeighborhoodBoardDetail = () => {
  const [board, setBoard] = useState([{}]);
  const { member } = useContext(MemberContext);
  const isAdmin = member?.memberAuth === 0;

  const { boardNo } = useParams();
  const [searchParams] = useSearchParams();
  const cp = Number(searchParams.get("cp") ?? 1);
  const navigate = useNavigate();
  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);
  const loginMemberNo = member?.memberNo;
  const [like, setLike] = useState(new Set());
  const [likeCount, setLikeCount] = useState(0);
  // 이 글에 들어온 프론트 경로: navigate(`/neighborhoodBoard/detail/${item.boardNo}`);
  // 이 글에서 서버로 보낼 url 주소: /board/detail/boardNo

  const handleBoardLike = async (boardNo) => {
    if (member == null) {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">로그인 후 이용해주세요.</div>
        </div>
      );
      return;
    }
    const isCurrentlyLiked = like.has(boardNo);

    setLike((prev) => {
      const updated = new Set(prev);
      if (updated.has(boardNo)) {
        updated.delete(boardNo); // 찜 해제
      } else {
        updated.add(boardNo); // 찜 추가
      }
      return updated;
    });
    try {
      const { data: totalLike } = await axiosAPI.post("/board/like", {
        boardNo: boardNo,
        liked: isCurrentlyLiked,
      });
    } catch (err) {
      console.error("찜 상태 변경 실패:", err);
    }
  };

  const handleBoardUpdateClick = () => {
    navigate(`/neighborhoodBoard/edit/${boardNo}${cp ? `?cp=${cp}` : ""}`, {
      state: {
        cityNo: post.cityNo,
        townNo: post.townNo,
        boardSubject: post.boardSubject,
      },
    });
  };

  const handleBoardDeleteClick = async (boardNo) => {
    if (confirm("글을 삭제하시겠습니까?")) {
      const { data: result } = await axiosAPI.delete(`/editBoard/${boardNo}`);
      if (result > 0) {
        toast.success(
          <div>
            <div className="toast-success-title">삭제 성공 알림!</div>
            <div className="toast-success-body">게시글이 삭제되었습니다.</div>
          </div>
        );
      }
      navigate(`/neighborhoodBoard?cp=${cp}`);
    } else {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">게시글 삭제 실패!</div>
        </div>
      );
      return; // 실패시 페이지 이동 막기
    }
  };

  const handleAdminDelete = async (boardNo) => {
    if (!confirm("관리자 권한으로 해당 게시글을 삭제하시겠습니까?")) return;
    const { data: result } = await axiosAPI.delete(`/admin/board/${boardNo}`);
    if (result > 0) {
      toast.success(
        <div>
          <div className="toast-success-title">삭제 성공 알림!</div>
          <div className="toast-success-body">
            관리자권한으로 게시글이 삭제되었습니다.
          </div>
        </div>
      );
      navigate(-1);
    }
  };

  function getCityName(cityCode) {
    const city = CITY.find((c) => String(c.code) === String(cityCode));
    return city ? city.name : cityCode;
  }

  function getTownName(fullcode) {
    const town = TOWN.find((t) => String(t.fullcode) === String(fullcode));
    return town ? town.name : fullcode;
  }

  const subjectMap = {
    Q: "질문",
    R: "리뷰",
    E: "기타",
  };
  useEffect(() => {
    if (!post) {
      setLoading(true);
      axiosAPI
        .get("board/neighborhoodDetail", { params: { boardNo } })
        .then(({ data }) => {
          setPost(data);
          setBoard(data);

          if (data.likeCheck === 1) {
            setLike(new Set([Number(boardNo)]));
          }

          if (data.likeCount !== undefined) {
            setLikeCount(data.likeCount);
          }
        })
        .finally(() => setLoading(false));
    }
  }, [boardNo]);

  if (loading) {
    return <div className="nb-detail-loading">로딩 중...</div>;
  }

  if (!post) {
    return <div className="nb-detail-error">게시글을 찾을 수 없습니다.</div>;
  }

  const {
    boardTitle,
    memberNickName,
    boardWriteDate,
    readCount,
    boardContent,
    cityNo,
    townNo,
    boardSubject,
    memberNo,
  } = post;

  return (
    <div className="nb-detail-container">
      <div className="nb-detail-wrapper">
        <div className="nb-detail-header">
          <div className="nb-detail-sigungu">
            <h5 className="nb-detail-sigungu-detail">
              {getCityName(cityNo)} {">"} {getTownName(townNo)} {">"}{" "}
              {subjectMap[boardSubject] || boardSubject}
            </h5>

            <h1 className="nb-detail-title">{boardTitle}</h1>
            <div className="nb-detail-meta">
              <span className="nb-detail-author">
                작성자 : {memberNickName}
              </span>
              <span className="nb-detail-separator">|</span>
              <span className="nb-detail-date">등록일 : {boardWriteDate}</span>
              <span className="nb-detail-separator">|</span>
              <span className="nb-detail-views">조회수 : {readCount}</span>
              {/* <span className="nb-detail-separator">|</span>
              <span className="nb-detail-likes">좋아요 : {likeCount}</span>{" "} */}
              {/* 추가 */}
              <span
                onClick={(e) => {
                  e.stopPropagation(); // nav 방지
                  handleBoardLike(board.boardNo);
                }}
              >
                <Heart
                  size={20}
                  stroke="pink" // 분홍 테두리
                  fill={like.has(board.boardNo) ? "red" : "transparent"}
                  className={`like-board ${
                    like.has(board.boardNo) ? "active" : ""
                  }`}
                />
              </span>
            </div>
          </div>
        </div>
        <div className="nb-detail-content">
          <div
            className="nb-detail-text"
            dangerouslySetInnerHTML={{ __html: boardContent }}
          />
        </div>
        <div className="nb-detail-buttons">
          {loginMemberNo == memberNo ? (
            <>
              <button
                className="nb-detail-btn nb-detail-btn-edit"
                onClick={handleBoardUpdateClick}
              >
                수정
              </button>
              <button
                className="nb-detail-btn nb-detail-btn-delete"
                onClick={() => handleBoardDeleteClick(boardNo)}
              >
                삭제
              </button>
            </>
          ) : null}
          {isAdmin && loginMemberNo !== memberNo && (
            <button
              className="nb-detail-btn nb-detail-btn-delete"
              onClick={() => handleAdminDelete(boardNo)}
            >
              관리자 글삭제
            </button>
          )}
          <button
            className="nb-detail-btn nb-detail-btn-list"
            onClick={() => navigate(-1)}
          >
            목록보기
          </button>
        </div>
        <NeighborhoodCommentSection boardNo={boardNo} />
      </div>
    </div>
  );
};

export default NeighborhoodBoardDetail;
