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

  // CSS와 JS 동적 로딩
  const loadResources = () => {
    return new Promise((resolve) => {
      // 이미 로드되었으면 바로 리턴
      if (window.$ && window.$.fn.summernote) {
        resolve();
        return;
      }

      // Bootstrap CSS
      if (!document.getElementById('bootstrap-css')) {
        const bootstrapCSS = document.createElement('link');
        bootstrapCSS.id = 'bootstrap-css';
        bootstrapCSS.rel = 'stylesheet';
        bootstrapCSS.href = 'https://cdn.jsdelivr.net/npm/bootstrap@4.6.2/dist/css/bootstrap.min.css';
        document.head.appendChild(bootstrapCSS);
      }

      // Summernote CSS
      if (!document.getElementById('summernote-css')) {
        const summernoteCSS = document.createElement('link');
        summernoteCSS.id = 'summernote-css';
        summernoteCSS.rel = 'stylesheet';
        summernoteCSS.href = 'https://cdn.jsdelivr.net/npm/summernote@0.8.18/dist/summernote-bs4.min.css';
        document.head.appendChild(summernoteCSS);
      }

      // jQuery
      if (!window.$) {
        const jqueryScript = document.createElement('script');
        jqueryScript.src = 'https://code.jquery.com/jquery-3.6.0.min.js';
        jqueryScript.onload = () => {
          // Summernote
          const summernoteScript = document.createElement('script');
          summernoteScript.src = 'https://cdn.jsdelivr.net/npm/summernote@0.8.18/dist/summernote-bs4.min.js';
          summernoteScript.onload = () => {
            resolve();
          };
          document.head.appendChild(summernoteScript);
        };
        document.head.appendChild(jqueryScript);
      } else {
        resolve();
      }
    });
  };

  // Summernote 초기화
  useEffect(() => {
    if (!editorRef.current || isInitialized.current) return;

    loadResources().then(() => {
      const $editor = window.$(editorRef.current);

      try {
        $editor.summernote({
          height: 700,
          minHeight: 700,
          placeholder: "",
          disableResizeEditor: true,
          focus: false,
          lang: 'ko-KR',
          // 가로 툴바로 설정
          toolbar: [
            ['style', ['bold', 'italic', 'underline']],
            ['fontsize', ['fontsize']],
            ['color', ['color']],
            ['para', ['ul', 'ol', 'paragraph']],
            ['insert', ['picture']],
            ['view', ['codeview']]
          ],
          callbacks: {
            onChange: function (contents) {
              handleChange(contents);
            },

            onImageUpload: function (files) {
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
              setTimeout(() => {
                const content = $editor.summernote('code');
                handleChange(content);
              }, 100);
            },

            onDrop: function (e) {
              e.preventDefault();
            },

            onInit: function () {
              const $noteEditor = $editor.next(".note-editor");
              const $editable = $noteEditor.find(".note-editable");
              const $toolbar = $noteEditor.find(".note-toolbar");

              // 툴바 강제로 가로 레이아웃
              $toolbar.css({
                display: 'block',
                width: '100%',
                padding: '10px'
              });

              // 툴바 버튼들 가로 정렬
              $toolbar.find('.note-btn-group').css({
                display: 'inline-block',
                marginRight: '5px',
                verticalAlign: 'top'
              });

              // 기본 스타일 적용
              $editable.css({
                padding: "15px",
                "line-height": "2.5",
                "font-size": "18px",
                width: "100%",
                "min-width": "1000px",
                "word-wrap": "break-word",
                "word-break": "break-all"
              });

              // 에디터 크기 설정
              $noteEditor.css({
                width: "100%",
                "min-width": "1000px",
                border: "1px solid #ddd",
                borderRadius: "4px"
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
    });

    return () => {
      if (isInitialized.current && window.$ && editorRef.current) {
        try {
          window.$(editorRef.current).summernote('destroy');
          isInitialized.current = false;
        } catch (e) {
          console.error("Summernote 제거 오류:", e);
        }
      }
    };
  }, []);

  // value prop 변경 처리
  useEffect(() => {
    if (isReady && isInitialized.current && window.$) {
      const $editor = window.$(editorRef.current);
      const currentCode = $editor.summernote('code');

      if (currentCode !== value) {
        $editor.summernote('code', value || "");
        lastValidContent.current = value || "";
      }
    }
  }, [value, isReady]);

  // disabled prop 처리
  useEffect(() => {
    if (isReady && isInitialized.current && window.$) {
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
          display: block !important;
          width: 100% !important;
          background: #f8f9fa !important;
          border-bottom: 1px solid #dee2e6 !important;
          padding: 10px !important;
        }
        
        .note-toolbar .note-btn-group {
          display: inline-block !important;
          margin-right: 8px !important;
          vertical-align: top !important;
        }
        
        .note-toolbar .note-btn {
          display: inline-block !important;
          margin-right: 2px !important;
        }
        
        .note-editable {
          min-height: 600px !important;
          max-height: 600px !important;
          overflow-y: auto !important;
          word-wrap: break-word !important;
          word-break: break-all !important;
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
        
        /* 드롭다운 메뉴 숨기기 */
        .note-dropdown-menu {
          display: none !important;
        }
      `}</style>

      <div style={{ 
        width: "100%",
        minWidth: "1000px"
      }}>
        <textarea
          ref={editorRef}
          style={{ 
            display: isReady ? "none" : "block", 
            width: "100%",
            height: "700px",
            border: "1px solid #ddd",
            borderRadius: "4px",
            padding: "15px",
            fontSize: "16px"
          }}
        />
      </div>
    </>
  );
}