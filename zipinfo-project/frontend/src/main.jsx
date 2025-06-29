import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App.jsx";

// 1) CSS
import "bootstrap/dist/css/bootstrap.min.css";
import "react-summernote/dist/react-summernote.css";
// 2) JS
import $ from "jquery";
import "bootstrap/dist/js/bootstrap.bundle.min";
// 3) 전역 할당
window.$ = window.jQuery = $;

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <App />
  </StrictMode>
);
