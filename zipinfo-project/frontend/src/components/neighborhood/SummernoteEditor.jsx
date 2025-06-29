import React, { useEffect, useRef, useState } from "react";

export default function SummernoteEditor({ value, onChange, disabled }) {
  const editorRef = useRef(null);
  const [isReady, setIsReady] = useState(false);
  const isInitialized = useRef(false);

  useEffect(() => {
    if (!editorRef.current || isInitialized.current) return;

    if (typeof window.$ === "undefined" || !window.$.fn.summernote) {
      setIsReady(false);
      return;
    }

    const $editor = window.$(editorRef.current);

    try {
      $editor.summernote({
        height: 1500,
        minHeight: 1500,
        lang: "en-US",
        placeholder: "내용을 입력해주세요", // 기본 placeholder 사용
        disableResizeEditor: true,
        focus: false,
        toolbar: [
          ["style", ["style"]],
          ["font", ["bold", "italic", "underline"]],
          ["fontname", ["fontname"]],
          ["color", ["color"]],
          ["para", ["ul", "ol", "paragraph"]],
          ["insert", ["link", "picture"]],
        ],
        callbacks: {
          onChange: function (contents) {
            if (onChange) onChange(contents);
          },
          onImageUpload: function (files) {
            // 이미지 업로드 시 크기 검증 및 처리
            for (let i = 0; i < files.length; i++) {
              const file = files[i];
              const reader = new FileReader();

              reader.onload = (e) => {
                const img = new Image();
                img.onload = () => {
                  // 에디터 너비 가져오기 (패딩 제외)
                  const $editable = $editor
                    .next(".note-editor")
                    .find(".note-editable");
                  const editorWidth = $editable.width() - 50; // 패딩 여유분 고려

                  // if (img.width > editorWidth) {
                  //   alert(
                  //     `이미지 가로 크기가 너무 큽니다.\n최대 허용 크기: ${editorWidth}px\n업로드된 이미지 크기: ${img.width}px`
                  //   );
                  //   return;
                  // }

                  // 이미지 삽입
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

                      // 이미지 삽입 후 다음 줄로 이동
                      setTimeout(() => {
                        const range = document.createRange();
                        const sel = window.getSelection();

                        // 이미지 다음에 p 태그 추가
                        const $newP = window.$("<p><br></p>");
                        $image.after($newP);

                        // 커서를 새 p 태그로 이동
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
            // 외부 이미지 링크 삽입 시에도 크기 검증
            const img = new Image();
            img.onload = () => {
              const $editable = $editor
                .next(".note-editor")
                .find(".note-editable");
              // const editorWidth = $editable.width() - 50;

              // 이미지 삽입
              $editor.summernote("insertImage", url, function ($image) {
                $image.css({
                  "max-width": "100%",
                  height: "auto",
                  display: "block",
                  margin: "5px 0",
                });

                // 이미지 삽입 후 다음 줄로 이동
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

            // 간단한 에디터 스타일링
            $editable.css({
              padding: "5px 40px 40px 10px", // top right bottom left
              "line-height": "2.5",
              "font-size": "18px",
              "writing-mode": "horizontal-tb !important",
              direction: "ltr !important",
              "text-orientation": "mixed !important",
              "unicode-bidi": "normal !important",
              width: "100%",
              "min-width": "1000px",
            });

            // 간단한 CSS 추가
            if (!document.getElementById("simple-summernote-fix")) {
              const style = document.createElement("style");
              style.id = "simple-summernote-fix";
              style.textContent = `
                .note-editable {
                  writing-mode: horizontal-tb !important;
                  direction: ltr !important;
                  text-orientation: mixed !important;
                  unicode-bidi: normal !important;
                  word-wrap: break-word !important;
                  word-break: break-all !important;
                  overflow-wrap: break-word !important;
                  white-space: normal !important;
                }
                .note-editable p {
                  margin: 0 !important;
                  padding: 0 !important;
                  writing-mode: horizontal-tb !important;
                  direction: ltr !important;
                  word-wrap: break-word !important;
                  word-break: break-all !important;
                }
                .note-editable * {
                  writing-mode: horizontal-tb !important;
                  direction: ltr !important;
                }
                .note-placeholder {
                  writing-mode: horizontal-tb !important;
                  direction: ltr !important;
                }
                .note-editable:empty:before {
                  writing-mode: horizontal-tb !important;
                  direction: ltr !important;
                }
                .note-editable img {
                  max-width: 100% !important;
                  height: auto !important;
                  display: block !important;
                  margin: 5px 0 !important;
                }
              `;
              document.head.appendChild(style);
            }

            // 에디터 크기 설정
            $noteEditor.css({
              height: "1550px",
              "min-height": "1550px",
              width: "100%",
              "min-width": "1000px",
            });

            // 리사이즈바 제거
            $noteEditor.find(".note-resizebar").remove();

            // 초기 값 설정
            if (value) {
              $editor.summernote("code", value);
            }

            if (disabled) {
              $editor.summernote("disable");
            }

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
      if (isInitialized.current && window.$ && editorRef.current) {
        try {
          const styleElement = document.getElementById("simple-summernote-fix");
          if (styleElement) styleElement.remove();
          window.$(editorRef.current).summernote("destroy");
          isInitialized.current = false;
        } catch (e) {}
      }
    };
  }, []);

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
        minHeight: "1600px",
        width: "100%",
        minWidth: "1000px",
      }}
    >
      {!isReady && (
        <div style={{ padding: "20px", textAlign: "center", color: "#666" }}>
          에디터 준비 중...
        </div>
      )}
      <textarea
        ref={editorRef}
        placeholder="내용을 입력해주세요"
        style={{ display: isReady ? "none" : "block", width: "100%" }}
      />
    </div>
  );
}
