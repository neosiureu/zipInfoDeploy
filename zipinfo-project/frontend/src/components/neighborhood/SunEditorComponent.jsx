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

  // 에디터 클릭 이벤트 - 이미지 선택 방지
  const handleEditorClick = () => {
    setTimeout(() => {
      const editor = editorRef.current?.editor;
      if (editor) {
        // 현재 선택된 요소가 이미지인지 확인
        const selection = window.getSelection();
        if (selection.anchorNode && selection.anchorNode.nodeName === 'IMG') {
          // 이미지가 선택되었다면 선택 해제하고 다음 줄로 이동
          const editable = editor.getContext().element.wysiwyg;
          const range = document.createRange();
          const newSelection = window.getSelection();
          
          // 에디터의 마지막 위치로 커서 이동
          range.selectNodeContents(editable);
          range.collapse(false);
          newSelection.removeAllRanges();
          newSelection.addRange(range);
        }
      }
    }, 50);
  };

  // 이미지 업로드 완료 후 처리 - 선택 상태 해제
  const handleImageUpload = (targetImgElement, index, state, imageInfo, remainingFilesCount) => {
    if (state === 'create') {
      // 이미지 삽입 완료 후 즉시 선택 해제하고 커서 이동
      setTimeout(() => {
        const editor = editorRef.current?.editor;
        if (editor) {
          // 선택 상태 완전 해제
          const selection = window.getSelection();
          selection.removeAllRanges();
          
          // 에디터 영역으로 포커스 이동
          const editable = editor.getContext().element.wysiwyg;
          
          // 새로운 p 태그 생성하고 이미지 다음에 추가
          const newP = document.createElement('p');
          newP.innerHTML = '<br>';
          newP.style.cssText = 'margin: 10px 0; padding: 0; min-height: 20px; line-height: 1.6; clear: both;';
          
          // 이미지 요소 다음에 p 태그 삽입
          if (targetImgElement && targetImgElement.parentNode) {
            targetImgElement.parentNode.insertAdjacentElement('afterend', newP);
          } else {
            editable.appendChild(newP);
          }
          
          // 새 p 태그로 커서 이동
          const range = document.createRange();
          const newSelection = window.getSelection();
          
          range.setStart(newP, 0);
          range.setEnd(newP, 0);
          newSelection.removeAllRanges();
          newSelection.addRange(range);
          
          // 에디터에 포커스
          editable.focus();
          editor.focus();
          
          // 이미지 선택 상태 강제 해제
          if (targetImgElement) {
            targetImgElement.blur();
            targetImgElement.style.outline = 'none';
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
    // 이미지 관련 설정 - 선택/리사이징 비활성화
    imageFileInput: true,
    imageUrlInput: false, // URL 입력 탭 숨김
    imageAccept: ".jpg, .jpeg, .png, .gif, .bmp, .webp",
    imageUploadSizeLimit: 10 * 1024 * 1024, // 10MB
    imageMultipleFile: false, // 단일 파일만
    imageResizing: false, // 이미지 리사이징 완전 비활성화
    imageHeightShow: false, // 높이 조절 비활성화
    imageWidthShow: false, // 너비 조절 비활성화
    imageAlignShow: false, // 정렬 옵션 비활성화
    imageSizeOnlyPercentage: false, // 퍼센트 리사이징 비활성화
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
        onClick={handleEditorClick}
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
        
        /* 이미지 리사이징 컨트롤 완전 숨김 */
        .se-resizing-container {
          display: none !important;
          visibility: hidden !important;
        }
        
        .se-controller-image {
          display: none !important;
          visibility: hidden !important;
        }
        
        .se-resizing-bar {
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