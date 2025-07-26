import React, { useRef, useEffect } from "react";
import SunEditor from "suneditor-react";
import "suneditor/dist/css/suneditor.min.css";
import { toast } from "react-toastify";

export default function SunEditorComponent({ value, onChange, disabled }) {
  const editorRef = useRef(null);
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

  // 이미지 업로드 완료 후 처리 - 강제 커서 이동
  const handleImageUpload = (targetImgElement, index, state, imageInfo, remainingFilesCount) => {
    if (state === 'create') {
      setTimeout(() => {
        const editor = editorRef.current?.editor;
        if (editor && targetImgElement) {
          try {
            const context = editor.getContext();
            const editable = context.element.wysiwyg;
            
            // 이미지 바로 다음에 새로운 p 태그 생성
            const newP = document.createElement('p');
            newP.innerHTML = '<br>';
            newP.style.cssText = 'margin: 10px 0; padding: 0; min-height: 20px; line-height: 1.6; display: block; clear: both;';
            
            // 이미지 요소 바로 다음에 p 태그 삽입
            if (targetImgElement.parentNode) {
              targetImgElement.parentNode.insertAdjacentElement('afterend', newP);
            } else {
              editable.appendChild(newP);
            }
            
            // 강제로 커서를 새 p 태그로 이동
            const range = document.createRange();
            const selection = window.getSelection();
            
            // 선택 영역 완전 제거
            selection.removeAllRanges();
            
            // 새 p 태그의 br 요소 앞에 커서 위치 설정
            if (newP.firstChild && newP.firstChild.nodeName === 'BR') {
              range.setStartBefore(newP.firstChild);
              range.setEndBefore(newP.firstChild);
            } else {
              range.setStart(newP, 0);
              range.setEnd(newP, 0);
            }
            
            selection.addRange(range);
            
            // 이미지 선택 상태 완전 제거
            targetImgElement.blur();
            if (targetImgElement.parentNode) {
              targetImgElement.parentNode.blur();
            }
            
            // 에디터에 포커스 및 커서 재설정
            setTimeout(() => {
              editable.focus();
              
              // 커서 위치 재확인 및 재설정
              const newSelection = window.getSelection();
              if (newSelection.rangeCount === 0 || !newSelection.getRangeAt(0).collapsed) {
                const newRange = document.createRange();
                newRange.setStart(newP, 0);
                newRange.setEnd(newP, 0);
                newSelection.removeAllRanges();
                newSelection.addRange(newRange);
              }
              
              // 이미지 컨트롤러 강제 제거
              const imageController = document.querySelector('.se-controller-image');
              if (imageController) {
                imageController.style.display = 'none';
                imageController.remove();
              }
              
              const lineBreaker = document.querySelector('.se-line-breaker');
              if (lineBreaker) {
                lineBreaker.style.display = 'none';
                lineBreaker.remove();
              }
              
            }, 50);
            
          } catch (error) {
            console.error('이미지 업로드 후 처리 중 오류:', error);
          }
        }
      }, 100);
    }
  };

  // 이미지 업로드 Before 핸들러 - 파일 선택 즉시 업로드
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
        
        // uploadHandler로 응답 전달 - 이미지가 에디터에 자동 삽입됨
        uploadHandler(response);
      })
      .catch((error) => {
        console.error("서버 업로드 실패:", error);
        toast.error("이미지 업로드에 실패했습니다.");
        uploadHandler({ result: [] });
      });
    
    // false 반환으로 기본 Base64 처리 완전 차단
    return false;
  };

  // Enter 키 처리 개선
  const handleKeyDown = (event) => {
    if (event.key === 'Enter') {
      const editor = editorRef.current?.editor;
      if (editor) {
        const selection = window.getSelection();
        const range = selection.getRangeAt(0);
        
        // 현재 커서가 이미지 근처에 있는지 확인
        const currentElement = range.commonAncestorContainer;
        const imgElement = currentElement.nodeType === Node.ELEMENT_NODE 
          ? currentElement.querySelector('img') 
          : currentElement.parentElement?.querySelector('img');
          
        if (imgElement) {
          // 이미지가 있는 경우 새 줄 추가 강제 처리
          event.preventDefault();
          
          const newP = document.createElement('p');
          newP.innerHTML = '<br>';
          newP.style.cssText = 'margin: 10px 0; padding: 0; min-height: 20px; line-height: 1.6;';
          
          const context = editor.getContext();
          const editable = context.element.wysiwyg;
          editable.appendChild(newP);
          
          const newRange = document.createRange();
          const newSelection = window.getSelection();
          newRange.setStart(newP, 0);
          newRange.setEnd(newP, 0);
          newSelection.removeAllRanges();
          newSelection.addRange(newRange);
        }
      }
    }
  };

  // value prop 변경 시 에디터 업데이트
  useEffect(() => {
    if (editorRef.current && value !== undefined) {
      const editor = editorRef.current.editor;
      const currentContent = editor.getContents();
      
      if (currentContent !== value) {
        isUpdating.current = true;
        editor.setContents(value || "");
        setTimeout(() => {
          isUpdating.current = false;
        }, 100);
      }
    }
  }, [value]);

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
    // 이미지 컨트롤러 완전 비활성화
    imageRotation: false,
    imageGalleryUrl: false,
    // 라인브레이커 비활성화
    lineBreaker: false,
    // 기타 비활성화
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
        setContents={value || ""}
        onChange={handleChange}
        onImageUploadBefore={handleImageUploadBefore}
        onImageUpload={handleImageUpload}
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
          pointer-events: none !important;
          -webkit-user-select: none !important;
          -moz-user-select: none !important;
          -ms-user-select: none !important;
        }
        
        /* 이미지를 포함한 p 태그 스타일 */
        .sun-editor-editable p:has(img) {
          margin: 15px 0 !important;
          padding: 0 !important;
          text-align: center !important;
          clear: both !important;
          display: block !important;
          width: 100% !important;
        }
        
        /* 이미지 리사이징 컨트롤 완전 숨김 */
        .se-resizing-container {
          display: none !important;
          visibility: hidden !important;
        }
        
        .se-controller-image {
          display: none !important;
          visibility: hidden !important;
          pointer-events: none !important;
        }
        
        .se-resizing-bar {
          display: none !important;
        }
        
        /* 이미지 컨트롤러 화살표 완전 제거 */
        .se-controller-image-btn {
          display: none !important;
        }
        
        .se-controller-image .se-btn {
          display: none !important;
        }
        
        /* 이미지 선택 시 나타나는 모든 컨트롤 숨김 */
        .se-line-breaker {
          display: none !important;
        }
        
        .se-line-breaker-component {
          display: none !important;
        }
        
        /* 이미지 선택 방지 */
        .sun-editor-editable img:focus {
          outline: none !important;
          border: none !important;
        }
        
        .sun-editor-editable img::selection {
          background: transparent !important;
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
        
        .sun-editor-editable p:empty:before {
          content: '';
          display: inline-block;
          height: 20px;
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