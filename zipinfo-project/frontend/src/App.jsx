import "./App.css";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import Layout from "./components/common/Layout";
import Main from "./components/Main";
import SalePage from "./components/sale/SalePage";
import MyInfo from "./components/myPage/MyInfo";
import UpdateInfo from "./components/myPage/UpdateInfo";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 공통 레이아웃 라우트 */}
        <Route path="/" element={<Layout />}>
          <Route index element={<Main />} />
          <Route path="sale" element={<SalePage />} />
          <Route path="myPage" element={<MyInfo />} />
          <Route path="myPage/updateInfo" element={<UpdateInfo />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
