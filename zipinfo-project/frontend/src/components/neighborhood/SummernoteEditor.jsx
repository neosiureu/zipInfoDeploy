import React, { useEffect, useRef, useState } from "react";
import { toast } from "react-toastify";
import $ from 'jquery';
import 'summernote/dist/summernote-bs4.min.js';

// CSS 임포트 - 정확한 경로
import 'bootstrap/dist/css/bootstrap.min.css';
import 'summernote/dist/summernote-bs4.min.css';

// jQuery를 window에 할당
window.$ = window.jQuery = $;

export default function SummernoteEditor({ value, onChange, disabled }) {
  const editorRef = useRef(null);
  const [isReady, setIsReady] = useState(false);
  const isInitialized = useRef(false);
  const lastValidContent = useRef(value || "");

  // 텍스트만 추출하는 함수
  const extractTextContent = (htmlContent) => {
    if (!htmlContent) return "";
    const tempDiv = document.createElement("div");
    tempDiv.innerHTML = htmlContent;
    const textOnly = tempDiv.textContent || tempDiv.innerText || "";
    tempDiv.remove();
    return textOnly.trim();
  };

  // 내용이 비어있는지 확인하는 함수
  const isContentEmpty = (htmlContent) => {
    if (!htmlContent) return true;
    const textContent = extractTextContent(htmlContent);
    const hasImage = htmlContent.includes("<img");
    const hasListContent = htmlContent.includes("<ul") || 
                          htmlContent.includes("<ol") || 
                          htmlContent.includes("<li");
    
    return textContent.length === 0 && !hasImage && !hasListContent;
  };

  // 변경 핸들러 - 간소화됨
  const handleChange = (content) => {
    // 글자수 체크 (2000자 제한)
    const textContent = extractTextContent(content);
    
    if (textContent.length > 2000) {
      // 2000자 초과시 이전 상태로 복원
      toast.error(
        <div>
          <div className="toast-error-title">글자수 초과</div>
          <div className="toast-error-body">
            텍스트는 최대 2000자까지 입력할 수 있습니다.
          </div>
        </div>
      );
      
      // 이전 유효한 내용으로 복원
      if (editorRef.current) {
        $(editorRef.current).summernote('code', lastValidContent.current);
      }
      return;
    }

    // 유효한 내용이면 저장하고 상위로 전달
    lastValidContent.current = content;
    onChange(content);
  };

  // Summernote 초기화 - 안전한 방식
  useEffect(() => {
    if (!editorRef.current || isInitialized.current) return;

    const $editor = $(editorRef.current);

    try {
      $editor.summernote({
        height: 700,
        minHeight: 700,
        lang: "ko-KR",
        placeholder: "내용을 입력해주세요",
        disableResizeEditor: true,
        focus: false,
        toolbar: [
          ["style", ["style"]],
          ["font", ["bold", "italic", "underline"]],
          ["color", ["color"]],
          ["para", ["ul", "ol", "paragraph"]],
          ["insert", ["picture"]],
        ],
        callbacks: {
          onChange: function (contents) {
            handleChange(contents);
          },

          onImageUpload: function (files) {
            Array.from(files).forEach((file) => {
              // 파일 크기 체크 (5MB 제한)
              if (file.size > 5 * 1024 * 1024) {
                toast.error(
                  <div>
                    <div className="toast-error-title">파일 크기 초과</div>
                    <div className="toast-error-body">
                      이미지는 5MB 이하만 업로드 가능합니다.
                    </div>
                  </div>
                );
                return;
              }

              // 서버 업로드
              const formData = new FormData();
              formData.append("image", file);

              fetch(`${import.meta.env.VITE_API_BASE_URL}/editBoard/uploadImage`, {
                method: "POST",
                body: formData,
              })
                .then((response) => response.text())
                .then((serverImageUrl) => {
                  console.log("서버 업로드 성공:", serverImageUrl);
                })
                .catch((error) => {
                  console.error("서버 업로드 실패:", error);
                });

              // 로컬에서 이미지 표시
              const reader = new FileReader();
              reader.onload = (e) => {
                $editor.summernote('insertImage', e.target.result, function ($image) {
                  $image.css({
                    "max-width": "100%",
                    "height": "auto",
                    "display": "block",
                    "margin": "10px 0"
                  });
                });
              };
              reader.readAsDataURL(file);
            });
          },

          onPaste: function (e) {
            // 붙여넣기 후 글자수 체크
            setTimeout(() => {
              const content = $editor.summernote('code');
              handleChange(content);
            }, 100);
          },

          onDrop: function (e) {
            e.preventDefault(); // 드래그 앤 드롭 방지
          },

          onInit: function () {
            const $noteEditor = $editor.next(".note-editor");
            const $editable = $noteEditor.find(".note-editable");

            // 기본 스타일 적용
            $editable.css({
              padding: "15px",
              "line-height": "2.5",
              "font-size": "18px",
              "writing-mode": "horizontal-tb !important",
              direction: "ltr !important",
              "text-align": "left !important",
              width: "100%",
              "min-width": "1000px",
              "word-wrap": "break-word",
              "word-break": "break-all"
            });

            // 에디터 크기 설정
            $noteEditor.css({
              width: "100%",
              "min-width": "1000px"
            });

            // 초기값 설정
            if (value) {
              $editor.summernote('code', value);
              lastValidContent.current = value;
            }

            if (disabled) {
              $editor.summernote('disable');
            }

            setIsReady(true);
            isInitialized.current = true;
          },
        },
      });
    } catch (error) {
      console.error("Summernote 초기화 오류:", error);
      setIsReady(false);
    }

    return () => {
      if (isInitialized.current && editorRef.current) {
        try {
          $(editorRef.current).summernote('destroy');
          isInitialized.current = false;
        } catch (e) {
          console.error("Summernote 제거 오류:", e);
        }
      }
    };
  }, []);

  // value prop 변경 처리
  useEffect(() => {
    if (isReady && isInitialized.current) {
      const $editor = $(editorRef.current);
      const currentCode = $editor.summernote('code');

      if (currentCode !== value) {
        $editor.summernote('code', value || "");
        lastValidContent.current = value || "";
      }
    }
  }, [value, isReady]);

  // disabled prop 처리
  useEffect(() => {
    if (isReady && isInitialized.current) {
      const $editor = $(editorRef.current);
      if (disabled) {
        $editor.summernote('disable');
      } else {
        $editor.summernote('enable');
      }
    }
  }, [disabled, isReady]);

  return (
    <>
      {/* 커스텀 CSS */}
      <style>{`
        .note-editor {
          width: 100% !important;
          min-width: 1000px !important;
          border: 1px solid #ddd !important;
          border-radius: 4px !important;
        }
        
        .note-editable {
          min-height: 600px !important;
          max-height: 600px !important;
          overflow-y: auto !important;
          word-wrap: break-word !important;
          word-break: break-all !important;
          writing-mode: horizontal-tb !important;
          direction: ltr !important;
          text-align: left !important;
        }
        
        .note-editable img {
          max-width: 100% !important;
          height: auto !important;
          display: block !important;
          margin: 10px 0 !important;
        }
        
        .note-editable ul,
        .note-editable ol {
          margin: 10px 0 !important;
          padding-left: 30px !important;
        }
        
        .note-editable li {
          margin: 8px 0 !important;
          line-height: 1.8 !important;
        }
      `}</style>

      <div style={{ 
        width: "100%",
        minWidth: "1000px"
      }}>
        <textarea
          ref={editorRef}
          style={{ display: isReady ? "none" : "block", width: "100%" }}
        />
      </div>
    </>
  );
}