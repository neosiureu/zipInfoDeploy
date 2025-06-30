import React, { useState, useEffect } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import "../../css/neighborhood/NeighborhoodBoardDetail.css";
import NeighborhoodCommentSection from "./NeighborhoodCommentSection";
import { axiosAPI } from "../../api/axiosAPI";
import { CITY, TOWN } from "../common/Gonggong";

const NeighborhoodBoardDetail = () => {
  const { boardNo } = useParams();
  const [searchParams] = useSearchParams();
  const cp = Number(searchParams.get("cp") ?? 1);
  const navigate = useNavigate();
  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);

  // 이 글에 들어온 프론트 경로: navigate(`/neighborhoodBoard/detail/${item.boardNo}`);
  // 이 글에서 서버로 보낼 url 주소: /board/detail/boardNo

  // 목록보기, 수정, 삭제버튼을 각각 눌렀을 때 행동으로 아직은 구현하지 않음

  const handleBoardUpdateClick = () => {
    navigate(`/neighborhoodBoard/edit/${boardNo}${cp ? `?cp=${cp}` : ""}`, {
      state: {
        cityNo: post.cityNo,
        townNo: post.townNo,
        boardSubject: post.boardSubject,
      },
    });
  };

  const handleDelete = useNavigate(() => {
    navigate(`/neighborhoodBoard?cp=${cp}`);
  }, []);

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
        .then(({ data }) => setPost(data))
        .catch(() => console.log("불러오기 실패"))
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
  } = post;

  return (
    <div className="nb-detail-container">
      <div className="nb-detail-wrapper">
        <div className="nb-detail-header">
          <div className="nb-detail-sigungu">
            <h5 className="nb-detail-sigungu">
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
          <button
            className="nb-detail-btn nb-detail-btn-edit"
            onClick={handleBoardUpdateClick}
          >
            수정
          </button>
          <button className="nb-detail-btn nb-detail-btn-delete">삭제</button>
          <button
            className="nb-detail-btn nb-detail-btn-list"
            onClick={() => navigate(`/neighborhoodBoard?cp=${cp}`)}
          >
            목록보기
          </button>
        </div>
        <br></br>
        <NeighborhoodCommentSection boardNo={boardNo} />
      </div>
    </div>
  );
};

export default NeighborhoodBoardDetail;
