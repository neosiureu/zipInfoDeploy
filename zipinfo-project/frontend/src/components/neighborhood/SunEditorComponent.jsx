import React, { useRef, useEffect, useCallback } from "react";
import SunEditor from "suneditor-react";
import "suneditor/dist/css/suneditor.min.css";
import { toast } from "react-toastify";

export default function SunEditorComponent({ value, onChange, disabled }) {
  const editorRef = useRef(null);
  const sunEditorInstanceRef = useRef(null);
  const observerRef = useRef(null);
  const figureCursorManagerRef = useRef(null);
  const isUpdating = useRef(false);

  // 텍스트만 추출하는 함수
  const extractTextContent = (htmlContent) => {
    if (!htmlContent) return "";
    const tempDiv = document.createElement("div");
    tempDiv.innerHTML = htmlContent;
    // 이미지 태그 제거 후 텍스트만 추출
    const images = tempDiv.querySelectorAll("img");
    images.forEach((img) => img.remove());
    const textOnly = tempDiv.textContent || tempDiv.innerText || "";
    tempDiv.remove();
    return textOnly.trim();
  };

  // 내용이 비어있는지 확인
  const isContentEmpty = (htmlContent) => {
    if (!htmlContent) return true;
    const textContent = extractTextContent(htmlContent);
    const hasImage = htmlContent.includes("<img");
    const hasListContent = htmlContent.includes("<ul") || htmlContent.includes("<ol") || htmlContent.includes("<li");
    
    return textContent.length === 0 && !hasImage && !hasListContent;
  };

  // 변경 핸들러
  const handleChange = (content) => {
    if (isUpdating.current) return;

    // 텍스트 길이 체크 (2000자 제한)
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
      return;
    }

    onChange(content);
  };

  // 수정된 자동 Submit 기능 설정 (올바른 선택자 사용)
  const setupAutoSubmit = useCallback((core) => {
    console.log('개선된 자동 Submit 기능 설정 시작');
    
    const dialogObserver = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        if (mutation.type === 'childList') {
          const addedNodes = Array.from(mutation.addedNodes);
          addedNodes.forEach(node => {
            if (node.nodeType === Node.ELEMENT_NODE) {
              // 수정된 선택자: 연구 결과에 따른 올바른 구조
              const imageDialog = node.querySelector('.se-dialog form.editor_image') || 
                                (node.classList?.contains('se-dialog') && 
                                 node.querySelector('form.editor_image') ? node : null);
              
              if (imageDialog) {
                console.log('이미지 다이얼로그 감지됨 (올바른 선택자):', imageDialog);
                
                // DOM 렌더링 완료를 위한 지연
                setTimeout(() => {
                  setupDialogAutoSubmit(imageDialog);
                }, 100); // 50ms에서 100ms로 증가
              }
            }
          });
        }
      });
    });

    const setupDialogAutoSubmit = (dialog) => {
      // 기존 이벤트 리스너 중복 방지
      if (dialog._autoSubmitSetup) {
        console.log('이미 설정된 다이얼로그, 건너뛰기');
        return;
      }
      dialog._autoSubmitSetup = true;

      // 연구 결과에 따른 올바른 선택자들
      const fileInput = dialog.querySelector('input._se_image_file') || 
                        dialog.querySelector('input[type="file"][accept*="image"]') ||
                        dialog.querySelector('input[type="file"]');
      
      const submitButton = dialog.querySelector('.se-btn-primary[type="submit"]') || 
                          dialog.querySelector('button[type="submit"]') ||
                          dialog.querySelector('.se-btn-primary') ||
                          dialog.querySelector('.se-dialog-footer button:last-child');
      
      console.log('파일 입력 요소:', fileInput);
      console.log('Submit 버튼:', submitButton);
      
      if (fileInput && submitButton) {
        const changeHandler = (e) => {
          console.log('파일 선택됨:', e.target.files);
          
          if (e.target.files && e.target.files.length > 0) {
            // 파일 처리 시간을 위한 지연
            setTimeout(() => {
              console.log('Submit 버튼 클릭 실행');
              
              // 버튼이 여전히 존재하고 활성화되어 있는지 확인
              if (submitButton && !submitButton.disabled && submitButton.offsetParent !== null) {
                submitButton.click();
              } else {
                console.warn('Submit 버튼이 비활성화되어 있거나 제거됨');
                
                // 대안: 폼 제출 시도
                const form = dialog.querySelector('form.editor_image');
                if (form) {
                  console.log('폼 직접 제출 시도');
                  form.submit();
                } else {
                  // 최후 수단: Enter 키 이벤트
                  const enterEvent = new KeyboardEvent('keydown', {
                    key: 'Enter',
                    code: 'Enter',
                    keyCode: 13,
                    which: 13,
                    bubbles: true
                  });
                  dialog.dispatchEvent(enterEvent);
                }
              }
            }, 200); // 150ms에서 200ms로 증가
          }
        };

        fileInput.addEventListener('change', changeHandler);
        
        // 정리를 위해 핸들러 저장
        dialog._autoSubmitHandler = changeHandler;
        dialog._fileInput = fileInput;
        
        console.log('이벤트 리스너 설정 완료');
      } else {
        console.warn('파일 입력 또는 Submit 버튼을 찾을 수 없음');
        console.log('사용 가능한 input 요소들:', dialog.querySelectorAll('input'));
        console.log('사용 가능한 button 요소들:', dialog.querySelectorAll('button'));
      }
    };

    // 전체 document.body 감시
    dialogObserver.observe(document.body, {
      childList: true,
      subtree: true
    });

    // observer 저장
    if (!core._autoSubmitObserver) {
      core._autoSubmitObserver = dialogObserver;
    }

  }, []);

  // Figure 커서 갇힘 해결을 위한 통합 클래스 (기존 코드 유지)
  const createFigureCursorManager = useCallback((editorElement) => {
    return {
      // figure 영역에서 강제 커서 탈출
      forceCursorEscapeFromFigure() {
        const selection = window.getSelection();
        if (selection.rangeCount === 0) return false;
        
        const range = selection.getRangeAt(0);
        const container = range.commonAncestorContainer;
        
        // figure 요소 감지 (se-component 포함)
        const figureElement = container.nodeType === Node.TEXT_NODE ? 
          container.parentElement?.closest('figure') : 
          container.closest?.('figure');
          
        const seComponent = container.nodeType === Node.TEXT_NODE ?
          container.parentElement?.closest('.se-component') :
          container.closest?.('.se-component');
        
        const targetContainer = seComponent || figureElement;
        
        if (targetContainer) {
          // se-component 또는 figure 뒤에 paragraph가 없으면 생성
          let nextElement = targetContainer.nextSibling;
          if (!nextElement || nextElement.nodeType !== Node.ELEMENT_NODE) {
            const newParagraph = document.createElement('p');
            newParagraph.innerHTML = '<br>';
            newParagraph.className = 'cursor-escape-helper';
            newParagraph.style.cssText = 'margin: 10px 0; padding: 0; min-height: 20px; line-height: 1.6; cursor: text;';
            targetContainer.parentNode.insertBefore(newParagraph, nextElement);
            nextElement = newParagraph;
          }
          
          // 커서를 새 위치로 강제 이동
          const newRange = document.createRange();
          try {
            if (nextElement.firstChild && nextElement.firstChild.nodeType === Node.TEXT_NODE) {
              newRange.setStart(nextElement.firstChild, 0);
            } else {
              newRange.setStart(nextElement, 0);
            }
            newRange.collapse(true);
            selection.removeAllRanges();
            selection.addRange(newRange);
            
            // 포커스 유지
            if (editorElement) {
              editorElement.focus();
            }
            
            console.log('커서가 figure 영역에서 탈출했습니다:', nextElement);
            return true;
          } catch (error) {
            console.warn('커서 탈출 실패:', error);
            return false;
          }
        }
        return false;
      },

      // se-component 영역 강제 탈출 (핵심 해결책)
      escapeSeComponent(seComponentElement) {
        try {
          // se-component 다음에 빈 paragraph 생성
          let nextElement = seComponentElement.nextElementSibling;
          if (!nextElement || !nextElement.classList.contains('cursor-escape-helper')) {
            const escapeParagraph = document.createElement('p');
            escapeParagraph.innerHTML = '<br>';
            escapeParagraph.className = 'cursor-escape-helper';
            escapeParagraph.style.cssText = 'margin: 10px 0; padding: 0; min-height: 20px; line-height: 1.6; cursor: text;';
            seComponentElement.parentNode.insertBefore(escapeParagraph, nextElement);
            nextElement = escapeParagraph;
          }
          
          // 커서를 escape paragraph로 이동
          const range = document.createRange();
          const selection = window.getSelection();
          
          range.setStart(nextElement, 0);
          range.collapse(true);
          selection.removeAllRanges();
          selection.addRange(range);
          
          editorElement.focus();
          return true;
        } catch (error) {
          console.error('se-component 탈출 실패:', error);
          return false;
        }
      },

      // figure 처리 (연구 결과 적용)
      processFigure(figureElement) {
        // 1. figure에 처리 완료 마크 추가
        figureElement.classList.add('cursor-escape-processed');
        
        // 2. se-component 찾기
        const seComponent = figureElement.closest('.se-component');
        if (seComponent) {
          // se-component 다음에 탈출 경로 확보
          this.escapeSeComponent(seComponent);
        }
        
        // 3. figure 자체에도 탈출 경로 확보
        if (!figureElement.nextElementSibling || 
            !figureElement.nextElementSibling.classList.contains('cursor-escape-helper')) {
          
          const helper = document.createElement('p');
          helper.className = 'cursor-escape-helper';
          helper.innerHTML = '<br>';
          helper.style.cssText = 'margin: 10px 0; padding: 0; min-height: 20px; line-height: 1.6; cursor: text;';
          
          const parentContainer = figureElement.closest('.se-component') || figureElement;
          parentContainer.parentNode.insertBefore(helper, parentContainer.nextSibling);
        }
      },

      // 키보드 이벤트 처리
      handleKeyboardNavigation(e) {
        if (['ArrowRight', 'ArrowDown', 'End'].includes(e.key)) {
          setTimeout(() => {
            this.forceCursorEscapeFromFigure();
          }, 10);
        } else if (e.key === 'Enter') {
          setTimeout(() => {
            const selection = window.getSelection();
            if (selection.rangeCount > 0) {
              const range = selection.getRangeAt(0);
              const seComponent = range.commonAncestorContainer.nodeType === Node.TEXT_NODE ?
                range.commonAncestorContainer.parentElement?.closest('.se-component') :
                range.commonAncestorContainer.closest?.('.se-component');
              
              if (seComponent) {
                e.preventDefault();
                this.escapeSeComponent(seComponent);
              }
            }
          }, 10);
        }
      }
    };
  }, []);

  // 브라우저 호환성을 고려한 강화된 커서 이동 (기존 코드 유지)
  const enhancedCursorFix = useCallback((imageElement) => {
    if (!sunEditorInstanceRef.current || !figureCursorManagerRef.current) return;

    const isFirefox = navigator.userAgent.toLowerCase().includes('firefox');
    const isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);
    const delay = isFirefox ? 150 : isSafari ? 50 : 120;

    const executeCursorFix = () => {
      // 1. se-component 찾기 및 탈출 (핵심 해결책)
      const seComponent = imageElement.closest('.se-component');
      if (seComponent) {
        if (figureCursorManagerRef.current.escapeSeComponent(seComponent)) {
          console.log('se-component 탈출 성공');
          return;
        }
      }

      // 2. figure 요소 탈출
      const figureElement = imageElement.closest('figure');
      if (figureElement) {
        figureCursorManagerRef.current.processFigure(figureElement);
        if (figureCursorManagerRef.current.forceCursorEscapeFromFigure()) {
          console.log('figure 탈출 성공');
          return;
        }
      }

      // 3. 기존 fallback 전략들
      const fallbackStrategies = [
        // 전략 1: 표준 Range API + 새 단락 생성
        () => {
          const range = document.createRange();
          const selection = window.getSelection();
          
          let nextElement = imageElement.nextSibling;
          if (!nextElement || nextElement.nodeType !== Node.ELEMENT_NODE) {
            const p = document.createElement('p');
            p.innerHTML = '<br>';
            p.className = 'cursor-escape-helper';
            p.style.cssText = 'margin: 10px 0; padding: 0; min-height: 20px; line-height: 1.6; cursor: text;';
            imageElement.parentNode.insertBefore(p, imageElement.nextSibling);
            nextElement = p;
          }
          
          range.setStart(nextElement, 0);
          range.collapse(true);
          selection.removeAllRanges();
          selection.addRange(range);
          
          sunEditorInstanceRef.current.core.focus();
        },

        // 전략 2: SunEditor 내부 API 사용
        () => {
          const core = sunEditorInstanceRef.current.core;
          const nextElement = imageElement.nextSibling || 
            (() => {
              const p = core.util.createElement('p');
              p.innerHTML = '<br>';
              p.className = 'cursor-escape-helper';
              imageElement.parentNode.insertBefore(p, imageElement.nextSibling);
              return p;
            })();
          
          if (core.setRange) {
            core.setRange(nextElement, 0, nextElement, 0);
          }
          core.focus();
        }
      ];

      // 각 전략을 순차적으로 시도
      for (let i = 0; i < fallbackStrategies.length; i++) {
        try {
          fallbackStrategies[i]();
          console.log(`커서 위치 설정 성공 (전략 ${i + 1})`);
          return;
        } catch (error) {
          console.warn(`전략 ${i + 1} 실패:`, error);
        }
      }
    };

    if (isSafari) {
      requestAnimationFrame(() => {
        setTimeout(executeCursorFix, delay);
      });
    } else {
      setTimeout(executeCursorFix, delay);
    }
  }, []);

  // SunEditor 인스턴스 가져오기 + Figure 커서 매니저 초기화 + 자동 Submit 설정
  const getSunEditorInstance = useCallback((sunEditor) => {
    sunEditorInstanceRef.current = sunEditor;

    const wysiwyg = sunEditor.core.context.element.wysiwyg;
    
    // Figure 커서 매니저 초기화
    figureCursorManagerRef.current = createFigureCursorManager(wysiwyg);

    // 개선된 자동 Submit 기능 설정
    setupAutoSubmit(sunEditor.core);

    // MutationObserver 설정 (figure, se-component 모두 감지)
    if (observerRef.current) {
      observerRef.current.disconnect();
    }

    observerRef.current = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        if (mutation.type === 'childList') {
          // 이미지 요소 감지
          const addedImages = Array.from(mutation.addedNodes)
            .filter(node => node.tagName === 'IMG');
          
          addedImages.forEach(img => {
            if (img.complete) {
              enhancedCursorFix(img);
            } else {
              img.onload = () => enhancedCursorFix(img);
            }
          });

          // se-component 요소 감지
          const addedComponents = Array.from(mutation.addedNodes)
            .filter(node => node.classList && node.classList.contains('se-component'));
          
          addedComponents.forEach(component => {
            const figures = component.querySelectorAll('figure');
            figures.forEach(figure => {
              figureCursorManagerRef.current.processFigure(figure);
            });
          });

          // figure 요소 감지
          const addedFigures = Array.from(mutation.addedNodes)
            .filter(node => node.tagName === 'FIGURE');
          
          addedFigures.forEach(figure => {
            figureCursorManagerRef.current.processFigure(figure);
          });
        }
      });
    });

    observerRef.current.observe(wysiwyg, {
      childList: true,
      subtree: true
    });

    // 키보드 이벤트 핸들러 설정
    wysiwyg.addEventListener('keydown', (e) => {
      figureCursorManagerRef.current.handleKeyboardNavigation(e);
    });

  }, [createFigureCursorManager, enhancedCursorFix, setupAutoSubmit]);

  // 이미지 업로드 완료 후 처리
  const handleImageUpload = useCallback((targetImgElement, index, state, imageInfo, remainingFilesCount) => {
    if (state === 'create' && targetImgElement) {
      // 모든 이미지 컨트롤러 제거
      setTimeout(() => {
        document.querySelectorAll('.se-controller-image, .se-line-breaker, .se-resizing-container').forEach(el => {
          el.remove();
        });
      }, 50);

      // 마지막 이미지 업로드 완료 시 강화된 커서 조정
      if (remainingFilesCount === 0) {
        enhancedCursorFix(targetImgElement);
      }
    }
  }, [enhancedCursorFix]);

  // 이미지 업로드 Before 핸들러 - 서버 업로드 유지
  const handleImageUploadBefore = (files, info, uploadHandler) => {
    const file = files[0];
    
    // 서버 업로드
    const formData = new FormData();
    formData.append("image", file);

    fetch(`${import.meta.env.VITE_API_BASE_URL}/editBoard/uploadImage`, {
      method: "POST",
      body: formData,
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`서버 오류: ${response.status}`);
        }
        return response.text();
      })
      .then((serverImageUrl) => {
        console.log("서버 업로드 성공:", serverImageUrl);
        
        // SunEditor가 요구하는 응답 형식
        const response = {
          result: [{
            url: serverImageUrl.trim(),
            name: file.name,
            size: file.size
          }]
        };
        
        uploadHandler(response);
      })
      .catch((error) => {
        console.error("서버 업로드 실패:", error);
        toast.error("이미지 업로드에 실패했습니다.");
        uploadHandler({ result: [] });
      });
    
    return false; // 기본 Base64 처리 완전 차단
  };

  // 에디터 클릭 이벤트 처리
  const handleEditorClick = (event) => {
    setTimeout(() => {
      // 이미지 컨트롤러 제거
      document.querySelectorAll('.se-controller-image, .se-line-breaker, .se-resizing-container').forEach(el => {
        el.remove();
      });
      
      // 이미지 클릭 시 강화된 커서 이동
      const clickedElement = event.target;
      if (clickedElement && clickedElement.tagName === 'IMG') {
        enhancedCursorFix(clickedElement);
      }
    }, 50);
  };

  // Enter 키 처리
  const handleKeyDown = (event) => {
    if (event.key === 'Enter') {
      // 이미지 컨트롤러가 있다면 제거
      document.querySelectorAll('.se-controller-image, .se-line-breaker, .se-resizing-container').forEach(el => {
        el.remove();
      });
    }
  };

  // value prop 변경 시 에디터 업데이트
  useEffect(() => {
    if (sunEditorInstanceRef.current && value !== undefined) {
      const currentContent = sunEditorInstanceRef.current.getContents();
      
      if (currentContent !== value) {
        isUpdating.current = true;
        sunEditorInstanceRef.current.setContents(value || "");
        setTimeout(() => {
          isUpdating.current = false;
        }, 100);
      }
    }
  }, [value]);

  // 컴포넌트 정리 (개선된 정리 로직)
  useEffect(() => {
    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
      
      // 자동 Submit observer 정리
      if (sunEditorInstanceRef.current?.core?._autoSubmitObserver) {
        sunEditorInstanceRef.current.core._autoSubmitObserver.disconnect();
      }
      
      // 다이얼로그 이벤트 리스너 정리
      document.querySelectorAll('.se-dialog').forEach(dialog => {
        if (dialog._autoSubmitHandler && dialog._fileInput) {
          dialog._fileInput.removeEventListener('change', dialog._autoSubmitHandler);
          delete dialog._autoSubmitHandler;
          delete dialog._fileInput;
          delete dialog._autoSubmitSetup;
        }
      });
    };
  }, []);

  const editorOptions = {
    height: 700,
    placeholder: "내용을 입력해주세요",
    showPathLabel: false,
    resizingBar: false,
    buttonList: [
      ["bold", "underline", "italic"],
      ["fontColor", "hiliteColor"],
      ["list"],
      ["image"]
    ],
    // 이미지 관련 설정 (수정된 부분)
    imageFileInput: true,
    imageUrlInput: false, // URL 입력 비활성화로 파일 선택에 집중
    imageAccept: ".jpg, .jpeg, .png, .gif, .bmp, .webp",
    imageUploadSizeLimit: 10 * 1024 * 1024,
    imageMultipleFile: false, // 단일 파일만 허용
    imageResizing: false,
    imageHeightShow: false,
    imageWidthShow: false,
    imageAlignShow: false,
    imageSizeOnlyPercentage: false,
    imageRotation: false,
    imageGalleryUrl: false,
    lineBreaker: false,
    videoFileInput: false,
    audioFileInput: false,
    width: "100%",
    minWidth: "1000px"
  };

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
      <SunEditor
        ref={editorRef}
        getSunEditorInstance={getSunEditorInstance}
        setContents={value || ""}
        onChange={handleChange}
        onImageUploadBefore={handleImageUploadBefore}
        onImageUpload={handleImageUpload}
        onClick={handleEditorClick}
        onKeyDown={handleKeyDown}
        setOptions={editorOptions}
        disable={disabled}
        height="700px"
        setDefaultStyle="font-family: Arial, sans-serif; font-size: 16px; line-height: 1.6; padding: 10px;"
      />
      
      <style jsx global>{`
        .sun-editor {
          border: none !important;
        }
        
        .se-wrapper {
          border: none !important;
        }
        
        .se-container {
          border: none !important;
        }
        
        .se-toolbar {
          border-bottom: 1px solid #ddd !important;
          background: #f8f9fa !important;
        }
        
        .se-wrapper-inner .se-wrapper-wysiwyg {
          border: none !important;
        }
        
        .se-wrapper-inner .se-wrapper-code {
          border: none !important;
        }
        
        .sun-editor-editable {
          padding: 15px !important;
          min-height: 650px !important;
          font-size: 16px !important;
          line-height: 1.6 !important;
          word-break: break-word !important;
          overflow-wrap: break-word !important;
        }
        
        .sun-editor-editable img {
          max-width: 100% !important;
          height: auto !important;
          display: block !important;
          margin: 15px auto !important;
          clear: both !important;
          float: none !important;
          vertical-align: top !important;
          border: none !important;
          outline: none !important;
          user-select: none !important;
          pointer-events: auto !important;
          -webkit-user-select: none !important;
          -moz-user-select: none !important;
          -ms-user-select: none !important;
          cursor: default !important;
        }
        
        /* 연구 결과 적용: figure:after pseudo-element 완전 제거 */
        [contenteditable="true"] figure:after,
        [contenteditable] figure:after,
        .sun-editor-editable figure:after,
        .se-wrapper-inner figure:after,
        .se-component figure:after {
          content: none !important;
          display: none !important;
        }
        
        /* figure:before도 제거 */
        [contenteditable="true"] figure:before,
        [contenteditable] figure:before,
        .sun-editor-editable figure:before,
        .se-wrapper-inner figure:before,
        .se-component figure:before {
          content: none !important;
          display: none !important;
        }
        
        /* 처리된 figure 요소의 pseudo-element 제거 */
        .cursor-escape-processed:after,
        .cursor-escape-processed:before {
          content: none !important;
          display: none !important;
        }
        
        /* 커서 탈출 도우미 요소 스타일 */
        .cursor-escape-helper {
          min-height: 20px !important;
          line-height: 1.6 !important;
          margin: 10px 0 !important;
          padding: 0 !important;
          border: none !important;
          outline: none !important;
          cursor: text !important;
          clear: both !important;
        }
        
        /* se-component 다음의 cursor-escape-helper */
        .se-component + .cursor-escape-helper {
          cursor: text !important;
          background: transparent !important;
        }
        
        /* figure 상호작용 차단 (추가 안전 장치) */
        figure:after {
          pointer-events: none !important;
          position: absolute !important;
          z-index: -1 !important;
        }
        
        /* 이미지 리사이징 컨트롤 완전 숨김 */
        .se-resizing-container,
        .se-controller-image,
        .se-line-breaker,
        .se-line-breaker-component,
        .se-controller-image-btn,
        .se-controller-image .se-btn,
        .se-resizing-bar {
          display: none !important;
          visibility: hidden !important;
          pointer-events: none !important;
          position: absolute !important;
          left: -9999px !important;
          opacity: 0 !important;
        }
        
        /* SunEditor 내부의 모든 이미지 관련 컨트롤 숨김 */
        .sun-editor [class*="se-controller"],
        .sun-editor [class*="se-resizing"],
        .sun-editor [class*="se-line-breaker"] {
          display: none !important;
        }
        
        .sun-editor-editable p {
          margin: 10px 0 !important;
          padding: 0 !important;
          min-height: 20px !important;
          line-height: 1.6 !important;
          clear: both !important;
          display: block !important;
          width: 100% !important;
        }
        
        .sun-editor-editable p:empty {
          min-height: 20px !important;
        }
        
        .sun-editor-editable ul,
        .sun-editor-editable ol {
          margin: 10px 0 !important;
          padding-left: 30px !important;
        }
        
        .sun-editor-editable ul li,
        .sun-editor-editable ol li {
          margin: 8px 0 !important;
          line-height: 1.6 !important;
        }
        
        .se-placeholder {
          color: #999 !important;
          font-size: 16px !important;
          line-height: 1.6 !important;
          padding: 15px !important;
        }
      `}</style>
    </div>
  );
}