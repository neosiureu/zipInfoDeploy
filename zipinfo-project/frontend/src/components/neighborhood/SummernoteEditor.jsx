import React, { useEffect, useRef, useState } from "react";
import { toast } from "react-toastify";

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

  // 변경 핸들러
  const handleChange = (content) => {
    const textContent = extractTextContent(content);
    
    if (textContent.length > 2000) {
      toast.error(
        <div>
          <div className="toast-error-title">글자수 초과</div>
          <div className="toast-error-body">
            텍스트는 최대 2000자까지 입력할 수 있습니다.
          </div>
        </div>
      );
      
      if (window.$ && editorRef.current) {
        window.$(editorRef.current).summernote('code', lastValidContent.current);
      }
      return;
    }

    lastValidContent.current = content;
    onChange(content);
  };

  // 리소스 로딩
  const loadResources = async () => {
    return new Promise((resolve) => {
      // Bootstrap CSS가 없으면 추가
      if (!document.getElementById('bootstrap-css-cdn')) {
        const bootstrapLink = document.createElement('link');
        bootstrapLink.id = 'bootstrap-css-cdn';
        bootstrapLink.rel = 'stylesheet';
        bootstrapLink.href = 'https://cdn.jsdelivr.net/npm/bootstrap@4.6.2/dist/css/bootstrap.min.css';
        document.head.appendChild(bootstrapLink);
      }

      // Summernote CSS가 없으면 추가
      if (!document.getElementById('summernote-css-cdn')) {
        const summernoteLink = document.createElement('link');
        summernoteLink.id = 'summernote-css-cdn';
        summernoteLink.rel = 'stylesheet';
        summernoteLink.href = 'https://cdn.jsdelivr.net/npm/summernote@0.8.18/dist/summernote-bs4.min.css';
        document.head.appendChild(summernoteLink);
      }

      // jQuery 로딩
      if (!window.$) {
        const jqueryScript = document.createElement('script');
        jqueryScript.src = 'https://code.jquery.com/jquery-3.6.0.min.js';
        jqueryScript.onload = () => {
          // Summernote 로딩
          const summernoteScript = document.createElement('script');
          summernoteScript.src = 'https://cdn.jsdelivr.net/npm/summernote@0.8.18/dist/summernote-bs4.min.js';
          summernoteScript.onload = () => resolve();
          document.head.appendChild(summernoteScript);
        };
        document.head.appendChild(jqueryScript);
      } else if (window.$.fn.summernote) {
        resolve();
      } else {
        const summernoteScript = document.createElement('script');
        summernoteScript.src = 'https://cdn.jsdelivr.net/npm/summernote@0.8.18/dist/summernote-bs4.min.js';
        summernoteScript.onload = () => resolve();
        document.head.appendChild(summernoteScript);
      }
    });
  };

  // 초기화
  useEffect(() => {
    if (!editorRef.current || isInitialized.current) return;

    loadResources().then(() => {
      const $editor = window.$(editorRef.current);

      $editor.summernote({
        height: 700,
        minHeight: 700,
        placeholder: "내용을 입력해주세요",
        disableResizeEditor: true,
        focus: false,
        lang: 'ko-KR',
        toolbar: [
          ['style', ['bold', 'italic', 'underline']],
          ['color', ['color']],
          ['para', ['ul', 'ol']],
          ['insert', ['picture']]
        ],
        callbacks: {
          onChange: function(contents) {
            handleChange(contents);
          },
          onImageUpload: function(files) {
            Array.from(files).forEach((file) => {
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

              const reader = new FileReader();
              reader.onload = (e) => {
                $editor.summernote('insertImage', e.target.result, function($image) {
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
          onInit: function() {
            const $noteEditor = $editor.next(".note-editor");
            const $editable = $noteEditor.find(".note-editable");

            $editable.css({
              padding: "15px",
              "line-height": "2.5",
              "font-size": "18px",
              "word-wrap": "break-word",
              "word-break": "break-all"
            });

            $noteEditor.css({
              border: "1px solid #ddd",
              borderRadius: "4px"
            });

            if (value) {
              $editor.summernote('code', value);
              lastValidContent.current = value;
            }

            if (disabled) {
              $editor.summernote('disable');
            }

            setIsReady(true);
            isInitialized.current = true;
          }
        }
      });
    });

    return () => {
      if (isInitialized.current && window.$ && editorRef.current) {
        try {
          window.$(editorRef.current).summernote('destroy');
          isInitialized.current = false;
        } catch (e) {}
      }
    };
  }, []);

  // value 변경 처리
  useEffect(() => {
    if (isReady && window.$ && editorRef.current) {
      const $editor = window.$(editorRef.current);
      const currentCode = $editor.summernote('code');

      if (currentCode !== value) {
        $editor.summernote('code', value || "");
        lastValidContent.current = value || "";
      }
    }
  }, [value, isReady]);

  // disabled 처리
  useEffect(() => {
    if (isReady && window.$ && editorRef.current) {
      const $editor = window.$(editorRef.current);
      if (disabled) {
        $editor.summernote('disable');
      } else {
        $editor.summernote('enable');
      }
    }
  }, [disabled, isReady]);

  return (
    <>
      <style>{`
        .note-editor {
          width: 100% !important;
          min-width: 1000px !important;
          border: 1px solid #ddd !important;
          border-radius: 4px !important;
        }
        
        .note-toolbar {
          background: #f8f9fa !important;
          border-bottom: 1px solid #dee2e6 !important;
          padding: 8px !important;
        }
        
        .note-editable {
          min-height: 600px !important;
          max-height: 600px !important;
          overflow-y: auto !important;
        }
        
        .note-editable img {
          max-width: 100% !important;
          height: auto !important;
          display: block !important;
          margin: 10px 0 !important;
        }
      `}</style>

      <div style={{ 
        width: "100%",
        minWidth: "1000px"
      }}>
        <textarea 
          ref={editorRef}
          style={{ width: "100%" }}
        />
      </div>
    </>
  );
}