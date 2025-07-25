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
          // 현재 커서 위치에 이미지 삽입
          const imageHtml = `
            <div style="width: 100%; display: block; margin: 20px 0; padding: 0; clear: both;">
              <img src="${serverImageUrl.trim()}" style="max-width: 100%; height: auto; display: block; margin: 0 auto;" alt="${file.name}" />
            </div>
            <p><br></p>
          `;
          
          // HTML 삽입
          editor.insertHTML(imageHtml);
          
          // 커서를 이미지 다음 줄로 이동
          setTimeout(() => {
            editor.focus();
            const range = editor.getRange();
            if (range) {
              // 커서를 콘텐츠 끝으로 이동
              range.selectNodeContents(editor.getContext().element.wysiwyg);
              range.collapse(false);
              editor.setRange(range);
            }
          }, 100);
          
        } else {
          // fallback: 일반적인 응답 형식
          const response = {
            result: [{
              url: serverImageUrl.trim(),
              name: file.name,
              size: file.size
            }]
          };
          uploadHandler(response);
        }
      })
      .catch((error) => {
        console.error("서버 업로드 실패:", error);
        toast.error("이미지 업로드에 실패했습니다.");
        uploadHandler("이미지 업로드에 실패했습니다.");
      });
    
    // undefined 반환으로 uploadHandler 완료까지 대기
    return undefined;
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
    imageUrlInput: false, // URL 입력 탭 숨김
    imageTabDisabled: ['url'], // URL 탭 완전 비활성화
    imageAccept: ".jpg, .jpeg, .png, .gif, .bmp, .webp",
    imageUploadSizeLimit: 10 * 1024 * 1024, // 10MB
    // 다이얼로그 없이 바로 업로드
    imageMultipleFile: false,
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
        }
        
        .sun-editor-editable div {
          width: 100% !important;
          display: block !important;
          margin: 20px 0 !important;
          padding: 0 !important;
          clear: both !important;
          float: none !important;
        }
        
        .sun-editor-editable div img {
          display: block !important;
          margin: 0 auto !important;
          width: auto !important;
          max-width: 100% !important;
        }
        
        .sun-editor-editable p img {
          display: block !important;
          margin: 15px auto !important;
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