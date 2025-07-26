import React, { useRef, useEffect, useCallback } from "react";
import SunEditor from "suneditor-react";
import "suneditor/dist/css/suneditor.min.css";
import { toast } from "react-toastify";

export default function SunEditorComponent({ value, onChange, disabled }) {
  const editorRef = useRef(null);
  const sunEditorInstanceRef = useRef(null);
  const observerRef = useRef(null);
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

  // 브라우저 호환성을 고려한 커서 이동 함수 (연구 결과 적용)
  const browserCompatibleCursorFix = useCallback((imageElement) => {
    if (!sunEditorInstanceRef.current) return;

    const isFirefox = navigator.userAgent.toLowerCase().includes('firefox');
    const isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);
    const delay = isFirefox ? 150 : isSafari ? 50 : 120;

    const executeCursorFix = () => {
      const fallbackStrategies = [
        // 전략 1: 표준 Range API + 새 단락 생성 (가장 안정적)
        () => {
          const range = document.createRange();
          const selection = window.getSelection();
          
          // 이미지 다음에 빈 단락 생성
          let nextElement = imageElement.nextSibling;
          if (!nextElement || nextElement.nodeType !== Node.ELEMENT_NODE) {
            const p = document.createElement('p');
            p.innerHTML = '<br>';
            p.style.cssText = 'margin: 10px 0; padding: 0; min-height: 20px; line-height: 1.6;';
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
              imageElement.parentNode.insertBefore(p, imageElement.nextSibling);
              return p;
            })();
          
          if (core.setRange) {
            core.setRange(nextElement, 0, nextElement, 0);
          }
          core.focus();
        },

        // 전략 3: 텍스트 노드 삽입 방식
        () => {
          const range = document.createRange();
          const selection = window.getSelection();
          
          const textNode = document.createTextNode('\u00A0'); // Non-breaking space
          imageElement.parentNode.insertBefore(textNode, imageElement.nextSibling);
          
          range.setStart(textNode, 1);
          range.collapse(true);
          selection.removeAllRanges();
          selection.addRange(range);
          
          sunEditorInstanceRef.current.core.focus();
          
          // 불필요한 공백을 br로 교체
          setTimeout(() => {
            if (textNode.textContent === '\u00A0') {
              const br = document.createElement('br');
              textNode.parentNode.replaceChild(br, textNode);
            }
          }, 10);
        },

        // 전략 4: 에디터 끝으로 이동 (최후 수단)
        () => {
          const core = sunEditorInstanceRef.current.core;
          const wysiwyg = core.context.element.wysiwyg;
          const range = document.createRange();
          const selection = window.getSelection();
          
          range.selectNodeContents(wysiwyg);
          range.collapse(false);
          selection.removeAllRanges();
          selection.addRange(range);
          core.focus();
        }
      ];

      // 각 전략을 순차적으로 시도
      for (let i = 0; i < fallbackStrategies.length; i++) {
        try {
          fallbackStrategies[i]();
          console.log(`커서 위치 설정 성공 (전략 ${i + 1})`);
          return true;
        } catch (error) {
          console.warn(`전략 ${i + 1} 실패:`, error);
          if (i === fallbackStrategies.length - 1) {
            console.error('모든 커서 위치 설정 전략 실패');
            return false;
          }
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

  // SunEditor 인스턴스 가져오기 (연구 결과의 핵심 권장사항)
  const getSunEditorInstance = useCallback((sunEditor) => {
    sunEditorInstanceRef.current = sunEditor;

    // MutationObserver 설정 (연구 결과의 고급 해결책)
    if (observerRef.current) {
      observerRef.current.disconnect();
    }

    const wysiwyg = sunEditor.core.context.element.wysiwyg;
    observerRef.current = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        if (mutation.type === 'childList') {
          const addedImages = Array.from(mutation.addedNodes)
            .filter(node => node.tagName === 'IMG');
          
          addedImages.forEach(img => {
            // 이미지 로드 완료 후 커서 조정
            if (img.complete) {
              browserCompatibleCursorFix(img);
            } else {
              img.onload = () => browserCompatibleCursorFix(img);
            }
          });
        }
      });
    });

    observerRef.current.observe(wysiwyg, {
      childList: true,
      subtree: true
    });
  }, [browserCompatibleCursorFix]);

  // 이미지 업로드 완료 후 처리
  const handleImageUpload = useCallback((targetImgElement, index, state, imageInfo, remainingFilesCount) => {
    if (state === 'create' && targetImgElement) {
      // 모든 이미지 컨트롤러 제거
      setTimeout(() => {
        document.querySelectorAll('.se-controller-image, .se-line-breaker, .se-resizing-container').forEach(el => {
          el.remove();
        });
      }, 50);

      // 마지막 이미지 업로드 완료 시 커서 조정
      if (remainingFilesCount === 0) {
        browserCompatibleCursorFix(targetImgElement);
      }
    }
  }, [browserCompatibleCursorFix]);

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
      
      // 이미지 클릭 시 아래로 커서 이동
      const clickedElement = event.target;
      if (clickedElement && clickedElement.tagName === 'IMG') {
        browserCompatibleCursorFix(clickedElement);
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

  // 컴포넌트 정리
  useEffect(() => {
    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
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
    // 이미지 관련 설정
    imageFileInput: true,
    imageUrlInput: false,
    imageAccept: ".jpg, .jpeg, .png, .gif, .bmp, .webp",
    imageUploadSizeLimit: 10 * 1024 * 1024,
    imageMultipleFile: false,
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