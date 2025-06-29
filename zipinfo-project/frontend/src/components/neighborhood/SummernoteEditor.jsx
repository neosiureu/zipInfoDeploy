import ReactSummernote from "react-summernote";

const SummernoteEditor = ({ value, onChange, disabled }) => (
  <ReactSummernote
    value={value}
    options={{
      lang: "ko-KR",
      height: 300,
      dialogsInBody: true,
      toolbar: [
        ["style", ["style"]],
        ["font", ["bold", "underline", "clear"]],
        ["fontname", ["fontname"]],
        ["color", ["color"]],
        ["para", ["ul", "ol", "paragraph"]],
        ["table", ["table"]],
        ["insert", ["link", "picture", "video"]],
        ["view", ["fullscreen", "codeview"]],
      ],
    }}
    onChange={onChange}
    disabled={disabled}
  />
);

export default SummernoteEditor;
