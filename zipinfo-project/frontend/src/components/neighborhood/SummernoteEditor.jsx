import React, { useEffect, useRef, useState } from "react";

export default function SummernoteEditor({ value, onChange, disabled }) {
  const editorRef = useRef(null);
  const [isReady, setIsReady] = useState(false);
  const [isBootstrapLoaded, setIsBootstrapLoaded] = useState(false);
  const isInitialized = useRef(false);
  const isProcessingChange = useRef(false); // 변경 처리 중 플래그
  const isTyping = useRef(false); // 타이핑 중 플래그
  const typingTimeout = useRef(null); // 타이핑 타임아웃
  const lastChangeTime = useRef(0); // 마지막 변경 시간
  const changeQueue = useRef([]); // 변경사항 큐

  // 개선된 텍스트 추출 함수
  const extractTextContent = (htmlContent) => {
    if (!htmlContent) return "";

    // 임시 DOM 요소를 생성하여 정확한 텍스트 추출
    const tempDiv = document.createElement("div");
    tempDiv.innerHTML = htmlContent;

    // textContent를 사용하여 실제 텍스트만 추출
    const textOnly = tempDiv.textContent || tempDiv.innerText || "";

    // 정리 후 반환
    tempDiv.remove();
    return textOnly.trim();
  };

  // 내용이 비어있는지 확인하는 함수
  const isContentEmpty = (htmlContent) => {
    if (!htmlContent) return true;

    const textContent = extractTextContent(htmlContent);
    const hasImage = htmlContent.includes("<img");
    const hasMedia =
      htmlContent.includes("<video") || htmlContent.includes("<audio");

    // 리스트 요소가 있는지 확인
    const hasListContent =
      htmlContent.includes("<ul") ||
      htmlContent.includes("<ol") ||
      htmlContent.includes("<li");

    // 더 관대한 조건: 리스트가 있거나 텍스트가 있거나 미디어가 있으면 비어있지 않음
    const isEmpty =
      textContent.length === 0 && !hasImage && !hasMedia && !hasListContent;

    return isEmpty;
  };

  // 커서 위치 저장 및 복원 함수
  const saveCursorPosition = () => {
    const selection = window.getSelection();
    if (selection.rangeCount > 0) {
      const range = selection.getRangeAt(0);
      const $editable = window
        .$(editorRef.current)
        .next(".note-editor")
        .find(".note-editable");

      if ($editable[0] && $editable[0].contains(range.startContainer)) {
        return {
          startContainer: range.startContainer,
          startOffset: range.startOffset,
          endContainer: range.endContainer,
          endOffset: range.endOffset,
        };
      }
    }
    return null;
  };

  const restoreCursorPosition = (savedPosition) => {
    if (!savedPosition) return false;

    try {
      const $editable = window
        .$(editorRef.current)
        .next(".note-editor")
        .find(".note-editable");

      // 저장된 노드가 여전히 DOM에 있는지 확인
      if (
        savedPosition.startContainer &&
        savedPosition.startContainer.parentNode &&
        $editable[0].contains(savedPosition.startContainer)
      ) {
        const range = document.createRange();
        const selection = window.getSelection();

        range.setStart(savedPosition.startContainer, savedPosition.startOffset);
        range.setEnd(savedPosition.endContainer, savedPosition.endOffset);

        selection.removeAllRanges();
        selection.addRange(range);
        return true;
      }
    } catch (e) {
      // 복원 실패
    }
    return false;
  };

  // 타이핑 상태 관리 함수
  const startTyping = () => {
    isTyping.current = true;

    // 이전 타임아웃 클리어
    if (typingTimeout.current) {
      clearTimeout(typingTimeout.current);
    }

    // 500ms 후 타이핑 종료로 간주
    typingTimeout.current = setTimeout(() => {
      isTyping.current = false;
      processQueuedChanges();
    }, 75);
  };

  // 큐에 쌓인 변경사항 처리
  const processQueuedChanges = () => {
    if (changeQueue.current.length === 0) return;

    // 가장 최근 변경사항만 처리
    const latestChange = changeQueue.current[changeQueue.current.length - 1];
    changeQueue.current = [];

    processChange(latestChange.contents);
  };

  // 실제 변경사항 처리 함수
  const processChange = (contents) => {
    if (isProcessingChange.current) return;

    isProcessingChange.current = true;

    // 커서 위치 저장
    const savedCursor = saveCursorPosition();

    if (onChange) onChange(contents);

    // 내용 체크
    const isEmpty = isContentEmpty(contents);
    const $editable = window
      .$(editorRef.current)
      .next(".note-editor")
      .find(".note-editable");

    const editorTextContent = $editable.text().trim();
    const editorHasContent =
      editorTextContent.length > 0 ||
      $editable.find("img, ul, ol, li").length > 0;

    const shouldShowPlaceholder = isEmpty && !editorHasContent;
    const currentlyHasPlaceholder = $editable.hasClass("force-placeholder");

    // 플레이스홀더 상태 변경이 필요한 경우에만 처리
    if (shouldShowPlaceholder !== currentlyHasPlaceholder) {
      if (shouldShowPlaceholder) {
        $editable.addClass("force-placeholder");
      } else {
        $editable.removeClass("force-placeholder");
      }

      // 커서 위치 복원
      if (savedCursor && !isTyping.current) {
        requestAnimationFrame(() => {
          if (!restoreCursorPosition(savedCursor)) {
            // 복원 실패 시 에디터 끝으로 이동
            try {
              const range = document.createRange();
              const selection = window.getSelection();

              // 에디터의 마지막 위치로 이동
              range.selectNodeContents($editable[0]);
              range.collapse(false);

              selection.removeAllRanges();
              selection.addRange(range);
            } catch (e) {
              // 무시
            }
          }
          isProcessingChange.current = false;
        });
      } else {
        isProcessingChange.current = false;
      }
    } else {
      isProcessingChange.current = false;
    }

    // 자동 스크롤 (타이핑 중이 아닐 때만)
    if (
      !shouldShowPlaceholder &&
      !isTyping.current &&
      shouldShowPlaceholder === currentlyHasPlaceholder
    ) {
      setTimeout(() => {
        if ($editable.length) {
          $editable.animate({ scrollTop: $editable[0].scrollHeight }, 200);
        }
      }, 100);
    }
  };

  // Bootstrap CSS 동적 로딩
  const loadBootstrapCSS = () => {
    return new Promise((resolve) => {
      if (document.getElementById("bootstrap-css-for-summernote")) {
        setIsBootstrapLoaded(true);
        resolve();
        return;
      }

      const link = document.createElement("link");
      link.id = "bootstrap-css-for-summernote";
      link.rel = "stylesheet";
      link.href =
        "https://cdn.jsdelivr.net/npm/bootstrap@4.6.2/dist/css/bootstrap.min.css";

      link.onload = () => {
        setIsBootstrapLoaded(true);
        resolve();
      };

      link.onerror = () => {
        console.error("Bootstrap CSS 로드 실패");
        resolve();
      };

      document.head.appendChild(link);
    });
  };

  // Bootstrap css에 대해 컴포넌트 마운트 시에만 로드
  useEffect(() => {
    loadBootstrapCSS();

    return () => {
      const bootstrapLink = document.getElementById(
        "bootstrap-css-for-summernote"
      );
      if (bootstrapLink) {
        bootstrapLink.remove();
      }
    };
  }, []);

  useEffect(() => {
    if (!editorRef.current || isInitialized.current || !isBootstrapLoaded)
      return;

    if (typeof window.$ === "undefined" || !window.$.fn.summernote) {
      setIsReady(false);
      return;
    }

    const $editor = window.$(editorRef.current);

    try {
      $editor.summernote({
        height: 700,
        minHeight: 700,
        lang: "en-US",
        placeholder: "",
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
            const currentTime = Date.now();

            // 빠른 연속 입력 감지 (100ms 이내)
            if (currentTime - lastChangeTime.current < 100) {
              startTyping();

              // 변경사항을 큐에 추가
              changeQueue.current.push({
                contents: contents,
                timestamp: currentTime,
              });

              // 큐가 너무 커지지 않도록 제한
              if (changeQueue.current.length > 10) {
                changeQueue.current = changeQueue.current.slice(-5);
              }

              lastChangeTime.current = currentTime;
              return;
            }

            lastChangeTime.current = currentTime;

            // 타이핑 중이 아니면 즉시 처리
            if (!isTyping.current) {
              processChange(contents);
            } else {
              // 타이핑 중이면 큐에 추가
              changeQueue.current.push({
                contents: contents,
                timestamp: currentTime,
              });
            }
          },

          // 키보드 이벤트로 타이핑 감지
          onKeydown: function (e) {
            // 실제 문자 입력 키만 감지
            if (e.key && e.key.length === 1) {
              startTyping();
            }
          },

          onKeyup: function (e) {
            // 연속 입력 감지를 위한 타이핑 상태 갱신
            if (e.key && e.key.length === 1) {
              startTyping();
            }
          },

          onImageUpload: function (files) {
            for (let i = 0; i < files.length; i++) {
              const file = files[i];
              const formData = new FormData();
              formData.append("image", file);

              fetch("http://localhost:8080/editBoard/uploadImage", {
                method: "POST",
                body: formData,
              })
                .then((response) => response.text())
                .then((serverImageUrl) => {
                  console.log("서버 업로드 성공:", serverImageUrl);
                  // 서버 URL은 나중에 DB 저장할 때 사용될 예정
                })
                .catch((error) => {
                  console.error("서버 업로드 실패:", error);
                });
              const reader = new FileReader();

              reader.onload = (e) => {
                const img = new Image();
                img.onload = () => {
                  $editor.summernote(
                    "insertImage",
                    e.target.result,
                    function ($image) {
                      $image.css({
                        "max-width": "100%",
                        height: "auto",
                        display: "block",
                        margin: "5px 0",
                      });

                      setTimeout(() => {
                        const range = document.createRange();
                        const sel = window.getSelection();

                        const $newP = window.$("<p><br></p>");
                        $image.after($newP);

                        range.setStart($newP[0], 0);
                        range.collapse(true);
                        sel.removeAllRanges();
                        sel.addRange(range);
                      }, 10);
                    }
                  );
                };
                img.src = e.target.result;
              };
              reader.readAsDataURL(file);
            }
          },

          onImageLinkInsert: function (url) {
            const img = new Image();
            img.onload = () => {
              $editor.summernote("insertImage", url, function ($image) {
                $image.css({
                  "max-width": "100%",
                  height: "auto",
                  display: "block",
                  margin: "5px 0",
                });

                setTimeout(() => {
                  const range = document.createRange();
                  const sel = window.getSelection();

                  const $newP = window.$("<p><br></p>");
                  $image.after($newP);

                  range.setStart($newP[0], 0);
                  range.collapse(true);
                  sel.removeAllRanges();
                  sel.addRange(range);
                }, 10);
              });
            };
            img.onerror = () => {
              alert("이미지를 불러올 수 없습니다.");
            };
            img.src = url;
          },

          onInit: function () {
            const $noteEditor = $editor.next(".note-editor");
            const $editable = $noteEditor.find(".note-editable");

            $editable.css({
              padding: "5px 40px 50px 10px",
              "line-height": "2.5",
              "font-size": "18px",
              "writing-mode": "horizontal-tb !important",
              direction: "ltr !important",
              "text-orientation": "mixed !important",
              "unicode-bidi": "normal !important",
              width: "100%",
              "min-width": "1000px",
              "text-align": "left !important",
            });

            // CSS 스타일 추가
            if (!document.getElementById("simple-summernote-fix")) {
              const style = document.createElement("style");
              style.id = "simple-summernote-fix";
              style.textContent = `
                  .note-editable {
                  writing-mode: horizontal-tb !important;
                  direction: ltr !important;
                  unicode-bidi: normal !important;
                  text-align: left !important;
                  overflow-wrap: break-word;
                  word-break: break-all;
                  white-space: normal;
                  overflow-x: hidden !important;
                  overflow-y: auto !important;
                  padding-bottom: 50px !important;
                  }

                  /* 스크롤바 스타일 개선 */
                  .note-editable::-webkit-scrollbar {
                    width: 8px !important;
                  }

                  .note-editable::-webkit-scrollbar-track {
                    background: #f1f1f1 !important;
                    border-radius: 4px !important;
                  }

                  .note-editable::-webkit-scrollbar-thumb {
                    background: #c1c1c1 !important;
                    border-radius: 4px !important;
                  }

                  .note-editable::-webkit-scrollbar-thumb:hover {
                    background: #a8a8a8 !important;
                  }
                 
                  .note-editor, .note-toolbar {
                 width: 100% !important;
                min-width: 0 !important;
                  box-sizing: border-box !important;
                  }

                /* 각종 문단 p태그에 대한 스타일 */
                  .note-editable p {
                    margin: 0 !important;
                    padding: 0 !important;
                    writing-mode: horizontal-tb !important;
                    direction: ltr !important;
                    text-align: left !important;
                    word-wrap: break-word !important;
                    word-break: break-all !important;
                  }

                  /* 에디터 내 모든 요소에 방향 강제 적용 */
                  .note-editable * {
                    writing-mode: horizontal-tb !important;
                    direction: ltr !important;
                    text-align: left !important;
                  }

                  /* 커서 위치 강제 조정 */
                  .note-editable:focus {
                    direction: ltr !important;
                    text-align: left !important;
                  }

                  /* 0바이트일 때 강제 플레이스홀더 표시 */
                  .note-editable.force-placeholder:before {
                    content: '내용을 입력해주세요' !important;
                    color: #999 !important;
                    font-size: 18px !important;
                    line-height: 2.5 !important;
                    writing-mode: horizontal-tb !important;
                    direction: ltr !important;
                    padding-top: 5px !important;
                    padding-left: 10px !important;
                    display: block !important;
                    position: absolute !important;
                    top: 0 !important;
                    left: 0 !important;
                    pointer-events: none !important;
                    z-index: 1 !important;
                  }

                  /* 플레이스홀더(내용 없을 때 표시) 스타일 */
                  .note-placeholder {
                    writing-mode: horizontal-tb !important;
                    direction: ltr !important;
                    padding-top: 10px !important;
                  }

                  /* 에디터가 비었을 때 가상 요소에도 방향 적용 */
                  .note-editable:empty:before {
                    writing-mode: horizontal-tb !important;
                    direction: ltr !important;
                    padding-top: 10px !important;
                  }

                  /* 에디터 내부 이미지 */
                  .note-editable img {
                    max-width: 100% !important;
                    height: auto !important;
                    display: block !important;
                    margin: 5px 0 !important;
                    object-fit: contain !important;
                  }

                  /* 리스트 스타일 추가 */
                  .note-editable ul,
                  .note-editable ol {
                    margin: 10px 0 !important;
                    padding-left: 30px !important;
                  }
                  
                  .note-editable ul li,
                  .note-editable ol li {
                    margin: 8px 0 !important;
                    line-height: 1.8 !important;
                    font-size: 16px !important;
                  }
                  
                  .note-editable ul li::marker {
                    font-size: 14px !important;
                    color: #666 !important;
                  }
                  
                  .note-editable ol li::marker {
                    font-size: 16px !important;
                    font-weight: bold !important;
                    color: #333 !important;
                  }
                  
                  /* 중첩 리스트 스타일 */
                  .note-editable ul ul,
                  .note-editable ol ol {
                    margin: 5px 0 !important;
                    padding-left: 20px !important;
                  }`;
              document.head.appendChild(style);
            }

            $noteEditor.css({
              height: "700px",
              "min-height": "700px",
              width: "100%",
              "min-width": "1000px",
              "overflow-x": "hidden",
              "overflow-y": "auto",
            });

            $noteEditor.find(".note-resizebar").remove();

            // 초기 바이트 체크
            const initialIsEmpty = isContentEmpty(value);

            if (initialIsEmpty) {
              $editable.addClass("force-placeholder");
            }

            if (value) {
              $editor.summernote("code", value);
            }

            if (disabled) {
              $editor.summernote("disable");
            }

            // 초기 커서 위치를 왼쪽으로 설정
            setTimeout(() => {
              $editable.focus();
              const range = document.createRange();
              const sel = window.getSelection();

              const firstChild = $editable[0].firstChild;
              if (firstChild) {
                if (firstChild.nodeType === Node.TEXT_NODE) {
                  range.setStart(firstChild, 0);
                } else {
                  range.setStart(firstChild, 0);
                }
                range.collapse(true);
                sel.removeAllRanges();
                sel.addRange(range);
              }
            }, 100);

            setIsReady(true);
            isInitialized.current = true;
          },
        },
      });
    } catch (error) {
      console.error("Summernote 오류:", error);
      setIsReady(false);
    }

    return () => {
      // 타임아웃 정리
      if (typingTimeout.current) {
        clearTimeout(typingTimeout.current);
      }

      if (isInitialized.current && window.$ && editorRef.current) {
        try {
          const styleElement = document.getElementById("simple-summernote-fix");
          if (styleElement) styleElement.remove();
          window.$(editorRef.current).summernote("destroy");
          isInitialized.current = false;
        } catch (e) {}
      }
    };
  }, [isBootstrapLoaded]);

  useEffect(() => {
    if (isReady && isInitialized.current && window.$) {
      const $editor = window.$(editorRef.current);
      const currentCode = $editor.summernote("code");
      if (currentCode !== value) {
        $editor.summernote("code", value || "");
      }
    }
  }, [value, isReady]);

  useEffect(() => {
    if (isReady && isInitialized.current && window.$) {
      const $editor = window.$(editorRef.current);
      if (disabled) {
        $editor.summernote("disable");
      } else {
        $editor.summernote("enable");
      }
    }
  }, [disabled, isReady]);

  return (
    <div
      style={{
        border: "1px solid #ddd",
        borderRadius: "4px",
        minHeight: "700px",
        width: "100%",
        minWidth: "1000px",
      }}
    >
      {!isBootstrapLoaded && (
        <div style={{ padding: "20px", textAlign: "center", color: "#666" }}>
          Bootstrap 로딩 중...
        </div>
      )}
      {isBootstrapLoaded && !isReady && (
        <div style={{ padding: "20px", textAlign: "center", color: "#666" }}>
          에디터 준비 중...
        </div>
      )}
      <textarea
        ref={editorRef}
        style={{ display: isReady ? "none" : "block", width: "100%" }}
      />
    </div>
  );
}
