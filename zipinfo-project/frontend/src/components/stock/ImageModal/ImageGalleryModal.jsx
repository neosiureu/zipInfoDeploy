import React, { useState, useEffect } from "react";
import "../../../css/stock/ImageModal/ImageGalleryModal.css"; // 스타일은 따로 분리

const ImageGalleryModal = ({ item }) => {
  const { imgUrl } = item;
  const [isOpen, setIsOpen] = useState(false); // 모달 열림 여부
  const [currentIndex, setCurrentIndex] = useState(0); // 현재 보여줄 이미지 인덱스

  // ESC 키로 모달 닫기 처리
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === "Escape") setIsOpen(false);
      if (e.key === "ArrowLeft") showPrev();
      if (e.key === "ArrowRight") showNext();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  });

  const openModal = (index) => {
    setCurrentIndex(index);
    setIsOpen(true);
  };

  const showPrev = () => {
    setCurrentIndex((prev) => (prev - 1 + imgUrl.length) % imgUrl.length);
  };

  const showNext = () => {
    setCurrentIndex((prev) => (prev + 1) % imgUrl.length);
  };

  if (!imgUrl || imgUrl.length === 0) return null;

  return (
    <div>
      {/* 썸네일 목록 */}
      <div className="thumbnail-list">
        {imgUrl.map((url, index) => (
          <img
            key={index}
            src={url}
            alt={`썸네일-${index}`}
            className="thumbnail"
            onClick={() => openModal(index)}
          />
        ))}
      </div>

      {/* 모달 */}
      {/**modal-overlay: 모달의 전체 배경 (검정 반투명 영역).
        👉 이걸 클릭하면 모달이 닫히도록 onClick={() => setIsOpen(false)}이 바인딩되어 있음.

        modal-content: 가운데 이미지와 버튼이 들어 있는 실제 콘텐츠 영역. 
        
        ***************************************************************************************
        onClick={(e) => e.stopPropagation()}
        e.stopPropagation()은 이벤트가 상위 요소로 전달되는 것을 막음

        즉, modal-content 안에서 발생한 클릭 이벤트는 더 이상 부모인 modal-overlay로 전달되지 않음

        결과적으로 콘텐츠 내부를 클릭해도 모달이 닫히지 않음
        */}
      {isOpen && (
        <div className="modal-overlay" onClick={() => setIsOpen(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <img
              src={imgUrl[currentIndex]}
              alt={`이미지-${currentIndex}`}
              className="modal-image"
            />
            <button className="close-btn" onClick={() => setIsOpen(false)}>
              ✕
            </button>
            <button className="nav-btn prev" onClick={showPrev}>
              ‹
            </button>
            <button className="nav-btn next" onClick={showNext}>
              ›
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default ImageGalleryModal;
