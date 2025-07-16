import { useRef, useState } from "react";
import Modal from "react-modal";
import { ChevronLeft, ChevronRight, X } from "lucide-react";
import "../../css/stock/StockImgModal.css";

function StockImgModal({ item }) {
  const [open, setOpen] = useState(false);
  const [idx, setIdx] = useState(0);

  const railRef = useRef(null);

  const length = item.imgUrls.length;

  const next = () => {
    if (idx === length - 1) {
      return;
    }
    setIdx((idx + 1) % item.imgUrls.length);
  };
  const prev = () => {
    if (idx === 2) {
      return;
    }
    setIdx((idx - 1 + item.imgUrls.length) % item.imgUrls.length);
  };

  const go = (i) => {
    const realIdx = i + 2; // 본 슬라이드 인덱스
    setIdx(realIdx);

    const rail = railRef.current;
    const thumb = rail.children[i];
    if (!rail || !thumb) return;

    /* 썸네일을 가운데 두기 위해 필요한 scrollLeft 값 계산 */
    const target =
      thumb.offsetLeft + thumb.offsetWidth / 2 - rail.clientWidth / 2;

    /* 0 ~ (전체너비−보이는너비) 로 Clamp → 항상 영역 안에만 스크롤 */
    const max = rail.scrollWidth - rail.clientWidth;
    const left = Math.max(0, Math.min(target, max));

    rail.scrollTo({ left, behavior: "smooth" });
  };

  /* ───────── 스타일 객체 (원하면 className으로 대체 가능) ───────── */
  const modalStyles = {
    overlay: { backgroundColor: "rgba(0,0,0,.70)", zIndex: 1050 },
    content: {
      inset: "50% auto auto 50%",
      transform: "translate(-50%,-45%)",
      border: "none",
      background: "transparent",
      padding: 0,
      overflow: "visible",
    },
  };

  return (
    <>
      {/* ── 썸네일 목록 ─────────────────────────────────────────── */}
      <div className="stock-detail-panel">
        <div className="stock-detail-images">
          {item?.imgUrls && (
            <>
              <img
                src={`${import.meta.env.VITE_API_BASE_URL}${item.imgUrls[0]}`} //?v=${Date.now()}//?v=${Date.now()}	--> 현재 시간을 이용해 URL을 고유하게 만들어 브라우저 캐시를 우회
                alt="상세1"
                className="stock-detail-mainimg"
                onClick={() => {
                  setIdx(2);
                  setOpen(true);
                }}
              />
              <img
                src={
                  item?.imgUrls
                    ? `${import.meta.env.VITE_API_BASE_URL}${item.imgUrls[2]}`
                    : ""
                }
                className="stock-detail-mainimg"
                onClick={() => {
                  setIdx(2);
                  setOpen(true);
                }}
              />
            </>
          )}
        </div>
      </div>

      {/* ── 모달 ──────────────────────────────────────────────── */}
      <Modal
        isOpen={open}
        onRequestClose={() => setOpen(false)}
        style={modalStyles}
        contentLabel="매물 이미지"
        closeTimeoutMS={200}
      >
        <div className="modal-img-span">
          <span>{idx - 1}/</span>
          <span>{length - 2}</span>
        </div>
        <div>
          <button className="modal-nav left" onClick={prev}>
            <ChevronLeft size={40} />
          </button>
          <div className="slide-box">
            {/* 트랙을 왼쪽으로 이동 → translateX 에 transition 적용한다 */}
            <div
              className="slide-track"
              style={{ transform: `translateX(-${idx * 100}%)` }}
            >
              {item.imgUrls.map((u, i) => (
                <img
                  key={i}
                  className="slide-img"
                  src={`${import.meta.env.VITE_API_BASE_URL}${u}`}
                  alt={`매물 이미지 ${i + 1}`}
                />
              ))}
            </div>
          </div>

          <button className="modal-nav right" onClick={next}>
            <ChevronRight size={40} />
          </button>
        </div>
        <div className="modal-img-list">
          <div ref={railRef} className="modal-img-wrapper">
            {item.imgUrls.slice(2).map((u, i) => (
              <div className="modal-img">
                <img
                  key={i}
                  className="slide-img-mini"
                  src={`${import.meta.env.VITE_API_BASE_URL}${u}`}
                  alt={`매물 이미지 ${i + 1}`}
                  onClick={() => go(i)}
                />
              </div>
            ))}
          </div>
        </div>
      </Modal>
    </>
  );
}

export default StockImgModal;
