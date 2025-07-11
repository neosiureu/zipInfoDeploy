import React from "react";
import { X } from "lucide-react";

const DeletedBoardModal = ({ board, isOpen, onClose }) => {
  if (!isOpen || !board) return null;

  const subjectMap = {
    Q: "질문답변",
    R: "리뷰",
    E: "기타",
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>삭제된 게시글 상세</h2>
          <button className="modal-close" onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <div className="modal-body">
          <div className="board-info">
            <div className="info-row">
              <span className="info-label">제목:</span>
              <span className="info-value">{board.boardTitle}</span>
            </div>

            <div className="info-row">
              <span className="info-label">작성자:</span>
              <span className="info-value">{board.memberNickName}</span>
            </div>

            <div className="info-row">
              <span className="info-label">작성일:</span>
              <span className="info-value">{board.boardWriteDate}</span>
            </div>

            <div className="info-row">
              <span className="info-label">지역:</span>
              <span className="info-value">
                {board.cityName} &gt; {board.townName}
              </span>
            </div>

            <div className="info-row">
              <span className="info-label">조회수:</span>
              <span className="info-value">{board.readCount}</span>
            </div>

            <div className="info-row">
              <span className="info-label">분류:</span>
              <span className="info-value">
                {subjectMap[board.boardSubject] || "기타"}
              </span>
            </div>
          </div>

          <div className="board-content">
            <h3>게시글 내용</h3>
            <div className="content-text">{board.boardContent}</div>
          </div>
        </div>

        <div className="modal-footer">
          <button className="modal-button" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>
  );
};

export default DeletedBoardModal;
