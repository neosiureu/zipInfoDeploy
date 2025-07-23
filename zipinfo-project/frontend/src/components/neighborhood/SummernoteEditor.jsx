import React, { useEffect, useRef, useState } from "react";
import { toast } from "react-toastify";

export default function SummernoteEditor({ value, onChange, disabled }) {
  const editorRef = useRef(null);
  const [isReady, setIsReady] = useState(false);
  const [isBootstrapLoaded, setIsBootstrapLoaded] = useState(false);
  const isInitialized = useRef(false);
  const isProcessingChange = useRef(false);
  const isTyping = useRef(false);
  const typingTimeout = useRef(null);
  const lastChangeTime = useRef(0);
  const changeQueue = useRef([]);
  const [isCSSReady, setIsCSSReady] = useState(false);
  const isComposing = useRef(false);
  const lastValidHtml = useRef("");
  const savedCursor = useRef(null);
  const isProgrammatic = useRef(false);
  
  // 추가된 refs
  const pendingUpdate = useRef(false);
  const mutationObserver = useRef(null);
  const isManualChange = useRef(false);
  const lastKnownSelection = useRef(null);
  
  // 개선된 텍스트 추출 함수
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
    const hasListContent = htmlContent.includes("<ul") || htmlContent.includes("<ol") || htmlContent.includes("<li");
    return textContent.length === 0 && !hasImage && !hasListContent;
  };

  // 개선된 텍스트 오프셋 계산 - 더 정확한 위치 계산
  const getDetailedTextOffset = (root, node, offset) => {
    let textOffset = 0;
    let elementOffset = 0;
    
    const walker = document.createTreeWalker(
      root,
      NodeFilter.SHOW_ALL,
      null,
      false
    );

    let currentNode;
    while ((currentNode = walker.nextNode())) {
      if (currentNode === node) {
        return {
          textOffset: textOffset + offset,
          elementOffset: elementOffset,
          nodeType: node.nodeType,
          tagName: node.tagName,
          parentTag: node.parentElement?.tagName
        };
      }
      
      if (currentNode.nodeType === Node.TEXT_NODE) {
        textOffset += currentNode.textContent.length;
      } else if (currentNode.nodeType === Node.ELEMENT_NODE) {
        elementOffset++;
      }
    }
    
    return { textOffset, elementOffset, nodeType: null };
  };

  // 개선된 DOM 위치 복원
  const getNodeFromDetailedOffset = (root, targetInfo) => {
    let currentTextOffset = 0;
    let currentElementOffset = 0;
    
    const walker = document.createTreeWalker(
      root,
      NodeFilter.SHOW_ALL,
      null,
      false
    );

    let currentNode;
    let lastTextNode = null;
    
    while ((currentNode = walker.nextNode())) {
      if (currentNode.nodeType === Node.TEXT_NODE) {
        lastTextNode = currentNode;
        const nodeLength = currentNode.textContent.length;
        
        if (currentTextOffset + nodeLength >= targetInfo.textOffset) {
          return {
            node: currentNode,
            offset: targetInfo.textOffset - currentTextOffset,
          };
        }
        currentTextOffset += nodeLength;
      } else if (currentNode.nodeType === Node.ELEMENT_NODE) {
        currentElementOffset++;
      }
    }

    // fallback to last text node
    if (lastTextNode) {
      return {
        node: lastTextNode,
        offset: lastTextNode.textContent.length,
      };
    }

    return null;
  };

  // 강화된 커서 위치 저장
  const saveDetailedCursorPosition = () => {
    const selection = window.getSelection();
    if (selection.rangeCount > 0) {
      const range = selection.getRangeAt(0);
      const $editable = window.$(editorRef.current).next(".note-editor").find(".note-editable");

      if ($editable[0] && $editable[0].contains(range.startContainer)) {
        const startInfo = getDetailedTextOffset($editable[0], range.startContainer, range.startOffset);
        const endInfo = getDetailedTextOffset($editable[0], range.endContainer, range.endOffset);

        return {
          startInfo,
          endInfo,
          editable: $editable[0],
          timestamp: Date.now(),
          html: $editable[0].innerHTML
        };
      }
    }
    return null;
  };

  // 강화된 커서 위치 복원
  const restoreDetailedCursorPosition = (savedPosition) => {
    if (!savedPosition || !savedPosition.startInfo || !savedPosition.endInfo) {
      return false;
    }

    const $editable = window.$(editorRef.current).next(".note-editor").find(".note-editable");
    if (!$editable[0]) return false;

    try {
      const startPos = getNodeFromDetailedOffset($editable[0], savedPosition.startInfo);
      const endPos = getNodeFromDetailedOffset($editable[0], savedPosition.endInfo);

      if (startPos && endPos) {
        const range = document.createRange();
        const selection = window.getSelection();

        range.setStart(startPos.node, Math.min(startPos.offset, startPos.node.textContent?.length || 0));
        range.setEnd(endPos.node, Math.min(endPos.offset, endPos.node.textContent?.length || 0));

        selection.removeAllRanges();
        selection.addRange(range);
        return true;
      }
    } catch (e) {
      console.warn('커서 복원 실패:', e);
    }

    return false;
  };

  // 개선된 타이핑 상태 관리
  const startTyping = () => {
    isTyping.current = true;
    isManualChange.current = true;

    if (typingTimeout.current) {
      clearTimeout(typingTimeout.current);
    }

    // 타이핑 종료 시점을 더 짧게 조정 (50ms)
    typingTimeout.current = setTimeout(() => {
      isTyping.current = false;
      isManualChange.current = false;
      processQueuedChanges();
    }, 50);
  };

  // 개선된 큐 처리 - 더 스마트한 병합
  const processQueuedChanges = () => {
    if (changeQueue.current.length === 0 || isProcessingChange.current) return;

    const latestChange = changeQueue.current[changeQueue.current.length - 1];
    changeQueue.current = [];
    
    // 커서 위치 저장
    const currentCursor = saveDetailedCursorPosition();
    if (currentCursor) {
      lastKnownSelection.current = currentCursor;
    }

    processChange(latestChange.contents, currentCursor);
  };

  // 개선된 변경사항 처리
  const processChange = (contents, cursorInfo = null) => {
    if (isProcessingChange.current || isProgrammatic.current) {
      return;
    }

    isProcessingChange.current = true;
    pendingUpdate.current = true;

    try {
      // 글자수 제한 체크
      const textContent = extractTextContent(contents);
      if (textContent.length >= 2000) {
        const $editable = window.$(editorRef.current).next(".note-editor").find(".note-editable");
        
        if (lastValidHtml.current) {
          isProgrammatic.current = true;
          $editable.html(lastValidHtml.current);
          
          if (lastKnownSelection.current) {
            setTimeout(() => restoreDetailedCursorPosition(lastKnownSelection.current), 0);
          }
        }

        toast.error(
          <div>
            <div className="toast-error-title">글자수 초과</div>
            <div className="toast-error-body">텍스트는 최대 2000자까지 입력할 수 있습니다.</div>
          </div>
        );
        
        isProcessingChange.current = false;
        pendingUpdate.current = false;
        return;
      }

      // 유효한 내용 저장
      lastValidHtml.current = contents;
      
      // 플레이스홀더 처리
      const isEmpty = isContentEmpty(contents);
      const $editable = window.$(editorRef.current).next(".note-editor").find(".note-editable");
      const shouldShowPlaceholder = isEmpty && $editable.text().trim().length === 0;
      const currentlyHasPlaceholder = $editable.hasClass("force-placeholder");

      if (shouldShowPlaceholder !== currentlyHasPlaceholder) {
        if (shouldShowPlaceholder) {
          $editable.addClass("force-placeholder");
        } else {
          $editable.removeClass("force-placeholder");
        }
      }

      // 커서 복원을 위한 더 안전한 방법
      const finalCursor = cursorInfo || lastKnownSelection.current;
      
      if (finalCursor && isManualChange.current) {
        // requestAnimationFrame을 두 번 사용해서 더 안전하게
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            if (!isProgrammatic.current && restoreDetailedCursorPosition(finalCursor)) {
              // 커서 복원 성공
            }
            onChange(contents);
            pendingUpdate.current = false;
          });
        });
      } else {
        onChange(contents);
        pendingUpdate.current = false;
      }

    } catch (error) {
      console.error('processChange 오류:', error);
      pendingUpdate.current = false;
    } finally {
      setTimeout(() => {
        isProcessingChange.current = false;
      }, 10);
    }
  };

  // CSS 로딩 함수들 (기존과 동일)
  const loadSummernoteCSS = () => {
    return new Promise((resolve) => {
      if (document.getElementById("summernote-css-for-summernote")) {
        resolve();
        return;
      }
      const link = document.createElement("link");
      link.id = "summernote-css-for-summernote";
      link.rel = "stylesheet";
      link.href = "https://cdn.jsdelivr.net/npm/summernote@0.8.18/dist/summernote-bs4.min.css";
      link.onload = resolve;
      link.onerror = resolve;
      document.head.appendChild(link);
    });
  };

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
      link.href = "https://cdn.jsdelivr.net/npm/bootstrap@4.6.2/dist/css/bootstrap.min.css";

      link.onload = () => {
        setIsBootstrapLoaded(true);
        resolve();
      };

      link.onerror = resolve;
      document.head.appendChild(link);
    });
  };

  const removeLoadedCSS = () => {
    const bootstrap = document.getElementById("bootstrap-css-for-summernote");
    if (bootstrap) bootstrap.remove();
    const summernote = document.getElementById("summernote-css-for-summernote");
    if (summernote) summernote.remove();
  };

  // CSS 로딩
  useEffect(() => {
    Promise.all([loadBootstrapCSS(), loadSummernoteCSS()]).then(() => {
      setIsCSSReady(true);
    });
    return removeLoadedCSS;
  }, []);

  // 조합 이벤트 핸들러
  useEffect(() => {
    if (!isReady || !isInitialized.current || !window.$) return;

    const $editable = window.$(editorRef.current).next(".note-editor").find(".note-editable");

    if ($editable.length) {
      const handleCompositionStart = () => {
        isComposing.current = true;
        // 조합 시작 시 현재 커서 위치 저장
        lastKnownSelection.current = saveDetailedCursorPosition();
      };

      const handleCompositionEnd = () => {
        setTimeout(() => {
          isComposing.current = false;
          // 조합 종료 후 커서 위치 다시 저장
          const newCursor = saveDetailedCursorPosition();
          if (newCursor) {
            lastKnownSelection.current = newCursor;
          }
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
            if (isProgrammatic.current || pendingUpdate.current) {
              isProgrammatic.current = false;
              return;
            }

            // 현재 커서 위치 저장 (변경 전)
            const currentCursor = saveDetailedCursorPosition();
            lastKnownSelection.current = currentCursor;

            const currentTime = Date.now();
            const timeDiff = currentTime - lastChangeTime.current;

            // 더 짧은 간격으로 연속 입력 감지 (100ms)
            if (timeDiff < 100 && isManualChange.current) {
              startTyping();

              changeQueue.current.push({
                contents: contents,
                timestamp: currentTime,
                cursor: currentCursor
              });

              if (changeQueue.current.length > 5) {
                changeQueue.current = changeQueue.current.slice(-3);
              }

              lastChangeTime.current = currentTime;
              return;
            }

            lastChangeTime.current = currentTime;

            if (!isTyping.current && !isComposing.current) {
              processChange(contents, currentCursor);
            } else {
              changeQueue.current.push({
                contents: contents,
                timestamp: currentTime,
                cursor: currentCursor
              });
            }
          },

          onKeydown: function (e) {
          

            // 타이핑 감지
            if (e.key && e.key.length === 1 && !isComposing.current) {
              startTyping();
            }

            // 특수 키 처리 시 커서 위치 저장
            if (['Backspace', 'Delete', 'Enter'].includes(e.key)) {
              lastKnownSelection.current = saveDetailedCursorPosition();
            }
          },

          onKeyup: function (e) {
            if (e.key && e.key.length === 1 && !isComposing.current) {
              startTyping();
            }
          },

          onPaste: function (e) {
            // 붙여넣기 후 커서 위치 저장
            setTimeout(() => {
              lastKnownSelection.current = saveDetailedCursorPosition();
            }, 50);
          },

          onDrop: function (e) {
            e.preventDefault();
          },

          // 이미지 업로드 콜백들 (기존과 동일)
          onImageUpload: function (files) {
            for (let i = 0; i < files.length; i++) {
              const file = files[i];

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
                const img = new Image();
                img.onload = () => {
                  $editor.summernote("insertImage", e.target.result, function ($image) {
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
              toast.error(
                <div>
                  <div className="toast-error-title">오류 알림!</div>
                  <div className="toast-error-body">이미지를 불러올 수 없습니다.</div>
                </div>
              );
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

            // CSS 스타일 추가 (기존과 동일)
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
                }
              `;
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

            const initialIsEmpty = isContentEmpty(value);
            if (initialIsEmpty) {
              $editable.addClass("force-placeholder");
            }

            if (value) {
              isProgrammatic.current = true;
              $editor.summernote("code", value);
            }
            
            if (value && !isContentEmpty(value)) {
              $editable.removeClass("force-placeholder");
            }
            
            if (disabled) {
              $editor.summernote("disable");
            }

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
      if (typingTimeout.current) {
        clearTimeout(typingTimeout.current);
      }

      if (mutationObserver.current) {
        mutationObserver.current.disconnect();
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

  // value prop 변경 처리
  useEffect(() => {
    if (isReady && isInitialized.current && window.$) {
      const $editor = window.$(editorRef.current);
      const currentCode = $editor.summernote("code");

      if (currentCode !== value && !isProcessingChange.current && !isTyping.current && !pendingUpdate.current) {
        isProgrammatic.current = true;
        $editor.summernote("code", value || "");
        const $editable = $editor.next(".note-editor").find(".note-editable");
        if (value && !isContentEmpty(value)) {
          $editable.removeClass("force-placeholder");
        }
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