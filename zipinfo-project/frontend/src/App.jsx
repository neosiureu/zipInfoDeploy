import "./App.css";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import Layout from "./components/common/Layout";
import Main from "./components/Main";
import SalePage from "./components/sale/SalePage";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 공통 레이아웃 라우트 */}
        <Route path="/" element={<Layout />}>
          <Route index element={<Main />} />
          <Route path="sale" element={<SalePage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
