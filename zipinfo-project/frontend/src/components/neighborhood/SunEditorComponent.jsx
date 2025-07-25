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

  // 이미지 업로드 Before 핸들러 (파일 선택 즉시 업로드)
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
        return response.text(); // 서버가 텍스트로 URL만 반환
      })
      .then((serverImageUrl) => {
        console.log("서버 업로드 성공:", serverImageUrl);
        
        // 에디터 인스턴스 가져오기
        const editor = editorRef.current?.editor;
        if (editor) {
          // 이미지 HTML 생성 - 완전히 분리된 블록으로
          const imageHtml = `
            <div class="image-container" style="width: 100%; display: block; margin: 20px 0; padding: 0; clear: both; overflow: hidden; text-align: center; background: transparent; border: none;">
              <img src="${serverImageUrl.trim()}" style="max-width: 100%; height: auto; display: block; margin: 0 auto; border: none;" alt="${file.name}" />
            </div>
          `;
          
          // 현재 커서 위치에 이미지 삽입
          editor.insertHTML(imageHtml);
          
          // 강제로 새로운 p 태그 추가하고 커서 이동
          setTimeout(() => {
            const editable = editor.getContext().element.wysiwyg;
            
            // 새로운 p 태그 생성
            const newP = document.createElement('p');
            newP.innerHTML = '<br>';
            newP.style.cssText = 'margin: 10px 0; padding: 0; min-height: 20px; line-height: 1.6; clear: both;';
            
            // 이미지 컨테이너 다음에 p 태그 추가
            const imageContainers = editable.querySelectorAll('.image-container');
            const lastImageContainer = imageContainers[imageContainers.length - 1];
            
            if (lastImageContainer) {
              lastImageContainer.parentNode.insertBefore(newP, lastImageContainer.nextSibling);
            } else {
              editable.appendChild(newP);
            }
            
            // 커서를 새 p 태그로 이동
            const range = document.createRange();
            const selection = window.getSelection();
            
            range.setStart(newP, 0);
            range.setEnd(newP, 0);
            selection.removeAllRanges();
            selection.addRange(range);
            
            // 에디터 포커스
            editor.focus();
            
            // 변경사항 반영
            onChange(editor.getContents());
            
          }, 300);
        }
      })
      .catch((error) => {
        console.error("서버 업로드 실패:", error);
        toast.error("이미지 업로드에 실패했습니다.");
      });
    
    // false 반환으로 기본 동작 차단 (다이얼로그 안나옴)
    return false;
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
    // 이미지 업로드 설정 - 파일 선택 즉시 업로드
    imageFileInput: true,
    imageUrlInput: false, // URL 입력 완전 차단
    imageUploadHeader: null,
    imageUploadUrl: null, // 서버 URL 차단 (우리가 직접 처리)
    imageAccept: ".jpg, .jpeg, .png, .gif, .bmp, .webp",
    imageUploadSizeLimit: 10 * 1024 * 1024, // 10MB
    imageMultipleFile: false, // 단일 파일만
    imageRotation: false, // 회전 비활성화
    imageHeightShow: false, // 높이 설정 숨김
    imageWidthShow: false, // 너비 설정 숨김
    imageSizeOnlyPercentage: false,
    imageAlignShow: false, // 정렬 옵션 숨김
    // 기타 모든 미디어 비활성화
    videoFileInput: false,
    audioFileInput: false,
    katex: false,
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
          margin: 0 auto !important;
          clear: both !important;
          float: none !important;
          vertical-align: top !important;
          pointer-events: none !important;
          border: none !important;
          outline: none !important;
        }
        
        .sun-editor-editable .image-container {
          width: 100% !important;
          display: block !important;
          margin: 20px 0 !important;
          padding: 0 !important;
          clear: both !important;
          float: none !important;
          overflow: hidden !important;
          position: relative !important;
          text-align: center !important;
          background: transparent !important;
          border: none !important;
          min-height: auto !important;
          max-height: none !important;
          line-height: 0 !important;
          font-size: 0 !important;
        }
        
        .sun-editor-editable .image-container * {
          pointer-events: none !important;
        }
        
        .sun-editor-editable .image-container img {
          display: block !important;
          margin: 0 auto !important;
          width: auto !important;
          max-width: 100% !important;
          pointer-events: none !important;
          border: none !important;
        }
        
        .sun-editor-editable div {
          word-break: normal !important;
          overflow-wrap: normal !important;
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
        
        .sun-editor-editable p img {
          display: block !important;
          margin: 15px auto !important;
        }
        
        /* 이미지 컨테이너는 편집 불가능하게 */
        .sun-editor-editable .image-container {
          -webkit-user-select: none !important;
          -moz-user-select: none !important;
          -ms-user-select: none !important;
          user-select: none !important;
          cursor: default !important;
        }
        
        /* 이미지 컨테이너에는 텍스트 입력 불가 */
        .sun-editor-editable .image-container:focus {
          outline: none !important;
        }
        
        .sun-editor-editable .image-container::before,
        .sun-editor-editable .image-container::after {
          content: "" !important;
          display: block !important;
          clear: both !important;
          height: 0 !important;
          visibility: hidden !important;
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