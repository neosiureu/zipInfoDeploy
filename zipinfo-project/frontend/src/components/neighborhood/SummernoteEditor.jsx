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
  const [isCSSReady, setIsCSSReady] = useState(false);
  const isComposing = useRef(false);

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

    // 리스트 요소가 있는지 확인
    const hasListContent =
      htmlContent.includes("<ul") ||
      htmlContent.includes("<ol") ||
      htmlContent.includes("<li");

    // 더 관대한 조건: 리스트가 있거나 텍스트가 있거나 미디어가 있으면 비어있지 않음
    const isEmpty = textContent.length === 0 && !hasImage && !hasListContent;

    return isEmpty;
  };

  // 텍스트 오프셋 계산 함수
  const getTextOffset = (root, node, offset) => {
    let textOffset = 0;
    const walker = document.createTreeWalker(
      root,
      NodeFilter.SHOW_TEXT,
      null,
      false
    );

    let currentNode;
    while ((currentNode = walker.nextNode())) {
      if (currentNode === node) {
        return textOffset + offset;
      }
      textOffset += currentNode.textContent.length;
    }
    return textOffset;
  };

  // 텍스트 오프셋에서 DOM 위치 찾기 함수
  const getNodeFromTextOffset = (root, targetOffset) => {
    let currentOffset = 0;
    const walker = document.createTreeWalker(
      root,
      NodeFilter.SHOW_TEXT,
      null,
      false
    );

    let currentNode;
    while ((currentNode = walker.nextNode())) {
      const nodeLength = currentNode.textContent.length;
      if (currentOffset + nodeLength >= targetOffset) {
        return {
          node: currentNode,
          offset: targetOffset - currentOffset,
        };
      }
      currentOffset += nodeLength;
    }

    // 텍스트 끝을 넘어선 경우
    const lastTextNode = getLastTextNode(root);
    if (lastTextNode) {
      return {
        node: lastTextNode,
        offset: lastTextNode.textContent.length,
      };
    }

    return null;
  };

  // 마지막 텍스트 노드 찾기
  const getLastTextNode = (root) => {
    const walker = document.createTreeWalker(
      root,
      NodeFilter.SHOW_TEXT,
      null,
      false
    );

    let lastNode = null;
    let currentNode;
    while ((currentNode = walker.nextNode())) {
      lastNode = currentNode;
    }
    return lastNode;
  };

  // 텍스트 오프셋 기반 커서 위치 저장 함수
  const saveCursorPosition = () => {
    console.log(" [CURSOR] saveCursorPosition 시작");

    const selection = window.getSelection();
    if (selection.rangeCount > 0) {
      const range = selection.getRangeAt(0);
      const $editable = window
        .$(editorRef.current)
        .next(".note-editor")
        .find(".note-editable");

      if ($editable[0] && $editable[0].contains(range.startContainer)) {
        // 기존 노드 참조 방식
        // const nodeBasedCursor = {
        //   startContainer: range.startContainer,
        //   startOffset: range.startOffset,
        //   endContainer: range.endContainer,
        //   endOffset: range.endOffset,
        // };

        // 새로운 텍스트 오프셋 방식
        const textOffset = getTextOffset(
          $editable[0],
          range.startContainer,
          range.startOffset
        );
        const endTextOffset = getTextOffset(
          $editable[0],
          range.endContainer,
          range.endOffset
        );

        const cursor = {
          // // 기존 방식 (백업용)
          // ...nodeBasedCursor,
          // 새로운 방식 (메인)
          textOffset: textOffset,
          endTextOffset: endTextOffset,
          editable: $editable[0],
        };

        return cursor;
      } else {
        console.log(" [CURSOR] 커서 위치 저장 실패: editable 영역 밖");
      }
    } else {
      console.log(" [CURSOR] 커서 위치 저장 실패: selection.rangeCount = 0");
    }
    return null;
  };

  // 개선된 커서 위치 복원 함수
  const restoreCursorPosition = (savedPosition) => {
    if (!savedPosition) {
      console.log(" [CURSOR] 복원 실패: savedPosition이 null");
      return false;
    }

    console.log(" [CURSOR] 커서 위치 복원 시도:", {
      nodeOffset: savedPosition.startOffset,
      textOffset: savedPosition.textOffset,
    });

    const $editable = window
      .$(editorRef.current)
      .next(".note-editor")
      .find(".note-editable");

    if (!$editable[0]) {
      console.log(" [CURSOR] 복원 실패: editable 없음");
      return false;
    }

    try {
      // 1단계: 기존 노드 참조 방식 시도

      const startPos = getNodeFromTextOffset(
        $editable[0],
        savedPosition.textOffset || 0
      );
      const endPos = getNodeFromTextOffset(
        $editable[0],
        savedPosition.endTextOffset || savedPosition.textOffset || 0
      );

      if (startPos && endPos) {
        const range = document.createRange();
        const selection = window.getSelection();

        range.setStart(startPos.node, startPos.offset);
        range.setEnd(endPos.node, endPos.offset);

        selection.removeAllRanges();
        selection.addRange(range);

        console.log("[CURSOR] 텍스트 오프셋 방식으로 복원 성공");
        return true;
      } else {
        console.log(" [CURSOR] 텍스트 오프셋 방식 실패: 위치 찾을 수 없음");
      }
    } catch (e) {
      console.log(" [CURSOR] 복원 실패 (예외):", e.message);
    }

    return false;
  };

  // 타이핑 상태 관리 함수
  const startTyping = () => {
    const wasTyping = isTyping.current;
    isTyping.current = true;

    console.log(" [TYPING] startTyping 호출", {
      wasTyping,
      isComposing: isComposing.current,
      queueLength: changeQueue.current.length,
    });

    // 이전 타임아웃 클리어
    if (typingTimeout.current) {
      clearTimeout(typingTimeout.current);
    }

    // 200ms 후 타이핑 종료로 간주 (안정성 향상)
    typingTimeout.current = setTimeout(() => {
      console.log("[TYPING] 타이핑 종료 (타임아웃)");
      isTyping.current = false;
      processQueuedChanges();
    }, 200);
  };

  // 큐에 쌓인 변경사항 처리
  const processQueuedChanges = () => {
    console.log(" [QUEUE] processQueuedChanges 호출", {
      queueLength: changeQueue.current.length,
    });

    if (changeQueue.current.length === 0) return;

    // 가장 최근 변경사항만 처리
    const latestChange = changeQueue.current[changeQueue.current.length - 1];
    changeQueue.current = [];

    console.log(
      "📋 [QUEUE] 큐에서 최신 변경사항 처리, 내용 길이:",
      latestChange.contents.length
    );
    processChange(latestChange.contents);
  };

  // 개선된 변경사항 처리 함수
  const processChange = (contents) => {
    console.log(" [PROCESS] processChange 시작", {
      contentsLength: contents.length,
      isProcessing: isProcessingChange.current,
      isTyping: isTyping.current,
      isComposing: isComposing.current,
    });

    if (isProcessingChange.current) {
      console.log(" [PROCESS] 이미 처리 중이므로 중단");
      return;
    }

    // 커서 위치 먼저 저장
    const currentCursor = saveCursorPosition();
    isProcessingChange.current = true;

    // 플레이스홀더 처리
    const isEmpty = isContentEmpty(contents);
    const $editable = window
      .$(editorRef.current)
      .next(".note-editor")
      .find(".note-editable");

    const shouldShowPlaceholder =
      isEmpty && $editable.text().trim().length === 0;
    const currentlyHasPlaceholder = $editable.hasClass("force-placeholder");

    console.log(" [PLACEHOLDER]", {
      isEmpty,
      shouldShow: shouldShowPlaceholder,
      currentlyHas: currentlyHasPlaceholder,
      editableTextLength: $editable.text().trim().length,
    });

    if (shouldShowPlaceholder !== currentlyHasPlaceholder) {
      if (shouldShowPlaceholder) {
        console.log(" [PLACEHOLDER] 플레이스홀더 추가");
        $editable.addClass("force-placeholder");
      } else {
        console.log(" [PLACEHOLDER] 플레이스홀더 제거");
        $editable.removeClass("force-placeholder");
      }
    }

    // 커서 복원 (isComposing 체크 제거, 타이핑 중이 아닐 때만)
    if (currentCursor && !isTyping.current) {
      console.log(" [PROCESS] 커서 복원 시도");
      requestAnimationFrame(() => {
        console.log("[PROCESS] requestAnimationFrame 실행");
        const restored = restoreCursorPosition(currentCursor);
        console.log(" [PROCESS] 커서 복원 결과:", restored ? "성공" : "실패");

        // 커서 복원 후 React 상태 업데이트
        setTimeout(() => {
          console.log(" [PROCESS] onChange 호출");
          if (onChange) onChange(contents);
        }, 10);
      });
    } else {
      console.log("⏭[PROCESS] 커서 복원 건너뜀", {
        hasCursor: !!currentCursor,
        isTyping: isTyping.current,
      });
      // 커서 복원을 하지 않는 경우에는 즉시 onChange 호출
      console.log(" [PROCESS] onChange 즉시 호출");
      if (onChange) onChange(contents);
    }

    isProcessingChange.current = false;
    console.log("[PROCESS] processChange 완료");
  };

  // 진짜 썸머노츠 동적 로딩
  const loadSummernoteCSS = () => {
    return new Promise((resolve) => {
      if (document.getElementById("summernote-css-for-summernote")) {
        resolve();
        return;
      }
      const link = document.createElement("link");
      link.id = "summernote-css-for-summernote";
      link.rel = "stylesheet";
      link.href =
        "https://cdn.jsdelivr.net/npm/summernote@0.8.18/dist/summernote-bs4.min.css";
      link.onload = resolve;
      link.onerror = resolve;
      document.head.appendChild(link);
    });
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

  const removeLoadedCSS = () => {
    const bootstrap = document.getElementById("bootstrap-css-for-summernote");
    if (bootstrap) bootstrap.remove();
    const summernote = document.getElementById("summernote-css-for-summernote");
    if (summernote) summernote.remove();
  };

  // Bootstrap css에 대해 컴포넌트 마운트 시에만 로드
  useEffect(() => {
    Promise.all([loadBootstrapCSS(), loadSummernoteCSS()]).then(() => {
      setIsCSSReady(true);
    });
    return () => {
      removeLoadedCSS();
    };
  }, []);

  // Composition 이벤트 핸들러
  useEffect(() => {
    if (!isReady || !isInitialized.current || !window.$) return;

    const $editable = window
      .$(editorRef.current)
      .next(".note-editor")
      .find(".note-editable");

    if ($editable.length) {
      const handleCompositionStart = () => {
        console.log("🇰🇷 [COMPOSITION] 조합 시작");
        isComposing.current = true;
      };

      const handleCompositionEnd = () => {
        console.log("🇰🇷 [COMPOSITION] 조합 종료");
        // 조합 종료를 약간 지연시켜 안정성 확보
        setTimeout(() => {
          isComposing.current = false;
          console.log("[COMPOSITION] 조합 상태 완전 종료");
        }, 50);
      };

      $editable.on("compositionstart", handleCompositionStart);
      $editable.on("compositionend", handleCompositionEnd);

      return () => {
        $editable.off("compositionstart", handleCompositionStart);
        $editable.off("compositionend", handleCompositionEnd);
      };
    }
  }, [isReady]);

  // Summernote 초기화
  useEffect(() => {
    if (!editorRef.current || isInitialized.current || !isCSSReady) return;

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
            const timeDiff = currentTime - lastChangeTime.current;

            console.log("[ONCHANGE]", {
              contentsLength: contents.length,
              timeDiff,
              isTyping: isTyping.current,
              queueLength: changeQueue.current.length,
            });

            // 빠른 연속 입력 감지 (150ms 이내로 조정)
            if (timeDiff < 150) {
              console.log("⚡ [ONCHANGE] 빠른 입력 감지 - 큐에 추가");
              startTyping();

              // 변경사항을 큐에 추가
              changeQueue.current.push({
                contents: contents,
                timestamp: currentTime,
              });

              // 큐가 너무 커지지 않도록 제한
              if (changeQueue.current.length > 10) {
                changeQueue.current = changeQueue.current.slice(-5);
                console.log("📋 [ONCHANGE] 큐 크기 제한으로 인한 정리");
              }

              lastChangeTime.current = currentTime;
              return;
            }

            lastChangeTime.current = currentTime;

            // 타이핑 중이 아니면 즉시 처리
            if (!isTyping.current) {
              console.log(" [ONCHANGE] 즉시 처리");
              processChange(contents);
            } else {
              console.log(" [ONCHANGE] 타이핑 중이므로 큐에 추가");
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
            if (e.key && e.key.length === 1 && !isComposing.current) {
              startTyping();
            }
          },

          onKeyup: function (e) {
            // 연속 입력 감지를 위한 타이핑 상태 갱신
            if (e.key && e.key.length === 1 && !isComposing.current) {
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

                  .note-editable p {
                    margin: 0 !important;
                    padding: 0 !important;
                    writing-mode: horizontal-tb !important;
                    direction: ltr !important;
                    text-align: left !important;
                    word-wrap: break-word !important;
                    word-break: break-all !important;
                  }

                  .note-editable * {
                    writing-mode: horizontal-tb !important;
                    direction: ltr !important;
                    text-align: left !important;
                  }

                  .note-editable:focus {
                    direction: ltr !important;
                    text-align: left !important;
                  }

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

                  .note-placeholder {
                    writing-mode: horizontal-tb !important;
                    direction: ltr !important;
                    padding-top: 10px !important;
                  }

                  .note-editable:empty:before {
                    writing-mode: horizontal-tb !important;
                    direction: ltr !important;
                    padding-top: 10px !important;
                  }

                  .note-editable img {
                    max-width: 100% !important;
                    height: auto !important;
                    display: block !important;
                    margin: 5px 0 !important;
                    object-fit: contain !important;
                  }

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

            // 초기 상태 설정
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

            // 초기 커서 위치 설정
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
            setTimeout(() => {
              window.scrollTo(0, 0);
            }, 200);
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
  }, [isCSSReady]);

  // value prop 변경 처리 (개선된 버전)
  useEffect(() => {
    if (isReady && isInitialized.current && window.$) {
      const $editor = window.$(editorRef.current);
      const currentCode = $editor.summernote("code");

      // 더 엄격한 비교 + 처리 중일 때는 업데이트 안함
      if (
        currentCode !== value &&
        !isProcessingChange.current &&
        !isTyping.current
      ) {
        console.log("🔄 [EFFECT] value prop 변경으로 인한 코드 업데이트");
        $editor.summernote("code", value || "");
      } else {
        console.log("⏭️ [EFFECT] 코드 업데이트 건너뜀", {
          sameValue: currentCode === value,
          isProcessing: isProcessingChange.current,
          isTyping: isTyping.current,
        });
      }
    }
  }, [value, isReady]);

  // disabled prop 처리
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
      <textarea
        ref={editorRef}
        style={{ display: isReady ? "none" : "block", width: "100%" }}
      />
    </div>
  );
}
